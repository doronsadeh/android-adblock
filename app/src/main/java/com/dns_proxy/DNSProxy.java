package com.dns_proxy;

/*
 * dnsproxyd
 * Version 1.0
 * Copyright � 2008 Michael Landi
 *
 * This file is part of dnsproxyd.
 *
 * Dnsproxyd is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Dnsproxyd is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Dnsproxyd.  If not, see <http://www.gnu.org/licenses/>
 */

import android.util.Log;
import android.widget.Toast;

import com.lazarus.adblock.configuration.Configuration;
import com.lazarus.adblock.lists.EasyList;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;

import libcore.tlswire.util.ToastIt;

public class DNSProxy extends Thread {
    /*
     * Constants.
     */
    private static final String TAG = "DNSProxy";

    /*
     * Settings either default or loaded from configuration.
     */
    private int _intPort;

    /*
     * Instance variables.
     */
    private boolean _varListen;
    private EventListener _elSystem;

    private  DatagramSocket dSocket;

    /*
     * Constructor.
     */
    public DNSProxy(DatagramSocket dSocket, int port) {
        _intPort = port;
        this.dSocket = dSocket;
        printDebug("Listening for requests on port " + _intPort + ":");
    }

    public void close() {
        _varListen = false;
        _elSystem.close();

        try {
            Thread.currentThread().sleep(500);
        } catch (Exception e) {
            printDebug(e);
        }

        System.exit(0);
    }

    public void run() {
        _elSystem = new EventListener(this, 4445);
        _elSystem.start();

        try {
            _varListen = true;
            listen();
            _varListen = false;
        } catch (Exception e) {
            _varListen = false;
            printDebug(e);
        }
    }

    public void listen() throws Exception {
        byte[] bRequest;
        DatagramPacket dPacket;

        while (_varListen) {

            bRequest = new byte[512];
            dPacket = new DatagramPacket(bRequest, bRequest.length);
            dSocket.receive(dPacket);

            (new ClientThread(dSocket, dPacket.getAddress(), dPacket.getPort(), bRequest)).start();
            // Serial: (new ClientThread(dSocket, dPacket.getAddress(), dPacket.getPort(), bRequest)).run();
        }

        printDebug("UDP server socket closed, UDP server down");

        dSocket.close();
    }

    public class ClientThread extends Thread {
        private int _intPort;
        private byte[] _bRequest;
        private InetAddress _inaSender;
        private DatagramSocket _dsPointer;

        public ClientThread(DatagramSocket socket,
                            InetAddress address,
                            int port,
                            byte[] request) {
            _intPort = port;
            _bRequest = request;
            _inaSender = address;
            _dsPointer = socket;

            setName("client-thread-" + _intPort);
        }

        public void run() {
            byte[] bResponse;
            GetAddResults results;

            printDebug("UDP client: " + _inaSender + ":" + _intPort);

            DNSResponse dnsResponse = new DNSResponse(_bRequest);
            String strDomain = dnsResponse.getDomain();

            results = getAddress(strDomain);

             if (null != results)
                printDebug("UDP client [resolved]: " + _inaSender + ":" + _intPort + " [" + (results.blocked ? "blocked" : "pass") + "] ");

            if (null != results) {
                byte[] bAddrBuffer = results.buffer;

                DatagramPacket dPacket;
                if (results.blocked) {
                    // Generate a DNS response with domain not found (NXDOMAIN = 3)
                    bResponse = dnsResponse.getResponse(bAddrBuffer, (byte) 3);

                    ToastIt.toast("Block DNS/" + strDomain, Toast.LENGTH_SHORT, false);
                    Log.d(TAG, "DNS blocking " + strDomain);
                } else {
                    // Simply return as responded (NOERROR = 0)
                    bResponse = dnsResponse.getResponse(bAddrBuffer, (byte) 0);
                }

                dPacket = new DatagramPacket(bResponse, bResponse.length, _inaSender, _intPort);

                printDebug("UDP client [created response]: " + _inaSender + ":" + _intPort);

                try {
                    _dsPointer.send(dPacket);

                    printDebug("UDP client [response sent]: " + _inaSender + ":" + _intPort);

                } catch (Exception e) {
                    printDebug(e);
                }
            }
        }

        private class GetAddResults {
            boolean blocked;
            byte[] buffer;

            public GetAddResults(boolean blocked, byte[] buffer) {
                this.blocked = blocked;
                this.buffer = buffer;
            }
        }

        /*
         * FIXME: Performance
         * Optimize this code!  It's slowing down responses.
         */
        private GetAddResults getAddress(String domain) {
            try {
                byte[] bBuffer = (new DNSQuery()).doLookup(domain);
                if (null != bBuffer)
                    return new GetAddResults(isBlocked(domain), bBuffer);
                else
                    return null;
            } catch (Exception e) {
                printDebug(e);
                return null;
            }
        }

        private boolean isBlocked(String domain) {

            EasyList filters = Configuration.getFilterLists();

            if (null != filters) {
                try {
                    return filters.isBlocked(new URL("http://" + domain), null);
                } catch (MalformedURLException e) {
                    Log.e(TAG, "Malformed URL from DNS domain name: " + domain);
                    e.printStackTrace();
                }
            }

            return false;
        }
    }

    public static void printDebug(String e) {
        Log.d(TAG, e);
    }

    public static void printDebug(Exception e) {
        Log.d(TAG, e.getMessage());
    }

}
