package com.lazarus.adblock.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;

import com.lazarus.adblock.R;

import static android.content.Context.NOTIFICATION_SERVICE;
import static com.lazarus.adblock.service.AdblockSubsystem.globalDebug;

public class AdBlockServiceForegroundNotification {

    private static Context context;

    public static final int FOREGROUND_NOTIFICATION_ID = (int) java.util.UUID.randomUUID().getLeastSignificantBits();

    public static final int ACTIONS_REQ_CODE = 1000;

    private static String title;
    private static String ticker;
    private static int icon;

    public enum ACTIONS {
        PLAY("play"),
        PAUSE("pause"),
        UPDATE("update");

        private String action;

        ACTIONS(String play) {
            this.action = play;
        }

        public String valueOf() {
            return action;
        }
    }

    public static Notification buildForegroundNotification(Context _context, String _title, String _ticker, int _icon) {

        context = _context;

        title = _title;
        ticker = _ticker;
        icon = _icon;

        NotificationCompat.Builder b = new NotificationCompat.Builder(context);
        b.setOngoing(true);
        b.setContentTitle(title)
                .setContentText(ticker)
                .setSmallIcon(icon)
                .setTicker(ticker);

        Intent playReceive = new Intent();
        playReceive.setAction(ACTIONS.PLAY.valueOf());
        PendingIntent pendingIntentPlay = PendingIntent.getBroadcast(context,
                ACTIONS_REQ_CODE,
                playReceive,
                PendingIntent.FLAG_UPDATE_CURRENT);
        b.addAction(android.R.drawable.ic_media_play, "Block", pendingIntentPlay);


        Intent pauseReceive = new Intent();
        pauseReceive.setAction(ACTIONS.PAUSE.valueOf());
        PendingIntent pendingIntentPause = PendingIntent.getBroadcast(context,
                ACTIONS_REQ_CODE,
                pauseReceive,
                PendingIntent.FLAG_UPDATE_CURRENT);
        b.addAction(android.R.drawable.ic_media_pause, "Pause", pendingIntentPause);

        if (globalDebug) {
            Intent updateReceive = new Intent();
            updateReceive.setAction(ACTIONS.UPDATE.valueOf());
            PendingIntent pendingIntentUpdate = PendingIntent.getBroadcast(context,
                    ACTIONS_REQ_CODE,
                    updateReceive,
                    PendingIntent.FLAG_UPDATE_CURRENT);
            b.addAction(R.drawable.update_action, "Update", pendingIntentUpdate);
        }

        return b.build();
    }

    private static synchronized void updateNotification(Notification notification) {
        final NotificationManager notificationManager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(FOREGROUND_NOTIFICATION_ID, notification);
    }

    public static void modify() {
        updateNotification(buildForegroundNotification(context, title, ticker, icon));
    }

    public static void modifyIcon(int _icon) {
        updateNotification(buildForegroundNotification(context, title, ticker, _icon));
    }

    public static void modifyText(String text) {
        updateNotification(buildForegroundNotification(context, text, ticker, icon));
    }

    public static void modifyTickerText(String text) {
        updateNotification(buildForegroundNotification(context, title, text, icon));
    }

}
