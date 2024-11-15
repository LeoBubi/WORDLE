import java.io.*;
import java.lang.StringBuilder;
import java.lang.Math;
import java.lang.Thread;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class SWHandler extends Thread
{
	/* reference - current secret word */
	private StringBuilder secretWord;
	/* reference - content of current secret word */
	private HashMap<Character,ArrayList<Integer>> swContent;
	/* reference - opened words file */
	private RandomAccessFile wordsFile;
	/* length of a word (in bytes) */
	private int wordLength;
	/* time for a new secret word to be extracted (in minutes) */
	private int nextSWTime;
	/* game number */
	private AtomicInteger gameNo;


	/**
	 * Instantiate a new secret word handler.
	 * @param secretWord reference to the secret word.
	 * @param swContent reference to content of the secret word.
	 * @param wordsFile reference to the words file.
	 * @param wordLength length of a word (in bytes).
	 * @param nextSWTime time for a new secret word to be extracted (in minutes).
	 * @param gameNo game number.
	 */
	public SWHandler(StringBuilder secretWord, HashMap<Character,ArrayList<Integer>> swContent, RandomAccessFile wordsFile, int wordLength, int nextSWTime, AtomicInteger gameNo)
	{
		this.secretWord = secretWord;
		this.swContent = swContent;
		this.wordsFile = wordsFile;
		this.wordLength = wordLength;
		this.nextSWTime = nextSWTime;
		this.gameNo = gameNo;
	}

	@Override
	public void run()
	{
		String sw;
		try
		{
			while (true)
			{
				/* update game number */
				gameNo.incrementAndGet();

				/* replace old secret word */
				secretWord.delete(0, wordLength+1); // beware of \0
				sw = WordsFileHandler.getRandomWord();
				secretWord.append(sw);
				System.out.println("\nSecret word changed to: " + secretWord.toString() + "\n");

				/* define secret word's content */
				char c;
				swContent.clear();
				for (int i = 0; i < wordLength; i++)
				{
					c = sw.charAt(i);
					if (!swContent.containsKey(c))
					{
						ArrayList<Integer> occurrences = new ArrayList<Integer>(wordLength);
						occurrences.add(1);
						occurrences.add(i);
						swContent.put(c, occurrences);
					}
					else
					{
						ArrayList<Integer> occurrences = swContent.get(c);
						int occNo = occurrences.get(0);
						occurrences.set(0, occNo+1);
						occurrences.add(i);
					}
				}

				/* wait */
				Thread.sleep(nextSWTime * 60000);
			}
		}
		catch (IOException e)
		{
			System.err.println("FATAL ERROR: failure while reading words file.");
			System.exit(1);
		}
		catch (InterruptedException e)
		{
			System.err.println("FATAL ERROR: failure while waiting for next secret word.");
			System.exit(1);
		}
	}
}