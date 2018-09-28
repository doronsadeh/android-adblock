package com.lazarus.adblock.server;

import android.util.Log;

import com.lazarus.adblock.connections.ConnectionManager;
import com.lazarus.adblock.exceptions.AdblockException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

/**
 * this proxy server class implements the listener thread
 * of new tcp connections. it loops (blocking) on accepting
 * new connections. once accepted, it calls the createConnection()
 * method of connection manager and continues listening for
 * new connections.
 * 
 * TODO: handle udp connections
 * 
 * @author Dorons
 *
 */

public class TcpProxyServer {
	
	private static final String TAG = "TcpProxyServer";
	
	private int serverPort;
	
	private ConnectionManager connectionManager;
	
	public TcpProxyServer(ConnectionManager connectionManager) throws AdblockException {
		this.connectionManager = connectionManager;
		ServerRunnable serverRunnable = new ServerRunnable();
		serverPort = serverRunnable.getLocalPort();
		Thread serverThread = new Thread(serverRunnable, "tcp-proxy-server-thread");
    	serverThread.start();
	}
    
    private class ServerRunnable implements Runnable
    {
    	private ServerSocketChannel serverSocketChannel;
    	private int localPort;
    	    	
    	public ServerRunnable() throws AdblockException {
			try {
				serverSocketChannel = ServerSocketChannel.open();
				serverSocketChannel.socket().bind(new InetSocketAddress(0));
			} catch (IOException e) {
				throw new AdblockException("error creating/binding server socket channel");
			}
			
			localPort = serverSocketChannel.socket().getLocalPort();
					
			Log.d(TAG, "server socket created and binded to port " + localPort);
		}
    	
    	public int getLocalPort() {
    		return localPort;
    	}
    	
		@Override
		public void run() {
			while (!Thread.currentThread().isInterrupted()) {
				
			    SocketChannel localSocketChannel;
				try {
					localSocketChannel = serverSocketChannel.accept();
				} catch (IOException e) {
					Log.e(TAG, "Error occurred while accepting socket, we'll keep listening");
					e.printStackTrace();
					continue;
				}
			    
				Log.d(TAG, "Accepted socket");
				
				try {
					connectionManager.createConnection(localSocketChannel);
				} catch (AdblockException e) {
					e.printStackTrace();
				}
			}
			
			try {
				serverSocketChannel.close();
			} catch (IOException e) {
				Log.e(TAG, "Error occurred while closing server socket channel");
				e.printStackTrace();
			}
		}
    }
    
    public int getServerPort() {
    	return serverPort;
    }
}
