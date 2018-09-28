package com.lazarus.adblock.connections;

import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;

import com.lazarus.adblock.exceptions.AdblockException;

public class RemoteCommunicationRunnable extends CommunicationRunnable {

	public RemoteCommunicationRunnable(String name) throws AdblockException {
		super(name);
	}

	/*
	 * (non-Javadoc)
	 * @see com.lazarus.adblock.connections.CommunicationRunnable#getCommReadChannel(com.lazarus.adblock.connections.Connection)
	 * 
	 * returns the remote channel of a connection.
	 * this method is called by the super class to determine the
	 * appropriate channel to set to unblock mode and register to
	 * selector.
	 */
	@Override
	protected SelectableChannel getCommReadChannel(Connection connection) {
		return connection.getRemoteChannel();
	}

	/*
	 * (non-Javadoc)
	 * @see com.lazarus.adblock.connections.CommunicationRunnable#read(java.nio.channels.SelectionKey)
	 * 
	 * this method calls the appropriate method in the selected
	 * connection to read from the remote channel.
	 * this method is called when the remote channel associated with
	 * the key is ready for read.
	 */
	@Override
	protected void read(SelectionKey key) {
		/*
		 * the remote channel is ready for read
		 */
		Connection conn = (Connection)key.attachment();
		conn.readFromRemoteChannel();
	}
	
	/*
	 * (non-Javadoc)
	 * @see com.lazarus.adblock.connections.CommunicationRunnable#registerConnection(com.lazarus.adblock.connections.Connection)
	 * 
	 * setting the super class's buffer as the remote channel buffer in the
	 * connection must be done here, since after the connection is registered
	 * it can be ready for read.
	 */
	@Override
	public void registerConnection(Connection connection) throws AdblockException {
		connection.setRemoteChannelBuffer(byteBuffer);
		super.registerConnection(connection);
	}

}
