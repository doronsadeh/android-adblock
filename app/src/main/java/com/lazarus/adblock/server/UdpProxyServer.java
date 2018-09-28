package com.lazarus.adblock.server;

import com.dns_proxy.DNSProxy;

import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;

public class UdpProxyServer {

    private static final String TAG = "UdpProxyServer";

    private int serverPort;

    public UdpProxyServer() {

        DNSProxy dnspThread;

        while (true) {
            try {
                // Get a free port
                DatagramSocket localDatagramSocket = new DatagramSocket(0);

                // Register it
                int _serverPort = localDatagramSocket.getLocalPort();

                // Close the socket so we are able to reopen it with reuse
                localDatagramSocket.close();

                // Reopen the socket on the free port w/resuse
                localDatagramSocket = new DatagramSocket(null);
                localDatagramSocket.setReuseAddress(true);
                localDatagramSocket.bind(new InetSocketAddress(_serverPort));

                dnspThread = new DNSProxy(localDatagramSocket, _serverPort);
                dnspThread.start();

                serverPort = _serverPort;

                break;

            } catch (SocketException e) {
                // Silent
            }

            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public int getServerPort() {
        return serverPort;
    }
}
