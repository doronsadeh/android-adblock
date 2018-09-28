package com.lazarus.adblock.iptables;

import android.content.Context;
import android.util.Log;

/*
 * TODO: document
 */
public class IpTables {

    private static final String TAG = "IpTables";

    private static final int MY_UID = android.os.Process.myUid();

    private int tcpServerPort;
    private int udpServerPort;

    public IpTables(int tcpServerPort, int udpServerPort) {
        this.tcpServerPort = tcpServerPort;
        this.udpServerPort = udpServerPort;
    }

    public void set(Context context) {
        setIpTables(context, true);
    }

    public void clear(Context context) {
        setIpTables(context, false);
    }

    private void setIpTables(Context context, boolean apply) {
        // TODO support UDP traffic too, to allow DNS blocking

        String scripttorun;

        if (!apply) {
            /*
             * Delete by grepping on the comment. This way we clean all, even older entries
             */
            scripttorun = "$IPTABLES -t nat -S | grep LAZARUS_ADBLOCKER | sed 's/-A /iptables -t nat -D /' | sh || exit 1\n";
        } else {
            scripttorun = "$IPTABLES -t nat -A OUTPUT -m owner --uid-owner " + MY_UID + " -j RETURN -m comment --comment \"LAZARUS_ADBLOCKER\" || exit 1\n" +
                          "$IPTABLES -t nat -A OUTPUT -p tcp --sport 80 --dport 0:65535 -j REDIRECT --to-ports " + tcpServerPort + " -m comment --comment \"LAZARUS_ADBLOCKER\" || exit 2\n"    +
                          "$IPTABLES -t nat -A OUTPUT -p tcp --sport 0:65535 --dport 80 -j REDIRECT --to-ports " + tcpServerPort + " -m comment --comment \"LAZARUS_ADBLOCKER\" || exit 3\n"    +
                          "$IPTABLES -t nat -A OUTPUT -p tcp --sport 443 --dport 0:65535 -j REDIRECT --to-ports " + tcpServerPort + " -m comment --comment \"LAZARUS_ADBLOCKER\" || exit 4\n"   +
                          "$IPTABLES -t nat -A OUTPUT -p tcp --sport 0:65535 --dport 443 -j REDIRECT --to-ports " + tcpServerPort + " -m comment --comment \"LAZARUS_ADBLOCKER\" || exit 5\n"   ;
                          // + "$IPTABLES -t nat -A OUTPUT ! -d 127.0.0.1 -p udp -m udp --dport 53 -j DNAT --to-destination 127.0.0.1:" +  udpServerPort + " -m comment --comment \"LAZARUS_ADBLOCKER\" || exit 6\n" ;
        }

        Log.d(TAG, "running: " + scripttorun);

        boolean res = Api.runScriptAsRoot(context, scripttorun, true);

        if (!res) {
            Log.e(TAG, "script failed");
            return;
        }

        Log.d(TAG, "script succeeded");
    }
}
