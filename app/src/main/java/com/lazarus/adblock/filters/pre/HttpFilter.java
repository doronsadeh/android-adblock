package com.lazarus.adblock.filters.pre;

import android.util.Log;

import com.lazarus.adblock.connections.Connection;
import com.lazarus.adblock.connections.Direction;
import com.lazarus.adblock.connections.Tuple;
import com.lazarus.adblock.exceptions.AdblockException;
import com.lazarus.adblock.filters.Criteria;
import com.lazarus.adblock.filters.Filter;
import com.lazarus.adblock.filters.Opinion;
import com.lazarus.adblock.filters.Opinion.Mode;
import com.lazarus.adblock.lists.CSSRules;
import com.lazarus.adblock.lists.EasyList;
import com.lazarus.adblock.utils.ByteBufferInputStream;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/*
 * TODO: document
 */
public class HttpFilter extends Filter {

    /*
     * Union of fields extracted by this detector, that are provided
     * to the chained filter as formal parameters. This class encapsulates all
     * goodies that are required by any possible chained filter.
     */
    public class HttpData {
        private static final String TAG = "HttpData";

        // TODO currently don't buffer as we don't need the HTML payload
        public boolean buffering = false;

        public String host;
        public String path;
        public String referrer;

        public String encoding;
        public boolean isGziped;

        public ByteBuffer payloadU;
        public ByteBuffer payloadD;

        public HttpData(String host, String path, String referrer, String encoding, boolean isGziped) {
            this.host = host;
            this.path = path;
            this.referrer = referrer;

            this.encoding = encoding;
            this.isGziped = isGziped;

            this.payloadU = null;
            this.payloadD = null;
        }

        public void acc(ByteBuffer buffer, Direction dir) {

            if (!buffering)
                return;

            if (dir == Direction.UPSTREAM) {
                if (null == payloadU) {
                    payloadU = buffer;
                    return;
                }

                ByteBuffer _b = ByteBuffer.allocate(payloadU.limit() + buffer.limit());
                _b.put(payloadU);
                _b.put(buffer);
                payloadU = _b;
            } else if (dir == Direction.DOWNSTREAM) {
                if (null == payloadD) {
                    payloadD = buffer;
                    return;
                }

                ByteBuffer _b = ByteBuffer.allocate(payloadD.limit() + buffer.limit());
                _b.put(payloadD);
                _b.put(buffer);
                payloadD = _b;
            }
        }

        public URL getURL() {
            try {
                return new URL(host + path);
            } catch (MalformedURLException e) {
                Log.e(TAG, e.getMessage());
            }

            return null;
        }

        public String getReferrer() {
            return referrer;
        }
    }

    public static class HttpRequest {

		/*
		 	Request = Request-Line              ; Section 5.1
		 			 *(( general-header  		; Section 4.5
	                   | request-header         ; Section 5.3
	                   | entity-header ) CRLF)  ; Section 7.1
	                   CRLF
	                   [ message-body ]         ; Section 4.3

	        Request-Line   = Method SP Request-URI SP HTTP-Version CRLF
		 */

        public enum Method {
            UNDEFINED("UNDEFINED"),
            OPTIONS("OPTIONS"),
            GET("GET"),
            HEAD("HEAD"),
            POST("POST"),
            PUT("PUT"),
            DELETE("DELETE"),
            TRACE("TRACE"),
            CONNECT("CONNECT");

            private String value;
            private static Set<String> values = new HashSet(Arrays.asList(Method.values()));

            private Method(String v) {
                value = v;
            }

            public String value() {
                return value;
            }

            public static boolean isIn(String s) {
                return values.contains(s);
            }
        }

        private static final String TAG = "HttpRequest";

        public Method method = null;
        public URL requestURL = null;
        public String referrerURL = null;
        public boolean payloadGziped = false;
        public String encoding = null;

        public HttpRequest(Connection connection, ByteBuffer payload, Direction dir) throws AdblockException {

            try {

                //
                // Get Method and URI
                //
                String[] fields = (new String(payload.array(), "UTF-8")).split("\\s+");

                int i;
                for (i = 0; i < fields.length; i++) {
                    try {
                        if ((method = Method.valueOf(fields[i])) != null)
                            break;
                    } catch (IllegalArgumentException e) {
                        //string is not matched with a value
                        continue;
                    }
                }

                if ((method != null) && (i + 1 < fields.length)) {

                    //path is the string after the method
                    String path = fields[i + 1];

                    //continue to look for host url
                    for (int j = i + 2; j < fields.length; j++) {
                        String f = fields[j];
                        if (f.equalsIgnoreCase("host:") && (j + 1) < fields.length) {
                            requestURL = new URL("http://" + fields[j + 1] + path);
                            break;
                        }
                    }
                }

                //throw exception - didn't find url
                if (requestURL == null)
                    throw new AdblockException("cannot find url in payload");

                //
                // Get Headers
                //
                String[] lines = (new String(payload.array(), "UTF-8")).split("\n");

                for (String line : lines) {

                    // Get referrer header
                    if (line.trim().toLowerCase().startsWith("referer")) {
                        String sRefUrl = line.substring(line.indexOf(":") + 1).trim();
                        try {
                            URL u = new URL(sRefUrl);
                            referrerURL = u.getHost();
                        } catch (MalformedURLException e) {
                            referrerURL = sRefUrl;
                        }
                    }

                    if (line.trim().toLowerCase().startsWith("content-type")) {
                        if (line.toLowerCase().contains("charset")) {
                            encoding = line.substring(line.toLowerCase().indexOf("charset")).split("=")[1].trim();
                        }
                    }

                    if (line.trim().toLowerCase().startsWith("accept-encoding"))
                        payloadGziped = line.trim().toLowerCase().contains("gzip") || line.trim().toLowerCase().contains("deflate");
                }

                // We have all the info we need to make a block/pass descision, no point in wasting CPU on parsing the rest
                connection.bypass();

//                 TODO this is currently under self-debate as most sites are HTTPS by now and no injection can be done
//                //
//                // Get <head>, and inject CSS
//                //
//                Charset _encodingCharset = (null == encoding ? Charset.forName("UTF-8") : Charset.forName(encoding));
//
//                String body = null;
//                if (payloadGziped) {
//                    byte[] ba = new byte[payload.limit()];
//                    payload.position(0);
//                    payload.get(ba);
//                    for (int j = 0; j < ba.length - 3; j++) {
//                        Log.d(TAG, "ba[" + j + "] " + ba[j]);
//                        if ((ba[j] == '\r' && ba[j + 1] == '\n') &&
//                                (ba[j + 2] == '\r' && ba[j + 3] == '\n')) {
//                            if ((j + 4) < payload.limit())
//                                payload.position(j + 2);
//                            break;
//                        }
//                    }
//
//                    InputStream stream = new ByteBufferInputStream(payload);
//                    GZIPInputStream gzipStream;
//                    try {
//                        gzipStream = new GZIPInputStream(stream);
//                        Reader decoder = new InputStreamReader(gzipStream, _encodingCharset);
//                        BufferedReader buffered = new BufferedReader(decoder);
//                        body = buffered.readLine();
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                }
//
//                if (null != body && body.contains("<head>")) {
//                    Log.d(TAG, "<head> found in: " + body);
//                    body.replace("<head>", "<head>" + EasyList.getInstance().getCSSStyleStatement());
//                    // TODO inject CSS style from CSSRules right after head: i.e. convert the strings back to ByteBuffer,
//                    //      and remember to modify the HTTP headers (length) acording to the new length, then just let it
//                    //      flow in the river of code, and be sent to client (it's all DOWNSTREAM)
//                }

            } catch (UnsupportedEncodingException e) {
                //cannot convert payload to UTF-8 string
                throw new AdblockException(e.getMessage());
            } catch (MalformedURLException e) {
                //cannot build requestURL object
                throw new AdblockException(e.getMessage());
            }

            Log.d(TAG, "URI: " + requestURL);
        }
    }

    /*
     * Extract  and validate the first few mandatory HTTP fields
     */
    private HttpData parse(ByteBuffer payload, Direction dir) {

        // Create an internal-state copy of the buffer (same backing array)
        ByteBuffer lPayload = payload.duplicate();

        HttpRequest httpRequest;
        try {
            httpRequest = new HttpRequest(connection, lPayload, dir);
        } catch (AdblockException e) {
            return null;
        }
        return new HttpData("http://" + httpRequest.requestURL.getHost(),
                httpRequest.requestURL.getPath(),
                httpRequest.referrerURL,
                httpRequest.encoding,
                httpRequest.payloadGziped);
    }

    @Override
    public List<Opinion> process(Connection connection, List<Opinion> opinions, ByteBuffer data, Tuple tuple, Direction dir, Map<Filter, Filter> detected, Object callerDataObject) {

        this.connection = connection;

        if (!criteria.valid(dir, tuple) || tuple.getDstSocketAddress().getPort() != 80)
            return opinions;

        HttpData httpData = null;
        if (!skipMe(detected)) {
            // Do THIS filter's processing
            httpData = parse(data, dir);

            // If Http detected, add this filter to the list,
            // so it would be skipped next time
            if (httpData != null) {
                // Add us to detected, and build an opinion
                filterData = httpData;
                on = true;
                detected.put(this, this);
                Opinion opinion = new Opinion(Mode.PASS, detected, httpData.host + httpData.path);

                // Add our opinion to the list
                opinions.add(opinion);
            }
        }

        // Do chained filters' processing
        for (Filter c : chained) {
            opinions = c.process(connection, opinions, data, tuple, dir, detected, httpData);
        }

        return opinions;
    }

    public HttpFilter(Type type, Criteria criteria) {
        super(type, criteria, null);
    }
}
