import java.nio.channels.SocketChannel;
import java.nio.ByteBuffer;
import java.io.IOException;


public class ChannelReader
{
	/* socket channel to read from */
	private SocketChannel channel;


	/**
	 * Instantiate a new channel reader.
	 * @param channel the channel to read from.
	 */
	public ChannelReader(SocketChannel channel)
	{
		this.channel = channel;
	}

	/**
	 * Read raw data from the channel.
	 * @return the read raw data in a byte array.
	 * @throws IOException if an I/O error occurs.
	 */
	public byte[] read() throws IOException
	{
		ByteBuffer inBuffer = ByteBuffer.allocate(1024);
		int numBytes = channel.read(inBuffer);
		
		/* check if end-of-stream */
		if (numBytes == -1)
			return null;

		inBuffer.flip();
		byte[] inData = new byte[inBuffer.limit()];
		inBuffer.get(inData, 0, inBuffer.limit());
		return inData;
	}

	/**
	 * Read an integer from the channel.
	 * @return the read integer.
	 * @throws IOException if an I/O error occurs.
	 */
	public int readInt() throws IOException
	{
		ByteBuffer inBuffer = ByteBuffer.allocate(4);
		channel.read(inBuffer);
		inBuffer.flip();
		return inBuffer.getInt();
	}

	/**
	 * Read a string from the channel.
	 * @return the read string.
	 * @throws IOException if an I/O error occurs.
	 */
	public String readString() throws IOException
	{
		ByteBuffer inBuffer = ByteBuffer.allocate(1024);
		channel.read(inBuffer);
		inBuffer.flip();
		return new String(inBuffer.array());
	}
}