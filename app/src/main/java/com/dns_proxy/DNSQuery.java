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

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static java.lang.Math.random;

public class DNSQuery {

    private static final String TAG = "DNSQuery";
    private Pattern ipAddrPattern = Pattern.compile("[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+");

    private byte[] combine(byte one[], byte two[])
    {
        byte[] bBuffer = new byte[one.length + two.length];
        System.arraycopy(one, 0, bBuffer, 0, one.length);
        System.arraycopy(two, 0, bBuffer, one.length, two.length);
        return (bBuffer);
    }

    /*
     * Generate header information.  Copy the ID from the existing header.
     */
    private byte[] genHeader() {
        //The header is always 12 bytes (96 bits).
        byte[] bHeader = new byte[12];

        //Line 1 - Generate an ID
        bHeader[0] = (byte)(255.0 * random());
        bHeader[1] = (byte)(255.0 * random());

        //Line 2 - Packet options.
        bHeader[2] = (byte)0x01;	//00000001
        bHeader[3] = (byte)0x80;	//1000000r

        //Line 3 - Questions
        bHeader[4] = 0;				//00000000
        bHeader[5] = 1;				//00000001

        //Line 4 - Answers
        bHeader[6] = 0;				//00000000
        bHeader[7] = 0;				//00000001

        //Line 5 - Nameservers
        bHeader[8] = 0;				//00000000
        bHeader[9] = 0;				//00000000

        //Line 6 - Arcount
        bHeader[10] = 0;			//00000000
        bHeader[11] = 0;			//00000000

        //Return the new byte array.
        return bHeader;
    }

    /*
     * Generate the 'query' part of the packet.
     */
    private byte[] genQuestion(String domain) {

        String[] qname = domain.trim().split("\\.");
        int qLen = 0;
        for (String q : qname)
            qLen += q.length();

        int bQPtr = 0;
        byte[] bQuestion = new byte[qname.length + qLen + 1 + 4];
        for (String q : qname) {
            bQuestion[bQPtr] = (byte)q.length(); bQPtr++;
            for (char c : q.toCharArray()) {
                bQuestion[bQPtr] = (byte)c; bQPtr++;
            }
        }

        bQuestion[bQPtr] = (byte)0; bQPtr++;

        bQuestion[bQPtr] = (byte)0; bQPtr++;
        bQuestion[bQPtr] = (byte)1; bQPtr++;

        bQuestion[bQPtr] = (byte)0; bQPtr++;
        bQuestion[bQPtr] = (byte)1;

        return bQuestion;
    }

    private byte[] genQuery(String domain) {
        return combine(genHeader(), genQuestion(domain));
    }

    private List<String> getAddress(byte[] dnsResponse) {

        int rCode = dnsResponse[3] & 0xF;

        if (rCode != 0)
            return null;

        int qdCount = dnsResponse[4] * 256 + dnsResponse[5];
        int anCount = dnsResponse[6] * 256 + dnsResponse[7];

        // Skip Questions
        int qBase = 12;
        int ptr = 0;
        boolean done = false;
        while (!done) {
            for (int q = 0; q < qdCount; q++) {
                int ql = dnsResponse[qBase + ptr];
                if (ql == 0) {
                    ptr++;
                    done = true;
                    ptr += 4;
                    break;
                }
                ptr += ql + 1;
            }
        }

        List<String> answers = new ArrayList<>();

        int ipBase = qBase + ptr;

        for (int a = 0; a < anCount; a++) {

            // Skip non A records
            if (dnsResponse[ipBase + 3] != 1) {
                ipBase += 10;
                int rdLength = dnsResponse[ipBase] * 256 + dnsResponse[ipBase + 1];
                ipBase += 2 + rdLength;
                continue;
            }

            // To start of ip addr bytes
            ipBase += 12;

            String _a = "";
            _a += Integer.toString(dnsResponse[ipBase] & 0xFF);
            for (int k = 1; k < 4; k++) {
                _a += "." + Integer.toString(dnsResponse[ipBase + k] & 0xFF);
            }

            ipBase += 4;

            answers.add(_a);
        }

        return answers;
    }

	public byte[] doLookup(String domain) throws Exception {

	    String[] strSplit;
		byte[] bAddress = new byte[4];

		byte[] query = genQuery(domain);
        DatagramPacket dPacket = new DatagramPacket(query, query.length, InetAddress.getByName("8.8.8.8"), 53);
        DatagramSocket dSocket = new DatagramSocket(0);
        dSocket.send(dPacket);

        byte[] bRequest = new byte[512];
        DatagramPacket dRcvPacket = new DatagramPacket(bRequest, bRequest.length);

        dSocket.receive(dRcvPacket);

        List<String> answers = getAddress(dRcvPacket.getData());

        if (null != answers) {
            for (String answer : answers) {
                try {
                    if (ipAddrPattern.matcher(answer).matches()) {

                        Log.d(TAG, domain + " -> " + answer);

                        strSplit = answer.split("\\.");
                        for (int i = 0; i < 4; i++) {
                            int temp = Integer.parseInt(strSplit[i]);
                            bAddress[i] = (byte) temp;
                        }
                        return bAddress;
                    }
                } catch (Throwable t) {
                    // Silent
                    Log.e(TAG, t.getMessage() + "\n" + t.getCause());
                }
            }
        }

        return null;

//		try {
//            // TODO replace with getBytes
//            InetAddress inetAddrStr = InetAddress.getByName(domain);
//            strSplit = inetAddrStr.getHostAddress().split("\\.");
//            for (int i = 0; i < 4; i++) {
//                int temp = Integer.parseInt(strSplit[i]);
//                bAddress[i] = (byte) temp;
//            }
//            return bAddress;
//        }
//        catch (Throwable t) {
//            // Silent
//            Log.e(TAG, t.getMessage() + "\n" + t.getCause());
//        }

//        ResolverResult<A> r = ResolverApi.INSTANCE.resolve(new Question(domain, Record.TYPE.A));
//
//        if (r.wasSuccessful()) {
//            for (Record<? extends Data> record : r.getRawAnswer().answerSection) {
//                try {
//                    String inetAddrStr  = record.payloadData.toString();
//                    if (ipAddrPattern.matcher(inetAddrStr).matches()) {
//
//                        Log.d(TAG, domain + " -> " + inetAddrStr);
//
//                        strSplit = inetAddrStr.split("\\.");
//                        for (int i = 0; i < 4; i++) {
//                            int temp = Integer.parseInt(strSplit[i]);
//                            bAddress[i] = (byte) temp;
//                        }
//                        return bAddress;
//                    }
//                }
//                catch (Throwable t) {
//                    // Silent
//                    Log.e(TAG, t.getMessage() + "\n" + t.getCause());
//                }
//            }
//        }
//
//        return null;
	}
}
