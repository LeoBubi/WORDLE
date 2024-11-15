import java.io.*;
import java.net.*;
import java.util.*;
import java.nio.channels.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.lang.Thread;
import java.lang.StringBuilder;
import java.lang.reflect.Type;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;


public class ServerMain
{
	/* configuration file name */
	private static final String configFile = "configuration/serverProperties.properties";
	/* admin (server user) input */
	private static final Scanner adminInput = new Scanner(System.in);
	/* thread pool for executing client requests */
	private static final ExecutorService clientPool = Executors.newCachedThreadPool();
	/* current secret word - see SWHandler */
	private static final StringBuilder secretWord = new StringBuilder();
	/* content of current secret word (for later comparison with guessed word) - see SWHandler */
	private static final HashMap<Character,ArrayList<Integer>> swContent = new HashMap<Character,ArrayList<Integer>>();
	/* local users database */
	private static final HashMap<String,User> usersDB = new HashMap<String,User>();
	/* game number, incremented with each new secret word - see SWHandler */
	private static final AtomicInteger gameNo = new AtomicInteger(0);
	
	/* listen for new client connection requests */
	private static final AtomicBoolean listen = new AtomicBoolean(true);
	/* close all client connections */
	private static final AtomicBoolean stop = new AtomicBoolean(false);

	/* seconds to wait before disconnecting from all clients during shutdown - see Shutdown */
	private static int shutTimeout;

	/* words file name */
	private static String wordsFilename;
	/* opened words file */
	private static RandomAccessFile wordsFile;
	/* users backup file */
	private static String usersFile;
	/* maximum attempts for a user to guess login password */
	private static int maxLogPassAttempts;
	/* minimum length of a password (for registration) */
	private static int minPassLength;

	/* multicast socket for sending clients sharings */
	private static MulticastSocket mcSocket;
	/* multicast group address */
	private static InetAddress groupAddr;
	/* multicast group port */
	private static int groupPort;

	/* selector to handle client operations */
	private static Selector selector;
	/* server socket channel to listen for new client connections */
	private static ServerSocketChannel listener;
	/* port on which server listen for new client connections */
	private static int serverPort;

	/* WORDLE - length of a word (in bytes) */
	private static int wordLength;
	/* WORDLE - maximum attempts for a user to guess secret word */
	private static int wordleMaxAttempts;
	/* WORDLE - time for a new secret word to be extracted */
	private static int nextSWTime;

	/* for setting clients id */
	private static int clientID = 0;


	public static void main(String[] args)
	{
		/* read configuration file data */
		try {readConfigfFile(configFile);}
		catch (FileNotFoundException e)
		{failServer("configuration file inexistent or not accessible");}
		catch (IllegalArgumentException | UnknownHostException e)
		{failServer("configuration file contains invalid data");}
		catch (IOException e)
		{failServer("error while reading configuration file");}

		/* open words file */
		try {wordsFile = new RandomAccessFile(wordsFilename, "r");}
		catch (FileNotFoundException e)
		{failServer("words file inexistent or not accessible");}
		
		/* initialize words file handler (class) */
		try {new WordsFileHandler(wordsFile, wordLength);}
		catch (IOException e)
		{failServer("words file handler fatal error");}

		/* restore users databse if previously backed up */
		try {setUsersDB();}
		catch (FileNotFoundException e)
		{failServer("users json file inexistent or not accessible");}
		catch (IOException | JsonParseException e)
		{failServer("error while importing users data from json file");}

		/* open multicast connection */
		try
		{
			mcSocket = new MulticastSocket(groupPort);
			mcSocket.joinGroup(groupAddr);
		}
		catch (IOException e)
		{failServer("cannot open multicast connection");}

		/* start running secret word handler */
		Thread swHandler = new SWHandler(secretWord, swContent, wordsFile, wordLength, nextSWTime, gameNo);
		swHandler.start();

		/* initiliaze server listener for accepting client connections */
		try {initListener();}
		catch (IOException e)
		{failServer("cannot open or register server listener", mcSocket);}

		/* start running shutdown controller */
		Thread shutdown = new Shutdown(listen, stop, usersDB, usersFile, shutTimeout);
		shutdown.start();

		/* Handle new client connections or client requests */
		Set<SelectionKey> selectedKeys;
		Iterator<SelectionKey> keyIterator;
		SelectionKey key;
		while (true)
		{
			/* wait for operation requests */
			try {selector.select();}
			catch (IOException e) {continue;}

			selectedKeys = selector.selectedKeys();
			keyIterator = selectedKeys.iterator();
			while (keyIterator.hasNext())
			{
				key = keyIterator.next();
				keyIterator.remove();

				/* if key is NOT valid */
				if (!key.isValid()) {continue;}

				/* if a new client wants to connect */
				if (listen.get() && key.isAcceptable())
				{
					try
					{
						SocketChannel client = listener.accept();
						client.configureBlocking(false);
						client.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE, new ClientStatus(clientID++));
					}
					catch (IOException e) {/* if client cannot be accepted or registered, just ignore it */}
				}

				/* if a client wants to perform an operation */
				else if (key.isReadable())
				{
					try
					{
						/* retrieve date sent from client */
						byte[] clientData = new ChannelReader((SocketChannel)key.channel()).read();
						/* if client disconnected */
						if (clientData == null)
						{
							/* close client communication */
							try {((SocketChannel)key.channel()).close();}
							catch (IOException e) {/* do nothing */}
							key.cancel();

							continue;
						}

						clientPool.execute(new ClientHandler(
							clientData,
							key, 
							usersDB, 
							maxLogPassAttempts, 
							minPassLength, 
							wordleMaxAttempts, 
							secretWord.toString(), 
							new HashMap<Character,ArrayList<Integer>>(swContent), 
							wordLength, 
							gameNo.get(), 
							mcSocket,
							groupAddr,
							groupPort,
							stop
						));
					}
					catch (IOException e) {/* if cannot communicate with client, just ignore it */}
				}

				/* if a client is ready to receive something from server */
				else if (key.isWritable())
				{
					try
					{
						ChannelWriter toClient = new ChannelWriter((SocketChannel)key.channel());
						ClientStatus clientStatus = (ClientStatus) key.attachment();
						switch (clientStatus.canGetData())
						{
							case 1:
								toClient.write(clientStatus.getAck());
								break;
							case 2:
								toClient.write(clientStatus.getMessage());
								break;
							default:
								break;
						}
					}
					catch (IOException e) {/* if cannot communicate with client, just ignore it */}
				}
			}
		}
	}


	/**
	 * Initiliaze selector and register server socket.
	 * @throws IOException if an I/O error occurs.
	 */
	private static void initListener() throws IOException
	{
		selector = selector.open();
		listener = ServerSocketChannel.open();
		listener.bind(new InetSocketAddress("localhost", serverPort));
		listener.configureBlocking(false);
		listener.register(selector, SelectionKey.OP_ACCEPT, null);
	}


	/**
	 * Restore backed up users database, if existent.
	 * @throws FileNotFoundException if users backup file is inexistent or not accessible.
	 * @throws IOException if an I/O error occurs.
	 * @throws JsonParseException if users backup file is corrupted.
	 */
	private static void setUsersDB() throws FileNotFoundException, IOException, JsonParseException
	{
		FileReader jsonFile;
		String adminAnswer;

		/* try to open users json file */
		try {jsonFile = new FileReader(usersFile);}
		catch (FileNotFoundException e)
		{
			do
			{
				/* ask what to do */
				System.out.println("Users json file inexistent or not accessible, is this normal? (yes/no)");
				adminAnswer = adminInput.nextLine();

				if (adminAnswer.equals("no"))
					throw new FileNotFoundException();
				if (adminAnswer.equals("yes"))
				{
					/* nothing to restore */
					return;
				}
			} while (true);
		}

		/* restore users database from json file */
		Type userType = new TypeToken<User>(){}.getType();
		Gson gson = new Gson();
		JsonReader reader = new JsonReader(jsonFile);
		User user;

		reader.beginArray();
		while (reader.hasNext())
		{
			user = gson.fromJson(reader, userType);
			usersDB.put(user.getName(), user);
		}
		reader.endArray();
		reader.close();
	}


	/**
	 * Read configuration file data.
	 * @param configFile configuration file.
	 * @throws FileNotFoundException if configFile does not exist, is a directory rather than a regular file, or for some other reason cannot be opened for reading.
	 * @throws IOException if an error occurred when reading data from configFile.
	 * @throws IllegalArgumentException if configFile contains invalid data.
	 * @throws UnknownHostException if configFile contains invalid data.
	 */
	private static void readConfigfFile(String configFile) throws FileNotFoundException, IOException, IllegalArgumentException, UnknownHostException
	{
		/* open and load configuration file */
		FileInputStream inFile = new FileInputStream(configFile);
		Properties configData = new Properties();
		configData.load(inFile);

		/* read configuration file */
		shutTimeout = Integer.parseInt(configData.getProperty("shutTimeout"));
		wordsFilename = configData.getProperty("wordsFilename");
		usersFile = configData.getProperty("usersFile");
		maxLogPassAttempts = Integer.parseInt(configData.getProperty("maxLogPassAttempts"));
		minPassLength = Integer.parseInt(configData.getProperty("minPassLength"));
		groupAddr = InetAddress.getByName(configData.getProperty("groupAddr"));
		groupPort = Integer.parseInt(configData.getProperty("groupPort"));
		serverPort = Integer.parseInt(configData.getProperty("serverPort"));
		wordLength = Integer.parseInt(configData.getProperty("WORDLE_WordLength"));
		wordleMaxAttempts = Integer.parseInt(configData.getProperty("WORDLE_MaxAttempts"));
		nextSWTime = Integer.parseInt(configData.getProperty("WORDLE_nextWordTime"));

		/* check read data */
		if (shutTimeout < 0) throw new IllegalArgumentException();
		if (wordsFilename == null) throw new IllegalArgumentException();
		if (usersFile == null) throw new IllegalArgumentException();
		if (maxLogPassAttempts < 1) throw new IllegalArgumentException();
		if (minPassLength < 1) throw new IllegalArgumentException();
		if (!groupAddr.isMulticastAddress()) throw new IllegalArgumentException();
		if (groupPort < 1024 || groupPort > 65535) throw new IllegalArgumentException();
		if (serverPort < 1024 || serverPort > 65535) throw new IllegalArgumentException();
		if (wordLength < 1) throw new IllegalArgumentException();
		if (nextSWTime < 1) throw new IllegalArgumentException();
	}


	/**
	 * Abnormal exit procedure.
	 * @param message to be printed on stderr
	 */
	private static void failServer(String message)
	{
		System.err.println("ERROR: " + message);
		System.exit(1);
	}

	/**
	 * Abnormal exit procedure.
	 * @param message to be printed on stderr
	 * @param mcSocket multicast connection to close before terminating.
	 */
	private static void failServer(String message, MulticastSocket mcSocket)
	{
		System.err.println("ERROR: " + message);
		mcSocket.close();
		System.exit(1);
	}
}