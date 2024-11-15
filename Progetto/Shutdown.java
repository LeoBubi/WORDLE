import java.util.HashMap;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.lang.Thread;
import java.lang.reflect.Type;
import java.io.FileWriter;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonWriter;


public class Shutdown extends Thread
{
	/* read admin input */
	private final Scanner adminInput = new Scanner(System.in);
	/* reference - listen for new client connection requests */
	private AtomicBoolean listen;
	/* reference - close all client connections */
	private AtomicBoolean stop;
	/* reference - local users database */
	private HashMap<String,User> usersDB;
	/* users backup file */
	private String usersFile;
	/* seconds to wait before disconnecting from all connected clients */
	private int shutTimeout;


	/**
	 * Instantiate a new shutdown controller.
	 * @param listen tells the server listen for new clients or not.
	 * @param stop tells the server to disconnect from all connected clients.
	 * @param usersDB users databse.
	 * @param usersFile users backup file name.
	 * @param shutTimeout seconds to wait before disconnecting from all connected clients.
	 */
	public Shutdown(AtomicBoolean listen, AtomicBoolean stop, HashMap<String,User> usersDB, String usersFile, int shutTimeout)
	{
		this.listen = listen;
		this.stop = stop;
		this.usersDB = usersDB;
		this.usersFile = usersFile;
		this.shutTimeout = shutTimeout;
	}

	@Override
	public void run()
	{
		while (true)
		{
			if (adminInput.hasNextLine())
			{
				/* if admin wants to shut down this server */
				if (adminInput.nextLine().equals("shutdown"))
					break;
				System.out.println("Type \'shutdown\' to start the gradual shutdown of this server.");
			}
		}
		try
		{
			/* inform admin */
			System.out.println("Started shutdown process...");

			/* stop accepting new clintes connection requests */
			listen.set(false);

			/* inform admin */
			System.out.println("...stopped listening for new clients...");

			/* wait */
			Thread.sleep(shutTimeout * 1000);

			/* close all client connections */
			stop.set(true);

			/* inform admin */
			System.out.println("...closed all client connections...");

			/* wait */
			Thread.sleep(10000); // 10 seconds

			/* log all users out */
			usersDB.forEach((name,user) -> user.setLogStatus(false));

			/* inform admin */
			System.out.println("...logged all users out...");

			/* export usersDB to json users file */
			FileWriter jsonFile = new FileWriter(usersFile);
			Type userType = new TypeToken<User>(){}.getType();
			Gson gson = new Gson();
			JsonWriter writer = new JsonWriter(jsonFile);

			writer.beginArray();
			usersDB.forEach((name,user) -> gson.toJson(user, userType, writer));
			writer.endArray();
			writer.close();

			/* inform admin */
			System.out.println("...users database backed up to file: " + usersFile + "...");

			/* terminate execution, inform admin */
			System.out.println("... terminated shutdown process.");
			System.exit(0);
		}
		catch (Exception e)
		{
			System.err.println("FATAL ERROR: an error occured during shutdown");
			System.exit(1);
		}
	}
}