import java.util.Random;
import java.lang.Math;
import java.io.RandomAccessFile;
import java.io.IOException;


public class WordsFileHandler
{
	/* pseudo-random number generator */
	private static final Random generator = new Random(System.currentTimeMillis());

	/* file containing all the words for the game */
	private static RandomAccessFile wordsFile;
	/* length of each word (in bytes) */
	private static int wordLength;
	/* number of words in wordsFile */
	private static long wordsQty;
	/* buffer for reading a word from wordsFile */
	private static byte[] buffer;

	/**
	 * @param wordsFile file containing all the words for the game.
	 * @param wordLength length of each word (in bytes).
	 * @throws IOException if an I/O error occurs.
	 */
	public WordsFileHandler(RandomAccessFile wordsFile, int wordLength) throws IOException
	{
		this.wordsFile = wordsFile;
		this.wordLength = wordLength;

		buffer = new byte[wordLength];
		wordsQty = wordsFile.length() / (wordLength+1); // beware of \n

	}

	/**
	 * Get a random word from wordsFile
	 * @return a random word as a String.
	 * @throws IOException if an I/O error occurs.
	 */
	public static String getRandomWord() throws IOException
	{
		/* move randomly in the file */
		wordsFile.seek((long)Math.floor(generator.nextDouble() * wordsQty) * (wordLength+1)); // beware of \n
		/* read a single word */
		wordsFile.read(buffer);

		return new String(buffer); // \n removed
	}

	/**
	 * Do a binary search on wordsFile to verify if it contains the specified word.
	 * @param keyWord the word to check for existance in wordsFile.
	 * @return true if keyWord is is wordsFile, false otherwise.
	 * @throws IOException if an I/O error occurs.
	 */
	public static boolean exists(String keyWord) throws IOException
	{
		long lower = 0;
		long upper = wordsQty-1;
		long mid;
		String word;
		int comparison;

		while (lower <= upper)
		{
			mid = (lower + upper) / 2;
			wordsFile.seek(mid * (wordLength+1));
			wordsFile.read(buffer);
			word = new String(buffer);

			comparison = keyWord.compareTo(word);
			if (comparison == 0)
				return true;
			if (comparison < 0)
				upper = mid - 1;
			else
				lower = mid + 1;
		}
		return false;
	}
}