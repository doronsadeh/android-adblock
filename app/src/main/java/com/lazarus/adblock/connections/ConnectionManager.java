package com.lazarus.adblock.connections;

import android.util.Log;

import com.lazarus.adblock.exceptions.AdblockException;

import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SocketChannel;

/*
 * ConnectionManager is a singleton that manages the current
 * open connections.
 * It is responsible for opening/closing connections,
 * and managing them in the connections threads and selectors.
 */
public class ConnectionManager {

	private static final String TAG = "ConnectionManager";

	private static ConnectionManager instance = null;

	private ConnectionsTable connectionsTable = ConnectionsTable.getInstance();
	private AppsConnections appsMap = AppsConnections.getInstance();

	// TCP
	private LocalCommunicationRunnable localTcpComm;
	private RemoteCommunicationRunnable remoteTcpComm;
	private Thread localTcpCommThread;
	private Thread remoteTcpCommThread;

	private ConnectionManager() throws AdblockException {
		createTcpConnectionThreads();
		// TODO: Set up and run the local and endpoint threads
	}

	public void close() {
		deleteTcpConnectionThreads();
		Log.d(TAG, "connection manager is closed");
	}

	public static ConnectionManager getInstance() throws AdblockException {
		if (instance == null)
			instance = new ConnectionManager();
		return instance;
	}

	public static String getUidForStream(Tuple t) {
		return AppsConnections.getInstance().getUidForStream(t);
	}

	/*
	 * create tcp connection
	 */
	public void createConnection(SocketChannel localSocketChannel) throws AdblockException {

		Tuple tuple = Connection.getTuple(Tuple.PROTOCOL_TCP, localSocketChannel.socket().getPort());
		TCPConnection tcpConnection = new TCPConnection(localSocketChannel, tuple);

		localTcpComm.registerConnection(tcpConnection);
		Log.d(TAG, "Registered tcp connection " + tuple.toString() + " to " + localTcpCommThread.getName());

		remoteTcpComm.registerConnection(tcpConnection);
		Log.d(TAG, "Registered tcp connection " + tuple.toString() + " to " + remoteTcpCommThread.getName());

		// Update the connection table itseld
		connectionsTable.put(tuple, tcpConnection);

		// Update Apps UIDs table
		if (tuple.getUid() != null)
			appsMap.put(tuple.getUid(), tuple);

		Log.d(TAG, "tcp connection has been added to connections table " + tuple.toString());
	}

	public void closeConnection(Connection connection) {

		if (connection instanceof TCPConnection) {
			//unregisters the local channel of the connection from local selector
			localTcpComm.unregisterConnection(connection);

			//unregisters the remote channel of the connection from remote selector
			remoteTcpComm.unregisterConnection(connection);
		}

		// Remove from connections table
		connectionsTable.getInstance().remove(connection);
		
		// Remove from apps table
		appsMap.getInstance().remove(connection);
		
		//close local and remote channel of the connection
		connection.close();
	}

	private void createTcpConnectionThreads() throws AdblockException {

		localTcpComm = new LocalCommunicationRunnable("local_tcp_comm");
		remoteTcpComm = new RemoteCommunicationRunnable("remote_tcp_comm");

		localTcpCommThread = new Thread(localTcpComm, "local_tcp_thread");
		remoteTcpCommThread = new Thread(remoteTcpComm, "remote_tcp_thread");

		localTcpCommThread.start();
		remoteTcpCommThread.start();
	}

	/*
	 * this method ports an interrupt request to local and remote tcp
	 * communication threads. the tcp communication threads (runnables)
	 * are interrupted and halted whether waiting or selecting or performing
	 * i/o operation.
	 */
	private void deleteTcpConnectionThreads() {

		/*
		 * interrupt local and remote tcp communication threads
		 */
		localTcpCommThread.interrupt();
		remoteTcpCommThread.interrupt();

		//TODO: close all connections
	}
}
