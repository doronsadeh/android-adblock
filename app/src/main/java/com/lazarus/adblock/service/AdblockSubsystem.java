package com.lazarus.adblock.service;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

import com.lazarus.adblock.R;
import com.lazarus.adblock.Stats;
import com.lazarus.adblock.configuration.Configuration;
import com.lazarus.adblock.connections.ConnectionManager;
import com.lazarus.adblock.exceptions.AdblockException;
import com.lazarus.adblock.iptables.IpTables;
import com.lazarus.adblock.lists.EasyList;
import com.lazarus.adblock.server.TcpProxyServer;
import com.lazarus.adblock.server.UdpProxyServer;

import java.io.IOException;

import libcore.tlswire.util.TimeConversions;

import static com.lazarus.adblock.service.AdBlockServiceForegroundNotification.FOREGROUND_NOTIFICATION_ID;
import static com.lazarus.adblock.service.AdblockSubsystem.ServiceActions.UNSET;

public class AdblockSubsystem extends IntentService {

    private static final String TAG = "AdblockSubsystem";

    private ConnectionManager connectionManager = null;

    private TcpProxyServer tcpProxyServer = null;
    private UdpProxyServer udpProxyServer = null;

    private IpTables iptables = null;

    public static boolean globalDebug = false;

    public enum ServiceActions {
        INIT,
        START,
        SET,
        CLEAR,
        STOP,
        UPDATE,
        DEBUG,
        UNSET
    }

    public static ServiceActions message = UNSET;

    //
    // Note:
    //          The default Android behaviour is START_STICKY, so we MUST make the default
    //          constructor clean everything up and start the ad block service
    //
    public AdblockSubsystem() {
        super("AdblockSubsystem");
    }

    public void start() throws AdblockException {
        try {
            // Set the context in static global config
            Configuration.getInstance(this);

            // Cause the creation of the parsed EasyList
            EasyList.getInstance();

            connectionManager = ConnectionManager.getInstance();
            tcpProxyServer = new TcpProxyServer(connectionManager);
            udpProxyServer = new UdpProxyServer();
            iptables = new IpTables(tcpProxyServer.getServerPort(), udpProxyServer.getServerPort());
        } catch (AdblockException e) {
            if (connectionManager != null)
                connectionManager.close();
            throw e;
        }
    }

    public void stop() {
        connectionManager.close();
        //TODO: close all open objects
    }

    public void setIptables() throws AdblockException {
        if (iptables == null)
            throw new AdblockException("iptables is not initialized");

        Log.d(TAG, "setting iptables");
        iptables.set(this);
    }

    public void clearIptables() throws AdblockException {
        if (iptables == null)
            throw new AdblockException("iptables is not initialized");

        Log.d(TAG, "clearing iptables");
        iptables.clear(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
//        stop();
//        try {
//            clearIptables();
//        } catch (AdblockException e) {
//            Log.e(TAG, "Could not clear IP Tables, phone must be rebooted to regain network access, or app restarted manually");
//            e.printStackTrace();
//        }

        super.onDestroy();
    }

    @Override
    protected void onHandleIntent(Intent intent) {

        startForeground(FOREGROUND_NOTIFICATION_ID, (AdBlockServiceForegroundNotification.buildForegroundNotification(this,
                                                                                                                      "Lazarus Mobile AdBlock",
                                                                                                                       getResources().getString(R.string.adblock_updating_first_time),
                                                                                                                       R.drawable.updating)));

        if (null != intent) {
            String action = intent.getAction();
            if (null != action && action.equals(String.valueOf(ServiceActions.INIT))) {
                try {
                    // Start for the first time
                    start();
                    clearIptables();
                    setIptables();

                    while (true) {
                        try {
                            if (message == ServiceActions.STOP) {
                                message = ServiceActions.UNSET;
                                stop();
                                try {
                                    clearIptables();
                                } catch (AdblockException e) {
                                    Log.e(TAG, "Could not clear IP Tables, phone must be rebooted to regain network access, or app restarted manually");
                                    e.printStackTrace();
                                }
                                finally {
                                    stopSelf();
                                }
                            }
                            else if (message == ServiceActions.UPDATE) {
                                message = ServiceActions.UNSET;
                                EasyList l = Configuration.getFilterLists();
                                if (null != l && l.isReady()) {
                                    try {
                                        l.updateFilterLists();
                                    } catch (IOException e) {
                                        Log.e(TAG, "Failed to update");
                                        e.printStackTrace();
                                    }
                                }
                            }
                            else if (message == ServiceActions.CLEAR) {
                                message = ServiceActions.UNSET;
                                try {
                                    clearIptables();

                                    AdBlockServiceForegroundNotification.modifyIcon(R.drawable.pause);
                                    AdBlockServiceForegroundNotification.modifyTickerText(getResources().getString(R.string.adblock_paused));

                                } catch (AdblockException e) {
                                    Log.e(TAG, "Could not clear IP Tables, phone must be rebooted to regain network access, or app restarted manually");
                                    e.printStackTrace();
                                }
                            }
                            else if (message == ServiceActions.SET) {
                                message = ServiceActions.UNSET;
                                try {
                                    clearIptables();
                                    setIptables();

                                    EasyList l = Configuration.getFilterLists();
                                    if (null != l && l.isReady()) {
                                        AdBlockServiceForegroundNotification.modifyIcon(R.drawable.running);
                                        AdBlockServiceForegroundNotification.modifyTickerText(getResources().getString(R.string.adblock_active) +
                                                (null != Configuration.getFilterLists() ? (" " + TimeConversions.dateFromEpochMili(Configuration.getFilterLists().lastUpdate)) : ""));
                                    }
                                    else {
                                        AdBlockServiceForegroundNotification.modifyIcon(R.drawable.updating);
                                        AdBlockServiceForegroundNotification.modifyTickerText(getResources().getString(R.string.adblock_updating_first_time));
                                    }

                                } catch (AdblockException e) {
                                    Log.e(TAG, "Could not clear IP Tables, phone must be rebooted to regain network access, or app restarted manually");
                                    e.printStackTrace();
                                }
                            }

                            Thread.sleep(1 * 1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                } catch (AdblockException e) {
                    // TODO make sure this is encapsulated and started by a STICKY service
                    Log.e(TAG, "AdBlock main service died. Service will soon restart.");
                    e.printStackTrace();
                    stopSelf();
                }
            }
        }
    }

}
