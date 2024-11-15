import java.nio.channels.SocketChannel;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.io.IOException;


public class ChannelWriter
{
	/* buffer for output integers */
	private final ByteBuffer intBuffer = ByteBuffer.allocate(4);

	/* socket channel to write to */
	private SocketChannel channel;


	/**
	 * Instantiate a new channel writer.
	 * @param channel the channel to write to.
	 */
	public ChannelWriter(SocketChannel channel)
	{
		this.channel = channel;
	}

	/**
	 * Write an integer to the channel.
	 * @param number integer to write.
	 * @throws IOException if an I/O error occurs.
	 */
	public void write(int number) throws IOException
	{
		intBuffer.putInt(number);
		intBuffer.flip();
		channel.write(intBuffer);
		intBuffer.clear();
	}

	/**
	 * Write a string to the channel.
	 * @param message message to write.
	 * @throws IOException if an I/O error occurs.
	 */
	public void write(String message) throws IOException
	{
		channel.write(ByteBuffer.wrap(message.getBytes()));
	}
}