package com.lazarus.adblock.connections;

import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Set;

import com.lazarus.adblock.exceptions.AdblockException;

/**
 * CommunicationRunnable is an abstract super class that implements
 * the runnable task of communication thread (currently only for tcp).
 * it creates a selector and its main loop selects (select())
 * the keys of the channels that are ready for read, then it calls
 * the specific subclass implemented read() method.
 * it consists of a single buffer used to read from the selected
 * channels.
 * upon connection registration, the current select() method is halted,
 * the new connection's (subclass specific - local/remote) channel is
 * then registered to the selector and the listening (select()) loop resumes.
 * before that, the specific implemented subclass has registered the
 * appropriate channel (local/remote) with the byte buffer.
 * 
 * TODO: implement channel closing
 *  
 * @author Dorons
 *
 */
public abstract class CommunicationRunnable implements Runnable {

	protected static final String TAG = "CommunicationRunnable";
	
	private static final int BUFFER_SIZE = (8 * 1024);
	
	/*
	 * wait and waitObj are needed to synchronize between
	 * selecting and registering new channels with the selector.
	 */
	private boolean wait;
	private Object waitObj;
	
	private String name;
	private Selector selector;
	protected ByteBuffer byteBuffer;
		
	/*
	 * the constructor allocates a byte buffer that will be used for reading
	 * from the channels and creates a selector that will be used to
	 * select the channels that are ready for read.
	 */
	public CommunicationRunnable(String name) throws AdblockException {
		this.name = name;
		byteBuffer = ByteBuffer.allocate(BUFFER_SIZE);
		try {
			// TODO [usermod] Selector needs to be overridden and JNI impl. on top of PicoTCP
			this.selector = Selector.open();
		} catch (IOException e) {
			throw new AdblockException("cannot open a selector for comm runnable " + name);
		}
		wait = true;
		waitObj = new Object();
	}
	
	/*
	 * this method registers the connection with the communication runnable.
	 * sets the appropriate (local/remote) channel as non-blocking, and
	 * registers it it with the selector. if the selector is currently selecting
	 * (select() is currently blocking) it wakes it up, and resumes selecting
	 * after the channel is registered.
	 * the specific implemented subclass is responsible to set the byte buffer
	 * as the specific (local/remote) buffer. 
	 */
	public void registerConnection(Connection connection) throws AdblockException {
		
		//get the local/remote channel from the subclass
		SelectableChannel channel = getCommReadChannel(connection);
		try {
			channel.configureBlocking(false);
		} catch (IOException e) {
			throw new AdblockException(name + ": cannot set channel mode to blocking");
		}
		
		/*
		 * if selector is selecting, wake it up and wait
		 */
		if (wait == false) {
			wait = true;
			selector.wakeup();
		}
		
		try {
			channel.register(selector, SelectionKey.OP_READ, connection);
			
			Log.v(TAG, "Registered channel to comm runnable " + name);

			/*
			 * sign the thread to continue selecting
			 */
			wait = false;
			synchronized (waitObj) {
				waitObj.notifyAll();
			}
			
		} catch (ClosedChannelException e) {
			Log.e(TAG, "closed channel exception");
			e.printStackTrace();
		}
	}

	/*
	 * this method unregisters the connection with the communication runnable.
	 * uregisters it from with the selector.
	 * TODO: see if the selector must not be selecting 
	 */
	public void unregisterConnection(Connection connection) {
		
		//get the local/remote channel from the subclass
		SelectableChannel channel = getCommReadChannel(connection);

		/*
		 * TODO: check a scenario in which the key is already cancelled
		 */
		SelectionKey key = channel.keyFor(selector);
		if (key != null) {
			key.cancel();
			Log.v(TAG, "unregistered channel from comm runnable " + name);
		} else
			Log.w(TAG, "key got from channel is null, key for channel already been cancelled? (" + name + ")");
	}
	
	@Override
	public void run() {
		
		while (!Thread.currentThread().isInterrupted()) {
			try {
				if (wait) {
					Log.v(TAG, "waiting...");
					synchronized (waitObj) {
						try {
							waitObj.wait();
						} catch (InterruptedException e) {
							//thread is interrupted
							break;
						}
					}
				}
				
				int n = selector.select();
				if (n == 0)
					continue;
				
				Set<SelectionKey> selectedKeys = selector.selectedKeys();
				
				Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

				while (keyIterator.hasNext()) {
				    
				    SelectionKey key = keyIterator.next();

				    if (key.isValid() && key.isAcceptable()) {
				        // a connection was accepted by a ServerSocketChannel.

				    } else if (key.isValid() && key.isConnectable()) {
				        // a connection was established with a remote server.

				    } else if (key.isValid() && key.isReadable()) {
				        // a channel is ready for reading
				    	read(key);
				    } else if (key.isValid() && key.isWritable()) {
				        // a channel is ready for writing
				    }

				    keyIterator.remove();
				}

			} catch (IOException e) {
				Log.e(TAG, "ioexception");
				e.printStackTrace();
			}
			catch (CancelledKeyException e) {
				Log.e(TAG, "Canceled key: " + e.getMessage());
				e.printStackTrace();
			}
		}
	}
	
	/*
	 * abstract methods implemented by the specific subclass
	 */
	protected abstract SelectableChannel getCommReadChannel(Connection connection);
	protected abstract void read(SelectionKey key);
	
}
