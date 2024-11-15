import java.util.Arrays;
import java.util.HashMap;
import java.util.ArrayList;
import java.lang.Thread;


public class ClientStatus
{
	/* client unique id */
	private int id;
	/* section & step - see server's flowchart*/
	private int section = 1;
	private int step = 1;
	/* client name */
	private String username;
	/* client's candidate password (for registration) */
	private String passwd;
	/* password attempts (for login) */
	private int logPassAttempts = 0;
	/* word attempts in last game */
	private int wordleAttempts = 0;
	/* last secret word content (for comparison with guessed words) */
	private HashMap<Character,ArrayList<Integer>> lastWordContent;
	/* client current sharing object */
	private Share share;

	/* server ack to be sent to client */
	private int serverAck;
	/* server message to be sent to client */
	private String serverMessage;
	/* prevent the same ack/message to be read from client more than once */
	/* 0: can't get */
	/* 1: can get ack */
	/* 2: can get message */
	private int canGet = 0; // no need to have it atomic


	/**
	 * Instantiate a new client status associated with a new client.
	 * @param id the unique client id.
	 */
	public ClientStatus(int id)
	{
		this.id = id;
	}

	/*id*/
	public int getID() {return id;}
	/* section */
	public int getSection() {return section;}
	public void setSection(int nextSection) {section = nextSection;}
	/*step*/
	public int getStep() {return step;}
	public void setStep(int nextStep) {step = nextStep;}
	/*username*/
	public String getName() {return username;}
	public void setName(String name) {username = name;}
	/* passwd */
	public String getPasswd() {return passwd;}
	public void setPasswd(String pass) {passwd = pass;}
	public void clearPasswd() {passwd = null;}
	/* logPassAttempts */
	public int getPassAttempts() {return logPassAttempts;}
	public void addPassAttempt() {logPassAttempts++;}
	public void zeroPassAttempts() {logPassAttempts = 0;}
	/* wordleAttempts */
	public int getWordleAttempts() {return wordleAttempts;}
	public void addWordleAttempt() {wordleAttempts++;}
	public void zeroWordleAttempts() {wordleAttempts = 0;}
	/* lastWordContent */
	public HashMap<Character,ArrayList<Integer>> getLastWordContent() {return new HashMap<Character,ArrayList<Integer>>(lastWordContent);}
	public void setLastWordContent(HashMap<Character,ArrayList<Integer>> newContent) {lastWordContent = newContent;}
	/* share */
	public Share getShare() {return share;}
	public void setShare(Share newShare) {share = newShare;}

	/* serverAck */
	public int getAck()
	{
		int ack = serverAck;
		canGet = 0;
		return ack;
	}
	public void setAck(int ack)
	{
		/* make sure that client has time to read previous ack/message */
		while (canGet != 0)
		{
			try {Thread.sleep(100);}
			catch (InterruptedException e)
			{continue;}
		}
		serverAck = ack;
		canGet = 1;
	}
	/* serverMessage */
	public String getMessage()
	{
		String message = serverMessage;
		canGet = 0;
		return message;
	}
	public void setMessage(String message)
	{
		/* make sure that client has time to read previous ack/message */
		while (canGet != 0)
		{
			try {Thread.sleep(100);}
			catch (InterruptedException e)
			{continue;}
		}
		serverMessage = message;
		canGet = 2;
	}
	/* canGet */
	public int canGetData() {return canGet;}
}




















