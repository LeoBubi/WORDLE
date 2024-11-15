import java.net.*;
import java.util.ArrayList;
import java.io.IOException;


public class MCHandler extends Thread
{
	/* size of buffer for incoming packets */
	private static final int BUFFER_SIZE = 1024;

	/* multicast socket for receiving users shares */
	private MulticastSocket mcSocket;
	/* reference - local shares database */
	private ArrayList<String> sharesDB;

	/* input buffer for receiving incoming packets */
	private byte[] inBuffer = new byte[BUFFER_SIZE];
	/* packet for receiving incoming packets */
	private DatagramPacket inPacket = new DatagramPacket(inBuffer, BUFFER_SIZE);

	/**
	 * Instantiate a new multicast handler for receiving and storing users shares.
	 * @param mcSocket multicast socket for receiving users shares.
	 * @param sharesDB reference to local shares database.
	 */
	public MCHandler(MulticastSocket mcSocket, ArrayList<String> sharesDB)
	{
		this.mcSocket = mcSocket;
		this.sharesDB = sharesDB;
	}

	@Override
	public void run()
	{
		try
		{
			while (true)
			{
				/* wait for receiving a new packet */
				mcSocket.receive(inPacket);
				/* store packet data in local database */
				sharesDB.add(new String(inPacket.getData(), inPacket.getOffset(), inPacket.getLength()));
			}
		}
		catch (SocketException e)
		{
			/* multicast socket has been closed by ClientMain, terminate normally */
			return;
		}
		catch (IOException e)
		{
			System.err.println("FATAL ERROR: multicast failure (forced closure)");
			System.exit(1);
		}
	}
}