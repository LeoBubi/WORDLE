import java.util.Arrays;

public class Share
{
	/* user name */
	private String user;
	/* number of this game */
	private int gameNo;
	/* attempts that took user to finish the game */
	private int attemptsNo = 0;
	/* game scheme built from hints */
	private String[] scheme;
	/* maximum attempts for a user to guess secret word */
	private int wordleMaxAttempts;

	/**
	 * Instantiate new share object.
	 * @param user user name.
	 * @param gameNo number of the game.
	 * @param maxAttempts maximum attempts for a user to guess secret word.
	 */
	public Share(String user, int gameNo, int maxAttempts)
	{
		this.user = user;
		this.gameNo = gameNo;
		this.scheme = new String[maxAttempts];
		this.wordleMaxAttempts = maxAttempts;
	}

	/**
	 * Add a new attempt to guess secret word.
	 * @param attempt hint representing comprarison between guessed and secret word.
	 */
	public void newAttempt(String attempt)
	{
		scheme[attemptsNo] = attempt;
		attemptsNo++;
	}

	/* user */
	public String getUser() {return user;}
	/* gameNo */
	public int getGame() {return gameNo;}
	/* attemptsNo */
	public int getAttempts() {return attemptsNo;}
	public String getAttemptsString() {return (attemptsNo + "/" + wordleMaxAttempts);}
	/* scheme */
	public String[] getGameScheme() {return Arrays.copyOf(scheme, attemptsNo);}
}