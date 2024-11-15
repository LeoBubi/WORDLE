import java.util.Arrays;

public class User
{
	/* name */
	private String name;
	/* hash value of password */
	private byte[] passwd;
	/* log status */
	private boolean loggedIn = true;
	/* last secret word play */
	private String lastWord = null;
	/* statistics */
	private UserStats stats;

	/**
	 * Create a new user.
	 * @param name new user's name.
	 * @param passwd hash value of password new user's password.
	 * @param wordleMaxAttempts maximum attempts for a user to guess secret word.
	 */
	public User(String name, byte[] passwd, int wordleMaxAttempts)
	{
		this.name = name;
		this.passwd = passwd;
		this.stats = new UserStats(wordleMaxAttempts);
	}

	/* name */
	public String getName() {return name;}
	/* (copy of) passwd */
	public byte[] getPasswd() {return Arrays.copyOf(passwd, passwd.length);}
	/* loggedIn */
	public boolean getLogStatus() {return loggedIn;}
	public void setLogStatus(boolean status) {loggedIn = status;}
	/* lastWord */
	public String getLastWord() {return lastWord;}
	public void setLastWord(String newWord) {lastWord = newWord;}
	/* (reference to) stats */
	public UserStats getStats() {return stats;}
}