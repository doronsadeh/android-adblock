package com.lazarus.adblock.filters.pre;

import android.util.Log;

import com.lazarus.adblock.connections.Connection;
import com.lazarus.adblock.connections.Direction;
import com.lazarus.adblock.connections.Tuple;
import com.lazarus.adblock.filters.Criteria;
import com.lazarus.adblock.filters.Filter;
import com.lazarus.adblock.filters.Opinion;
import com.lazarus.adblock.filters.Opinion.Mode;
import com.lazarus.adblock.utils.ByteBufferInputStream;

import java.io.ByteArrayInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import libcore.tlswire.handshake.ClientHello;
import libcore.tlswire.handshake.HandshakeMessage;
import libcore.tlswire.handshake.HelloExtension;
import libcore.tlswire.handshake.ServerNameHelloExtension;

import static libcore.tlswire.handshake.HelloExtension.TYPE_SERVER_NAME;

public class SSLFilter extends Filter {

    private static final String TAG = "adblock_sslfilter";

    private enum Phase {
        UNDEFINED(-1),
        CLIENT_HELLO(1),
        SERVER_HELLO(2);

        private int value;

        private Phase(int value) {
            this.value = value;
        }

        public int value() {
            return value;
        }
    }

    private ClientHello tlsClientHelloParser = new ClientHello();

    public class SSLData {
        protected Phase phase;
        protected Tuple tuple;
        protected ByteBuffer payloadU = null;
        protected ByteBuffer payloadD = null;
        protected String serverName;

        public SSLData(Phase phase, Tuple tuple) {
            this.phase = phase;
            this.tuple = tuple;
        }

        public boolean is(Phase p) {
            return this.phase.equals(p);
        }

        public void set(Phase p) {
            this.phase = p;
        }

        public void set(String serverName) {
            this.serverName = serverName;
        }

        public void acc(ByteBuffer payload, Direction dir) {
            if (dir == Direction.UPSTREAM) {
                this.payloadU = payload;
                this.payloadU.position(0);
            } else if (dir == Direction.DOWNSTREAM) {
                this.payloadD = payload;
                this.payloadD.position(0);
            }

        }

        public boolean hasPayload(Direction dir) {
            if (dir == Direction.UPSTREAM && null != payloadU)
                return true;
            else if (dir == Direction.DOWNSTREAM && null != payloadD)
                return true;

            return false;
        }

        public URL getURL() {
            if (tuple == null)
                return null;

            try {
                String hostname = "https://" + serverName;
                return new URL(hostname);
            } catch (MalformedURLException e) {
                Log.e(TAG, "Malformed URL building from SNI over TLS");
            }

            return null;
        }

        public String getReferrer() {
            // There is NO referrer header is SSL
            return null;
        }
    }

    private SSLData state;

    public SSLFilter(Type type, Criteria criteria) {
        super(type, criteria, null);
        state = new SSLData(Phase.UNDEFINED, null);

        // Known SSL versions
        sslVersions.add((byte) 1);
        sslVersions.add((byte) 2);
        sslVersions.add((byte) 3);
    }

    private static final int clientHelloCodeByteOffset = 2;
    private static final int clientHelloSSLVerByteOffset = 3;
    private static final int serverHelloCodeByteOffset = 5;
    private static final int serverHelloContentTypeByteOffset = 0;
    private static final int serverHelloSSLVerByteOffset = 1;

    private static final int serverHelloHandshakeContentType = 0x22;

    private Set<Byte> sslVersions = new HashSet<>();

    @Override
    public List<Opinion> process(Connection connection,
                                 List<Opinion> opinions, ByteBuffer data,
                                 Tuple tuple, Direction dir, Map<Filter, Filter> detected,
                                 Object callerDataObject) {

        this.connection = connection;

        if (!criteria.valid(dir, tuple) || tuple.getDstSocketAddress().getPort() != 443)
            return opinions;

        state.tuple = tuple;

        ByteBuffer payload = data.duplicate();

        if (!skipMe(detected)) {

            // TODO handle not only SSL 2,3 but TLS x.x

            if (state.hasPayload(dir)) {
                if (dir == Direction.UPSTREAM) {
                    ByteBuffer _b = ByteBuffer.allocate(state.payloadU.limit() + payload.limit());
                    _b.put(state.payloadU);
                    _b.put(payload);
                    payload = _b;
                    payload.position(0);

                    try {
                        if (payload.get(5) == 1)
                            Log.d(TAG, tuple.getSrcSocketAddress().getPort() + ": " + (new String(payload.array(), "UTF-8")));
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                } else if (dir == Direction.DOWNSTREAM) {
                    ByteBuffer _b = ByteBuffer.allocate(state.payloadD.limit() + payload.limit());
                    _b.put(state.payloadD);
                    _b.put(payload);
                    payload = _b;
                    payload.position(0);
                }
            }

            try {
                // TLS (22 = SSL3_RT_HANDSHAKE, i.e. TLS handshake record type,
                // the offset to the ClientHello is 5 bytes)
                if (payload.get(0) == 22){
                    if (payload.limit() > 5 && payload.get(5) == 1) {
                        // ClientHello
                        payload.position(5);
                        ByteBufferInputStream dataInBB = new ByteBufferInputStream(payload);
                        DataInput dataIn = new DataInputStream(dataInBB);
                        HandshakeMessage msg = HandshakeMessage.read(dataIn);
                        if (msg instanceof ClientHello) {
                            ServerNameHelloExtension sni = (ServerNameHelloExtension) ((ClientHello) msg).findExtensionByType(TYPE_SERVER_NAME);
                            if (null != sni) {
                                // Get SNI ext.
                                state.set(sni.hostnames.get(0));

                                // Report
                                filterData = state;
                                on = true;
                                detected.put(this, this);
                                Opinion opinion = new Opinion(Mode.PASS, detected, state.serverName);
                                opinions.add(opinion);

                                // We got what we came for, i.e. the client hello, from now on bypass so no CPU is wasted on parsing SSL
                                // even if we did or did not get the SNI (we had one chance, and it is done)
                                // connection.countTLSClientHellos();
                                connection.bypass();
                            }
                        } else {
                            // Report
                            filterData = state;
                            on = false;
                            detected.put(this, this);
                            Opinion opinion = new Opinion(Mode.PASS, detected, "<none>");
                            opinions.add(opinion);
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (BufferUnderflowException e) {
                // Incomplete handshake, we'll accumulate payload for next time
                state.acc(payload, dir);

                // Report
                filterData = state;
                on = false;
                detected.put(this, this);
                Opinion opinion = new Opinion(Mode.PASS, detected, "<none>");
                opinions.add(opinion);
            }
        }

        // Do chained filters' processing
        for (Filter c : chained) {
            opinions = c.process(connection, opinions, data, tuple, dir, detected, state);
        }

        return opinions;
    }

}
