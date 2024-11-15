import java.io.*;
import java.net.*;
import com.google.gson.reflect.*;
import java.nio.channels.SocketChannel;
import java.util.Properties;
import java.util.Scanner;
import java.util.ArrayList;
import java.lang.Integer;
import java.lang.NumberFormatException;
import java.lang.Thread;
import java.lang.reflect.Type;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

public class ClientMain
{
	/* configuration file name */
	private static final String configFile = "configuration/clientProperties.properties";
	/* user input */
	private static final Scanner userInput = new Scanner(System.in);

	/* server address */
	private static InetAddress serverAddr;
	/* server port */
	private static int serverPort;
	/* socket channel for connecting to server */
	private static SocketChannel serverChannel;
	/* communication FROM server */
	private static ChannelReader fromServer;
	/* communication TO server */
	private static ChannelWriter toServer;

	/* multicast group address */
	private static InetAddress groupAddr;
	/* multicast group port */
	private static int groupPort;
	/* multicast socket for receiving packets */
	private static MulticastSocket mcSocket;
	/* users sharing messages databse */
	private static ArrayList<String> sharesDB;

	/* to store user input (text) */
	private static String userAnswer;
	/* to store user input (number) */
	private static int userAction;
	/* to store server input (number) */
	private static int serverAck;
	/* to store server input (text) */
	private static String serverAnswer;


	public static void main(String[] args)
	{
		/* read configuration file data */
		try {readConfigfFile(configFile);}
		catch (FileNotFoundException e)
		{failClient ("configuration file inexistent or not accessible");}
		catch (IllegalArgumentException | UnknownHostException e)
		{failClient("configuration file contains invalid data");}
		catch (IOException e)
		{failClient("error while reading configuration file");}

		/* open server connection */
		try 
		{
			serverChannel = SocketChannel.open(new InetSocketAddress(serverAddr, serverPort));
			fromServer = new ChannelReader(serverChannel);
			toServer = new ChannelWriter(serverChannel);
		}
		catch (IOException e)
		{failClient("cannot connect to server");}

		/* go to actions menu for logged out users */
		try {outMenu();}
		catch (IOException e)
		{failClient("cannot communicate with server", serverChannel, mcSocket);}
	}


	/**
	 * Actions menu for logged out users
	 * - Register
	 * - Log in
	 * - Quit
	 */
	private static void outMenu() throws IOException
	{
		do
		{
			/* display menu */
			System.out.println(
				"\n\n1: Register\n" +
				"2: Log in\n" +
				"3: Quit"
			);

			/* read user input and check its validity */
			try {userAction = Integer.parseInt(userInput.nextLine());}
			catch (NumberFormatException e) {continue;}

			if (userAction > 0 && userAction < 4)
				break;
		} while (true);

		/* send action to server */
		toServer.write(userAction);

		/* receive ACK from server */
		serverAck = fromServer.readInt();
		if (serverAck == -1) failClient("server error", serverChannel);

		/* (ACK == 0) which action? */
		if (userAction == 2) logIn();
		if (userAction == 1) register();
		closeClient(serverChannel); // userAction == 3
	}


	/**
	 * Log user in.
	 */
	private static void logIn() throws IOException
	{
		do
		{
			/* get username */
			System.out.print("\n\nUsername: ");
			userAnswer = userInput.nextLine();
			
			/* send username to server */
			toServer.write(userAnswer);

			/* receive ACK from server */
			serverAck = fromServer.readInt();
			if (serverAck == 0) break;
			if (serverAck == 1) continue;
			if (serverAck == 2)
			{
				System.out.println("\nAlready logged in.");
				continue;
			}
			failClient("server error", serverChannel); // serverAck == -1
		} while (true);

		do
		{
			/* get password */
			System.out.print("\nPassword: ");
			userAnswer = userInput.nextLine();
			
			/* send username to server */
			toServer.write(userAnswer);

			/* receive ACK from server */
			serverAck = fromServer.readInt();
			if (serverAck == 0) break;
			if (serverAck == 1) continue;
			if (serverAck == 2)
			{
				System.out.println("\nAttempted too many times, try again later.");
				closeClient(serverChannel);
			}
			failClient("server error", serverChannel); // serverAck == -1
		} while (true);

		System.out.println("\nSuccessfully logged in.");

		/* start multicast session */
		mcStart();

		/* go to actions menu for logged in users */
		inMenu();
	}


	/**
	 * Register user.
	 */
	private static void register() throws IOException
	{
		do
		{
			/* get username */
			System.out.print("\n\nUsername: ");
			userAnswer = userInput.nextLine();
			
			/* send username to server */
			toServer.write(userAnswer);

			/* receive ACK from server */
			serverAck = fromServer.readInt();
			if (serverAck == 0) break;
			if (serverAck == 1)
			{
				System.out.println("\nUsername already existent.");
				continue;
			}
			failClient("server error", serverChannel); // serverAck == -1
		} while (true);

		do
		{
			/* get password */
			System.out.print("\nPassword: ");
			userAnswer = userInput.nextLine();
			
			/* send username to server */
			toServer.write(userAnswer);

			/* receive ACK from server */
			serverAck = fromServer.readInt();
			if (serverAck > 0)
			{
				System.out.println("\nPassword must contain at least " + serverAck + " characters.");
				continue;
			}
			if (serverAck == -1) failClient("server error", serverChannel);

			/* (serverAck == 0) get password again */
			System.out.print("\nConfirm password: ");
			userAnswer = userInput.nextLine();
			
			/* send username to server */
			toServer.write(userAnswer);

			/* receive ACK from server */
			serverAck = fromServer.readInt();
			if (serverAck == 0) break;
			if (serverAck == 1)
			{
				System.out.println("\nPasswords don't match");
				continue;
			}
			failClient("server error", serverChannel); // serverAck == -1
		} while (true);

		System.out.println("\nSuccessfully registered.");

		/* start multicast session */
		mcStart();

		/* go to actions menu for logged in users */
		inMenu();
	}


	/**
	 * Handle multicast connection in another thread to receive sharing messages
	 */
	private static void mcStart()
	{
		/* open multicast serverChannel */
		try
		{
			mcSocket = new MulticastSocket(groupPort);
			mcSocket.joinGroup(groupAddr);
		}
		catch (IOException e)
		{failClient("cannot start multicast session", serverChannel);}

		/* initialize shares database */
		sharesDB = new ArrayList<String>();

		/* run multicast handler (listen and store) */
		Thread mcHandler = new MCHandler(mcSocket, sharesDB);
		mcHandler.start();
	}


	/**
	 * Actions menu for logged in users
	 * - PlayWORDLE
	 * - SendMeStatistics
	 * - ShowMeSharing
	 * - Log out
	 * - Quit
	 */
	private static void inMenu() throws IOException
	{
		do
		{
			/* display menu */
			System.out.println(
				"\n\n1: Play WORDLE\n" +
				"2: My statistics\n" +
				"3: Show sharing\n" +
				"4: Log out\n" +
				"5: Quit"
			);

			/* read user input and check its validity */
			try {userAction = Integer.parseInt(userInput.nextLine());}
			catch (NumberFormatException e) {continue;}

			if (userAction < 1 || userAction > 5)
				continue;

			/* if show sharing */
			if (userAction == 3) showMeSharing();

			/* else send action to server */
			toServer.write(userAction);

			/* receive ACK from server */
			serverAck = fromServer.readInt();
			if (serverAck == 1)
			{
				System.out.println("\nSecret word already played. Wait for a new one.");
				continue;
			}
			if (serverAck == -1) failClient("server error", serverChannel, mcSocket);
			break; // serverAck == 0
		} while (true);

		/* which action? */
		if (userAction == 1) playWORDLE();
		if (userAction == 2) sendMeStatistics();
		if (userAction == 4)
		{
			/* end multicast session */
			mcSocket.close();
			sharesDB = null;

			/* go to actions menu for logged out users */
			outMenu();
		}
		closeClient(serverChannel, mcSocket); // userAction == 5
	}


	/**
	 * Play WORDLE
	 */
	private static void playWORDLE() throws IOException
	{
		do
		{
			/* ask guessed word */
			System.out.print("\nWord: ");
			userAnswer = userInput.nextLine();

			/* send guessed word to server */
			toServer.write(userAnswer);

			/* receive ACK from server */
			serverAck = fromServer.readInt();
			if (serverAck == 1)
			{
				System.out.println("\n" + fromServer.readString());
				continue;
			}
			else if (serverAck == 3)
			{
				System.out.println("\nIllegal world: wrong length or inexistent.");
				continue;
			}
			break;
		} while (true);

		if (serverAck == 0)
			System.out.println("\nVICOTORY!");
		else if (serverAck == 2)
			System.out.println("\nDEFEAT");
		else // serverAck == -1
			failClient("server error", serverChannel, mcSocket);

		/* request sharing */
		do
		{
			System.out.println("\n\nShare results? (1: yes - 2: no)");
			try {userAction = Integer.parseInt(userInput.nextLine());}
			catch (NumberFormatException e) {continue;}

			if (userAction > 0 && userAction < 3)
				break;
		} while (true);

		/* send action to server */
		toServer.write(userAction);

		/* receive ACK from server */
		serverAck = fromServer.readInt();
		if (serverAck == 0 && userAction == 1)
			System.out.println("\nResults shared!");
		if (serverAck == -1)
			failClient("server error", serverChannel, mcSocket);

		/* go to actions menu for logged in users */
		inMenu();
	}


	/**
	 * Show other users shares
	 * NOTE: ClientMCHandler is still running in BG, so size can (only) increase
	 */
	private static void showMeSharing() throws IOException
	{
		/* if no one shared yet */
		if (sharesDB.size() == 0)
		{
			System.out.println("No one shared their results yet.");

			/* go to actions menu for logged in users */
			inMenu();
		}

		String jsonShare;
		Share nextShare;
		String[] gameScheme;
		Type shareType = new TypeToken<Share>(){}.getType();
		Gson gson = new Gson();

		for (int i = 0; i < sharesDB.size(); i++)
		{
			/* retrieve next share */
			jsonShare = sharesDB.get(i);
			try {nextShare = gson.fromJson(jsonShare.trim(), shareType);}
			catch (JsonParseException e)
			{
				System.err.println("\nbad share format");
				continue;
			}

			/* print sharing info */
			System.out.println(
				"\nUser: " + nextShare.getUser() +
				"\nGame: " + nextShare.getGame() +
				"\nAttempts: " + nextShare.getAttemptsString() +
				"\nGame scheme:"
			);
			gameScheme = nextShare.getGameScheme();
			for (int j = 0; j < nextShare.getAttempts(); j++)
				System.out.println("\t" + gameScheme[j]);
		}

		/* go to actions menu for logged in users */
		inMenu();
	}


	/**
	 * Show user statistics
	 */
	private static void sendMeStatistics() throws IOException
	{
		UserStats stats = null;
		float[] guessDistr;
		Type statsType = new TypeToken<UserStats>(){}.getType();
		Gson gson = new Gson();

		/* receive json stats from server */
		serverAnswer = fromServer.readString();
		try {stats = gson.fromJson(serverAnswer.trim(), statsType);}
		catch (JsonParseException e)
		{
			System.err.println("\nbad statistics format");
			
			/* go to actions menu for logged in users */
			inMenu();
		}

		/* print statistics */
		System.out.println(
			"\nGames played: " + stats.getGames() +
			"\nVictory rate: " + stats.getVictoriesPerc() +
			"\nLast streak: " + stats.getLastStreak() +
			"\nLongest streak: " + stats.getMaxStreak() +
			"\nGuess distribution:"
		);
		guessDistr = stats.getGuessDistribution();
		for (int i = 0; i < guessDistr.length; i++)
			System.out.println("\t" + (i+1) + " : " + guessDistr[i] + "%");
		
		/* go to actions menu for logged in users */
		inMenu();
	}


	/**
	 * Read configuration file data.
	 * @param configFile configuration file
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
		serverAddr = InetAddress.getByName(configData.getProperty("serverAddr"));
		serverPort = Integer.parseInt(configData.getProperty("serverPort"));
		groupAddr = InetAddress.getByName(configData.getProperty("groupAddr"));
		groupPort = Integer.parseInt(configData.getProperty("groupPort"));

		/* check read data */
		if (serverPort < 1024 || serverPort > 65535) throw new IllegalArgumentException();
		if (!groupAddr.isMulticastAddress()) throw new IllegalArgumentException();
		if (groupPort < 1024 || groupPort > 65535) throw new IllegalArgumentException();
	}



	/**
	 * Abnormal exit procedure.
	 * @param message to be printed on stderr
	 */
	private static void failClient(String message)
	{
		System.err.println("ERROR: " + message);
		System.exit(1);
	}

	/**
	 * Abnormal exit procedure.
	 * @param message message to be printed on stderr
	 * @param serverChannel serverChannel to close before terminating
	 */
	private static void failClient(String message, SocketChannel serverChannel)
	{
		System.err.println("ERROR: " + message);
		try {serverChannel.close();}
		catch (IOException e)
		{System.err.println("ERROR: failed serverChannel closure");}
		finally
		{System.exit(1);}
	}

	/**
	 * Abnormal exit procedure.
	 * @param message message to be printed on stderr
	 * @param serverChannel serverChannel to close before terminating
	 * @param mcSocket multicast serverChannel to close before terminating
	 */
	private static void failClient(String message, SocketChannel serverChannel, MulticastSocket mcSocket)
	{
		System.err.println("ERROR: " + message);
		try {
			serverChannel.close();
			mcSocket.close();
		}
		catch (IOException e)
		{System.err.println("ERROR: failed serverChannels closure");}
		finally
		{System.exit(1);}
	}

	/**
	 * Normal exit proedure.
	 * @param serverChannel serverChannel to close before terminating
	 */
	private static void closeClient(SocketChannel serverChannel)
	{
		try {serverChannel.close();}
		catch (IOException e)
		{failClient("ERROR: failed serverChannel closure");}
		System.exit(0);
	}

	/**
	 * Normal exit proedure.
	 * @param serverChannel serverChannel to close before terminating
	 * @param mcSocket multicast serverChannel to close before terminating
	 */
	private static void closeClient(SocketChannel serverChannel, MulticastSocket mcSocket)
	{
		try
		{
			serverChannel.close();
			mcSocket.close();
		}
		catch (IOException e)
		{failClient("ERROR: failed serverChannel closure");}
		System.exit(0);
	}
}









































