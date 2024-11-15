import java.nio.channels.SocketChannel;
import java.nio.channels.SelectionKey;
import java.io.IOException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import com.google.gson.Gson;
import java.lang.StringBuilder;
import java.lang.reflect.Type;
import com.google.gson.reflect.TypeToken;
import java.net.MulticastSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;


public class ClientHandler implements Runnable
{
	/* it makes shure that two users can't register with the same name */
	private static final HashSet<String> tmpNames = new HashSet<String>();

	/* close client connection - see Shutdown */
	private AtomicBoolean stop;

	/* client socket channel */
	private SocketChannel client;
	/* communication TO client (just for closing connection) */
	private ChannelWriter toClient;

	/* data read from client */
	private byte[] data;
	/* client's associated selection key */
	private SelectionKey key;
	/* reference - client status - see ClientStatus */
	private ClientStatus clientStatus;
	/* reference - local users databse */
	private HashMap<String,User> usersDB;
	/* current secret word's copy */
	private String secretWord;
	/* content of current secret word's copy */
	private HashMap<Character,ArrayList<Integer>> swContent;
	
	/* maximum attempts for a user to guess login password */
	private int maxLogPassAttempts;
	/* minimum length of a password (for registration) */
	private int minPassLength;
	/* WORDLE - maximum attempts for a user to guess secret word */
	private int wordleMaxAttempts;
	/* WORDLE - length of a word (in bytes) */
	private int wordLength;
	/* game number */
	private int gameNo;

	/* multicast socket for sending clients sharings */
	private MulticastSocket mcSocket;
	/* multicast group address */
	private InetAddress groupAddr;
	/* multicast group port */
	private int groupPort;
	/* to store client input (text) */
	private String clientAnswer;
	/* to store client input (number) */
	private int clientAction;

	/**
	 * Instantiate a new client handler.
	 * @param key client's associated selection key.
	 * @param usersDB reference to local users databse.
	 * @param maxLogPassAttempts maximum attempts for a user to guess login password.
	 * @param minPassLength minimum length of a password (for registration).
	 * @param wordleMaxAttempts maximum attempts for a user to guess secret word.
	 * @param swContent content of current secret word's copy.
	 * @param wordLength length of a word (in bytes).
	 * @param gameNo game number.
	 * @param mcSocket multicast socket for sending clients sharings.
	 * @param groupAddr multicast group address
	 * @param groupPort multicast group port
	 * @param stop if true, tells to close client connection
	 */
	public ClientHandler(
		byte[] data,
		SelectionKey key, 
		HashMap<String,User> usersDB, 
		int maxLogPassAttempts, 
		int minPassLength, 
		int wordleMaxAttempts, 
		String secretWord, 
		HashMap<Character,ArrayList<Integer>> swContent, 
		int wordLength, 
		int gameNo, 
		MulticastSocket mcSocket,
		InetAddress groupAddr,
		int groupPort,
		AtomicBoolean stop
	) throws IOException
	{
		client = (SocketChannel) key.channel();
		toClient = new ChannelWriter(client);

		this.data = data;
		this.key = key;
		clientStatus = (ClientStatus) key.attachment();
		this.usersDB = usersDB;
		this.maxLogPassAttempts = maxLogPassAttempts;
		this.minPassLength = minPassLength;
		this.wordleMaxAttempts = wordleMaxAttempts;
		this.secretWord = secretWord;
		this.swContent = swContent;
		this.wordLength = wordLength;
		this.gameNo = gameNo;
		this.mcSocket = mcSocket;
		this.groupAddr = groupAddr;
		this.groupPort = groupPort;
		this.stop = stop;
	}


	/**
	 * Read an integer from the given byte array.
	 * @param data the byte array to be read.
	 * @return the read integer.
	 * @throws IOException if an I/O error occurs.
	 */
	public int readInt(byte[] data) throws IOException
	{
		return new DataInputStream(new ByteArrayInputStream(data)).readInt();
	}

	/**
	 * Read a string from the given byte array.
	 * @param data the byte array to be read.
	 * @return the read string.
	 */
	public String readString(byte[] data)
	{
		return new String(data);
	}


	@Override
	public void run()
	{
		try
		{
			/* if stop */
			if (stop.get()) throw new InterruptedException();

			switch (clientStatus.getSection())
			{
				case 1:
					handle_1_1();
					break;

				case 2:
					switch (clientStatus.getStep())
					{
						case 1:
							handle_2_1();
							break;
						case 2:
							handle_2_2();
							break;
					}
					break;

				case 3:
					switch (clientStatus.getStep())
					{
						case 1:
							handle_3_1();
							break;
						case 2:
							handle_3_2();
							break;
						case 3:
							handle_3_3();
							break;
					}
					break;

				case 4:
					handle_4_1();
					break;

				case 5:
					handle_5_1();
					break;

				case 6:
					handle_6_1();
					break;
			}
		}
		catch (Exception e)
		{
			try
			{
				System.out.println("EXCEPTION: " + e.getMessage());
				/* send ACK = -1 immediately */
				toClient.write(-1);

				/* close client session */
				client.close();
			}
			catch (IOException ioe) {/* nothing to do */}
			key.cancel();
		}
	}

	/**
	 * INPUT:
	 * 1: register
	 * 2: log in
	 * 3: quit
	 */
	private void handle_1_1() throws Exception
	{
		/* read client action */
		clientAction = readInt(data);

		/* if client wnats to register */
		if (clientAction == 1)
		{
			/* update status */
			clientStatus.setSection(3);
			clientStatus.setStep(1);
		}

		/* if client wants to log in */
		else if (clientAction == 2)
		{
			/* update status */
			clientStatus.setSection(2);
			clientStatus.setStep(1);
		}

		/* send ACK = 0 */
		clientStatus.setAck(0);

		/* if quit */
		if (clientAction == 3)
		{
			/* send ACK = 0 immediately */
			toClient.write(0);
			
			/* close client session */
			client.close();
			key.cancel();
		}
	}

	/**
	 * INPUT:
	 * username
	 */
	private void handle_2_1() throws Exception
	{
		/* read username */
		clientAnswer = readString(data);

		/* if username doesn't exist */
		if (!usersDB.containsKey(clientAnswer))
		{
			/* send ACK = 1 */
			clientStatus.setAck(1);

			/* keep client status unchanged */
			return;
		}

		/* if user with given username is already logged in */
		if (usersDB.get(clientAnswer).getLogStatus())
		{
			/* send ACK = 2 */
			clientStatus.setAck(2);

			/* keep client status unchanged */
			return;
		}

		/* update status */
		clientStatus.setName(clientAnswer);
		clientStatus.setSection(2);
		clientStatus.setStep(2);

		/* send ACK = 0 */
		clientStatus.setAck(0);
	}

	/**
	 * INPUT:
	 * password
	 */
	private void handle_2_2() throws Exception
	{
		User user = usersDB.get(clientStatus.getName());

		/* read password */
		clientAnswer = readString(data);
		clientStatus.addPassAttempt();

		/* compute hash value of password */
		MessageDigest sha = null;
		try {sha = MessageDigest.getInstance("SHA-256");}
		catch (NoSuchAlgorithmException e) {/* never thrown */}
		byte[] hash = sha.digest(clientAnswer.getBytes());

		/* if password is correct */
		if (Arrays.equals(hash, user.getPasswd()))
		{
			/* log user in */
			user.setLogStatus(true);

			/* update status */
			clientStatus.zeroPassAttempts();
			clientStatus.setSection(4);
			clientStatus.setStep(1);

			/* send ACK = 0 */
			clientStatus.setAck(0);

			return;
		}

		/* if client got password wrong too many times */
		if (clientStatus.getPassAttempts() == maxLogPassAttempts)
		{
			/* send ACK = 2 */
			clientStatus.setAck(2);

			/* terminate client session */
			client.close();
			key.cancel();
		}

		else
		{
			/* send ACK = 1 */
			clientStatus.setAck(1);
		}
	}

	/**
	 * INPUT:
	 * username
	 */
	private void handle_3_1() throws Exception
	{
		/* read username */
		clientAnswer = readString(data);

		/* check that this username isn't already being chosen by another client */
		synchronized (tmpNames)
		{
			if (!tmpNames.contains(clientAnswer))
				tmpNames.add(clientAnswer);
			else
			{
				clientStatus.setAck(1);
				return;
			}
		}

		/* if username exists */
		if (usersDB.containsKey(clientAnswer))
		{
			/* remove this username from tmpNames */
			tmpNames.remove(clientAnswer);

			/* send ACK = 1 */
			clientStatus.setAck(1);

			/* keep client status unchanged */
			return;
		}

		/* update status */
		clientStatus.setName(clientAnswer);
		clientStatus.setSection(3);
		clientStatus.setStep(2);

		/* send ACK = 0 */
		clientStatus.setAck(0);
	}

	/**
	 * INPUT:
	 * password
	 */
	private void handle_3_2() throws Exception
	{
		/* read password */
		clientAnswer = readString(data);

		/* if password is too short */
		if (clientAnswer.length() < minPassLength)
		{
			/* send ACK = minPassLength */
			clientStatus.setAck(minPassLength);

			/* keep client status unchanged */
			return;
		}

		/* update status */
		clientStatus.setPasswd(clientAnswer);
		clientStatus.setSection(3);
		clientStatus.setStep(3);

		/* send ACK = 0 */
		clientStatus.setAck(0);
	}

	/**
	 * INPUT:
	 * password
	 */
	private void handle_3_3() throws Exception
	{
		/* read password */
		clientAnswer = readString(data);

		/* if it doesn't match with previous one */
		if (!clientAnswer.equals(clientStatus.getPasswd()))
		{
			/* update status */
			clientStatus.setSection(3);
			clientStatus.setStep(2);

			/* send ACK = 1 */
			clientStatus.setAck(1);

			return;
		}

		/* compute hash value of password */
		MessageDigest sha = null;
		try {sha = MessageDigest.getInstance("SHA-256");}
		catch (NoSuchAlgorithmException e) {/* never thrown */}
		byte[] hash = sha.digest(clientAnswer.getBytes());

		/* create new user */
		usersDB.put(clientStatus.getName(), new User(clientStatus.getName(), hash, wordleMaxAttempts));

		/* remove this username from tmpNames */
		tmpNames.remove(clientAnswer);

		/* update status */
		clientStatus.clearPasswd(); // for security reasons
		clientStatus.setSection(4);
		clientStatus.setStep(1);

		/* send ACK = 0 */
		clientStatus.setAck(0);
	}

	/**
	 * INPUT:
	 * 1: play wordle
	 * 2: send me statistics
	 * 4: log out
	 * 5: quit
	 */
	private void handle_4_1() throws Exception
	{
		User user = usersDB.get(clientStatus.getName());

		/* read client action */
		clientAction = readInt(data);

		/* if client wants to play wordle */
		if (clientAction == 1)
		{
			/* if client already played current secret word */
			if (secretWord.equals(user.getLastWord()))
			{
				/* send ACK = 1 */
				clientStatus.setAck(1);

				/* keep client status unchanged */
				return;
			}

			/* update client's last word */
			user.setLastWord(secretWord);

			/* prepare share object for later sharing */
			Share share = new Share(user.getName(), gameNo, wordleMaxAttempts);

			/* update status */
			clientStatus.setLastWordContent(swContent);
			clientStatus.zeroWordleAttempts();
			clientStatus.setShare(share);
			clientStatus.setSection(5);
			clientStatus.setStep(1);

			/* send ACK = 0 */
			clientStatus.setAck(0);

			return;
		}

		/* if client wants statistics */
		if (clientAction == 2)
		{
			/* retrieve client statistics */
			UserStats clientStats = user.getStats();

			/* create json statistics string */
			Gson gson = new Gson();
			Type statsType = new TypeToken<UserStats>(){}.getType();
			String statsString = gson.toJson(clientStats, statsType);

			/* send ACK = 0 */
			clientStatus.setAck(0);

			/* send json string */
			clientStatus.setMessage(statsString);

			/* keep client status unchanged */
			return;
		}

		/* log user out */
		user.setLogStatus(false);

		/* if client wants to log out */
		if (clientAction == 4)
		{
			/* update status */
			clientStatus.setSection(1);
			clientStatus.setStep(1);

			/* send ACK = 0 */
			clientStatus.setAck(0);

			return;
		}

		/* send ACK = 0 immediately */
		toClient.write(0);

		/* terminate client session */
		client.close();
		key.cancel();
	}

	/**
	 * INPUT:
	 * guessed word
	 */
	private void handle_5_1() throws Exception
	{
		User user = usersDB.get(clientStatus.getName());

		/* read guessed word */
		clientAnswer = readString(data);

		/* check if guessed word is legal */
		if (clientAnswer.length() != wordLength || !WordsFileHandler.exists(clientAnswer))
		{
			/* send ACK = 3 */
			clientStatus.setAck(3);

			/* keep client status unchanged */
			return;
		}

		clientStatus.addWordleAttempt();

		/* build hint */
		swContent = clientStatus.getLastWordContent();
		StringBuilder hint = new StringBuilder();
		ArrayList<Integer> occurrences;
		boolean rightPos;
		for (int i = 0; i < wordLength; i++)
		{
			occurrences = swContent.get(clientAnswer.charAt(i));
			if (occurrences == null)
			{
				hint.append('X');
				continue;
			}
			rightPos = false;
			for (int j = 1; j <= occurrences.get(0); j++)
			{
				if (occurrences.get(j) == i)
				{
					rightPos = true;
					break;
				}
			}
			if (rightPos)
				hint.append('+');
			else
				hint.append('?');
		}

		/* save hint for later sharing */
		clientStatus.getShare().newAttempt(hint.toString());

		/* if guessed word is secret word */
		if (clientAnswer.equals(user.getLastWord()))
		{
			/* update client statistics */
			user.getStats().addVictory(clientStatus.getWordleAttempts());

			/* update status */
			clientStatus.setSection(6);
			clientStatus.setStep(1);

			/* send ACK = 0 */
			clientStatus.setAck(0);

			return;
		}

		/* if user attempted to find secret word too many times */
		if (clientStatus.getWordleAttempts() == wordleMaxAttempts)
		{
			/* update client statistics */
			user.getStats().addDefeat();

			/* update status */
			clientStatus.setSection(6);
			clientStatus.setStep(1);

			/* send ACK = 2 */
			clientStatus.setAck(2);

			return;
		}

		/* send ACK = 1 */
		clientStatus.setAck(1);

		/* send hint */
		clientStatus.setMessage(hint.toString());

		/* keep client status unchanged */
		return;
	}

	/**
	 * INPUT:
	 * 1: share
	 * 2: don't share
	 */
	private void handle_6_1() throws Exception
	{
		/* read client action */
		clientAction = readInt(data);

		/* if client doesn't want to share game results */
		if (clientAction == 2)
		{
			/* update status */
			clientStatus.setSection(4);
			clientStatus.setStep(1);

			/* send ACK = 0 */
			clientStatus.setAck(0);

			return;
		}

		/* create json string from share object */
		Gson gson = new Gson();
		Type shareType = new TypeToken<Share>(){}.getType();
		String jsonShare = gson.toJson(clientStatus.getShare(), shareType);

		/* send json string on multicast group */
		byte[] buffer = jsonShare.getBytes();
		mcSocket.send(new DatagramPacket(buffer, buffer.length, groupAddr, groupPort));

		/* update status */
		clientStatus.setSection(4);
		clientStatus.setStep(1);

		/* send ACK = 0 */
		clientStatus.setAck(0);
	}
}




























