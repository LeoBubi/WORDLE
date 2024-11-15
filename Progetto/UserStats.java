import java.util.Arrays;


public class UserStats
{
	/* number of games played */
	private int gamesNo = 0;
	/* number of victories */
	private int victories = 0;
	/* number of defeats */
	private int defeats = 0;
	/* rate of victories */
	private float victoriesPerc = 0;
	/* last streak of victories */
	private int lastStreak = 0;
	/* longest streak of victories */
	private int maxStreak = 0;
	/* keep track of how many attempts took user to win played games */
	private int[] guesses;
	/* keep track of attempts distribution */
	private float[] guessDistr;
	/* attempts that took user to win last game, if won */
	private int lastGuessNo;

	/**
	 * Instantiate new user statistics object.
	 * @param maxAttempts maximum attempts for a user to guess secret word.
	 */
	public UserStats(int maxAttempts)
	{
		guesses = new int[maxAttempts];
		Arrays.fill(guesses, 0);
		guessDistr = new float[maxAttempts];
		Arrays.fill(guessDistr, 0);
	}

	/**
	 * @param guessNo number of attempts that took user to win last game.
	 */
	public void addVictory(int guessNo)
	{
		victories++;
		lastStreak++;
		lastGuessNo = guessNo;
		update(true);
	}

	public void addDefeat()
	{
		defeats++;
		lastStreak = 0;
		update(false);
	}

	private void update(boolean victory)
	{
		gamesNo = victories + defeats;
		victoriesPerc = (float)victories / (float)gamesNo * 100;
		if (maxStreak < lastStreak)
			maxStreak = lastStreak;
		
		if (victory)
		{
			/* update guess distribution */
			guesses[lastGuessNo-1]++;
			for (int i = 0; i < guessDistr.length; i++)
				guessDistr[i] = (float)(guesses[i]) / (float)victories * 100;
		}
	}
	/* gamesNo */
	public int getGames() {return gamesNo;}
	/* victoriesPerc */
	public float getVictoriesPerc() {return victoriesPerc;}
	/* lastStreak */
	public int getLastStreak() {return lastStreak;}
	/* maxStreak */
	public int getMaxStreak() {return maxStreak;}
	/* (reference to) guessDistr */
	public float[] getGuessDistribution() {return Arrays.copyOf(guessDistr, guessDistr.length);}
}