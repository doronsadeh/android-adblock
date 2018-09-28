package com.lazarus.adblock.connections;

import android.util.Log;
import android.widget.Toast;

import com.lazarus.adblock.exceptions.AdblockException;
import com.lazarus.adblock.filters.Filter;
import com.lazarus.adblock.filters.Filter.Type;
import com.lazarus.adblock.filters.Opinion;
import com.lazarus.adblock.filters.Opinion.Mode;
import com.lazarus.adblock.filters.pre.HttpFilter;
import com.lazarus.adblock.filters.pre.SSLFilter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.SocketChannel;

import libcore.tlswire.util.ToastIt;

import static libcore.tlswire.util.Conversions.hexStringToByteArray;

/*
 * TODO: document
 */
public class TCPConnection extends Connection {

	private static final String TAG = "TCPConnection";

	//number of open tcp connections (for debug)
	private static int num = 0;

    // Filter templates
	private static final Filter httpFilterTemplate = new HttpFilter(Type.PRE, null);
    private static final Filter sslFilterTemplate = new SSLFilter(Type.PRE, null);

    public TCPConnection(SocketChannel localSocketChannel, Tuple tuple) throws AdblockException {
		super();

		this.tuple = tuple;
		this.localChannel = localSocketChannel;

		try {
			remoteChannel = SocketChannel.open();
			((SocketChannel)remoteChannel).connect(tuple.getDstSocketAddress());
		} catch (IOException e) {
			throw new AdblockException("cannot open remote tcp socket");
		}

		Log.d(TAG, "tcp connection [" + tuple.toString() + "] is created (num=" + (++num) + ")");
	}

	/*
	 * this method applies the filtering logic on the traffic.
	 * it determines the appropriate (local or remote) byte buffer
	 * and destination socket channel according to data direction
	 * (incoming/outgoing):
	 * remote buffer and local channel for incoming (downstream),
	 * local buffer and remote channel for outgoing (upstream).
	 */
	private void filter(Direction dir) throws IOException {

		ByteBuffer byteBuffer = (dir == Direction.DOWNSTREAM ? remoteChannelByteBuffer : localChannelByteBuffer);
		SocketChannel destSocketChannel = (dir == Direction.DOWNSTREAM ? (SocketChannel)localChannel : (SocketChannel)remoteChannel);
		SocketChannel sourceSocketChannel = (dir == Direction.UPSTREAM ? (SocketChannel)localChannel : (SocketChannel)remoteChannel);

		// TODO! Consider allowing to modify the buffer, and return it via Opinion, or the parameters itself being modified
        //       this allows for the HttpFilter to inject the CSS into the buffer

		String uid = ConnectionManager.getUidForStream(tuple);
		Opinion opinion;
        opinion = process(byteBuffer,
                tuple,
                dir,
                detected,
                null);

        /*
		 * mark this connection for bypassing from now on
		 * 
		 * TODO: consider removing the current connection
		 * from being monitored thus avoid checking the
		 * bypass condition each time we have traffic
		 */
		if (opinion.mode == Mode.BYPASS)
			bypass = true;

		// set the decided connection mode
		connectionProxyMode = opinion.mode;

		switch (connectionProxyMode) {
		case BLOCK:
			blockTraffic(sourceSocketChannel, destSocketChannel, byteBuffer, opinion.entity);
			break;

		case PASS:
		case BYPASS:
			passTraffic(byteBuffer, destSocketChannel);
			break;
			
		default:
			Log.e(TAG, "Error! Bad opinion");
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.lazarus.adblock.connections.Connection#readFromLocalChannel()
	 * 
	 * this method reads from the local tcp socket channel until the
	 * channel has no data to read.
	 * it uses handleOutgoingTraffic() method to processes the data.
	 * if the local client has closed the connection (read() method
	 * returns -1), it calls initiateClose() methods which starts the
	 * connection closing flow using the connection manager.
	 */
	@Override
	public void readFromLocalChannel() {

	    // TODO [usermod] We need to override SocketChannel and impl. it on top of PicoTCP via JNI
		SocketChannel localSocketChannel = (SocketChannel)localChannel;
		int n;

		localChannelByteBuffer.clear();

		try {
			n = localSocketChannel.read(localChannelByteBuffer);
			
			if (n > 0) {

				//prepare the buffer for read
				localChannelByteBuffer.flip();

				handleOutgoingTraffic();

				localChannelByteBuffer.clear();
			}

			/*
			 * Remote entity (local device) shut the socket down cleanly.
			 * Do the same from our end and cancel the channel.
			 */
			if (n == -1) {
				Log.d(TAG, "local client has shut the tcp socket down cleanly, initiating connection close [" + tuple.toString() + "]");
				initiateClose();
			}
		} catch (AsynchronousCloseException e) {
			//ClosedByInterruptException is also handled here
			Log.e(TAG, "Lread: asynchronouscloseexception");
			e.printStackTrace();
			initiateClose();
		} catch (NotYetConnectedException e) {
			Log.e(TAG, "Lread: notyetconnectedexception");
			e.printStackTrace();
			initiateClose();
		} catch (ClosedChannelException e) {
			Log.e(TAG, "Lread: closedchannelexception");
			e.printStackTrace();
			initiateClose();
		} catch (IOException e) {
			Log.e(TAG, "Lread: ioexception: " + e.getMessage());
			e.printStackTrace();
			initiateClose();
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.lazarus.adblock.connections.Connection#readFromRemoteChannel()
	 * 
	 * this method reads from the remote tcp socket channel until the
	 * channel has no data to read.
	 * it uses handleIncomingTraffic() method to processes the data.
	 * if the remote client has closed the connection (read() method
	 * returns -1), it calls initiateClose() methods which starts the
	 * connection closing flow using the connection manager.
	 */
	@Override
	public void readFromRemoteChannel() {

		SocketChannel remoteSocketChannel = (SocketChannel)remoteChannel;
		int n;

		remoteChannelByteBuffer.clear();

		try {
			n = remoteSocketChannel.read(remoteChannelByteBuffer);
			
			if (n > 0) {

				//prepare the buffer for read
				remoteChannelByteBuffer.flip();

				handleIncomingTraffic();

				remoteChannelByteBuffer.clear();
			}

			/*
			 * Remote entity (remote client) shut the socket down cleanly.
			 * Do the same from our end and cancel the channel.
			 */
			if (n == -1) {
				Log.d(TAG, "remote client has shut the tcp socket down cleanly, initiating connection close [" + tuple.toString() + "]");
				initiateClose();
			}
		} catch (AsynchronousCloseException e) {
			//ClosedByInterruptException is also handled here
			Log.e(TAG, "Rread: asynchronouscloseexception");
			e.printStackTrace();
			initiateClose();
		} catch (NotYetConnectedException e) {
			Log.e(TAG, "Rread: notyetconnectedexception");
			e.printStackTrace();
			initiateClose();
		} catch (ClosedChannelException e) {
			Log.e(TAG, "Rread: closedchannelexception");
			e.printStackTrace();
			initiateClose();
		} catch (IOException e) {
			Log.e(TAG, "Rread: ioexception: " + e.getMessage());
			e.printStackTrace();
			initiateClose();
		}
	}

    /*
	 * this method handles the outgoing traffic from the connection.
	 * it uses the filter bank to process the traffic and act accordingly.
	 * it is called from readFromLocalChannel(), after reading outgoing
	 * data to byteBuffer.
	 * the byteBuffer's state must not be changed.
	 */
	private void handleOutgoingTraffic() throws IOException {

		//apply filtering logic on incoming traffic
		if (!bypass)
			filter(Direction.UPSTREAM);
		else
			passTraffic(localChannelByteBuffer, (SocketChannel)remoteChannel);
	}

	/*
	 * this method handles the incoming traffic from the connection.
	 * it is called from readFromRemoteChannel(), after reading incoming
	 * data to remoteChannelByteBuffer.
	 * the byteBuffer's state must not be changed.
	 */
	private void handleIncomingTraffic() throws IOException {

		//apply filtering logic on incoming traffic
		if (!bypass)
			filter(Direction.DOWNSTREAM);
		else
			passTraffic(remoteChannelByteBuffer, (SocketChannel)localChannel);
	}

	/*
	 * passes data (reads) from byte buffer (writes) to
	 * socket channel.
	 * byte buffer's state must not be changed (using a
	 * temporary duplicated buffer).
	 */
	private void passTraffic(ByteBuffer byteBuffer, SocketChannel socketChannel) throws IOException {

		/*
		 * duplicate the original buffer's state in order to keep original
		 * buffer's state unchanged.
		 */
		ByteBuffer tmpByteBuffer = byteBuffer.duplicate();

		// Keep writing till all buffer is out
		while (tmpByteBuffer.hasRemaining())
			socketChannel.write(tmpByteBuffer);
		
	}

	/*
	 * blocks data (byte buffer) flow from source channel to
	 * destination channel
	 * TODO: complete implementation
	 */
	private void blockTraffic(SocketChannel sourceSocketChannel,
                              SocketChannel destSocketChannel,
                              ByteBuffer byteBuffer,
                              String blockedEntity) {

		if (destSocketChannel != null && sourceSocketChannel != null)
			Log.d(TAG, "Blocking ... " + blockedEntity + ":" + destSocketChannel.socket().getPort());

		// Specific L4 protocols
		try {
			// TODO This is very wasteful, we need to keep one copy of each filter (we have those already) for lookup purposes, and use it as key to 'detected' here
			if (detected.containsKey(httpFilterTemplate)) {

				// Http

                ToastIt.toast("Blocking ad/HTTP " + blockedEntity, Toast.LENGTH_SHORT, false);
                Log.d(TAG, "Sending HTTP/404 to " + blockedEntity + ":" + destSocketChannel.socket().getPort());

				// TODO send the best hiding response (404 w/JS)
				// String response503 = "HTTP/1.1 503 Service Unavailable\n";
				// String response200 = "HTTP/1.1 200\n<html><body>Ad Blocked by the Lazarus Adblock</body></html>\n";
                String response404 = "HTTP/1.1 404\n";

				try {
					ByteBuffer buffer = ByteBuffer.wrap(response404.getBytes());
					while (buffer.hasRemaining())
						sourceSocketChannel.write(buffer);
				} catch (IOException e) {
					Log.e(TAG, "Failed blocking Http via 404 (" + e.getMessage() + ")");
				}
				finally {
                    initiateClose();
                }
			}
			else if (detected.containsKey(sslFilterTemplate)) {

			    // SSL/TLS

                ToastIt.toast("Blocking ad/TLS/SSL " + blockedEntity, Toast.LENGTH_SHORT, false);
                Log.d(TAG, "Blocking ad based on SNI, closing TLS/SSL connection for: " + blockedEntity);

                byte[] serverHelloFake = hexStringToByteArray("020000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000");
                try {
                    ByteBuffer buffer = ByteBuffer.wrap(serverHelloFake);
                    while (buffer.hasRemaining())
                        sourceSocketChannel.write(buffer);
                } catch (IOException e) {
                    Log.e(TAG, "Failed blocking TLS via fake ServerHello(" + e.getMessage() + ")");
                }
                finally {
                    // DEBUG lets see whta happens if we just fuck serverhello w/o closing connection
                    initiateClose();
                }
            }

			// TODO more L4 specific protocols

		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
	
	/*
	 * this method initiates the connection closing flow, signaling the
	 * connection manager that this connection needs to be closed.
	 * (invoked when readFromLocalChannel identifies that the local
	 * channel has been shut down cleanly). 
	 */
	private void initiateClose() {
		ConnectionManager connectionManager = null;
		try {
			connectionManager = ConnectionManager.getInstance();
		} catch (AdblockException e) {
			/*
			 * never reaches this code.
			 * getInstance can only throw a AdblockException in its
			 * initial run when creating instance of ConnectionManager.
			 * ConnectionManager already exists here.
			 */
			Log.e(TAG, "fatal");
		}
		connectionManager.closeConnection(this);

		/*		Log.d(TAG, "closing " + originSocketChannel.socket().getInetAddress().getHostAddress() + ":" + originSocketChannel.socket().getPort());
		key.channel().close();
		key.cancel();

		Log.d(TAG, "closing " + forwardSocketChannel.socket().getInetAddress().getHostAddress() + ":" + forwardSocketChannel.socket().getPort());
		SelectionKey forwardKey = forwardSocketChannel.keyFor(selector);
		forwardSocketChannel.close();
		forwardKey.cancel();*/
	}

	/*
	 * (non-Javadoc)
	 * @see com.lazarus.adblock.connections.Connection#close()
	 * 
	 * this method closes the local and remote socket channel.
	 * this tcp connection specific code is performed before calling
	 * the generic connection super class's close() method.
	 */
	public void close() {

		/*
		 * close local channel
		 */
		try {
			localChannel.close();
		} catch (ClosedChannelException e) {
			Log.e(TAG, "closedchannelexception while closing local channel: " + e.getMessage());
		} catch (IOException e) {
			Log.e(TAG, "ioexception while closing local channel: " + e.getMessage());
		}

		/*
		 * close remote channel
		 */
		try {
			remoteChannel.close();
		} catch (ClosedChannelException e) {
			Log.e(TAG, "closedchannelexception while closing remote channel: " + e.getMessage());
		} catch (IOException e) {
			Log.e(TAG, "ioexception while closing remote channel: " + e.getMessage());
		}

		super.close();

		Log.d(TAG, "tcp connection [" + tuple.toString() + "] is closed (num=" + (--num) + ")");
	}

}
