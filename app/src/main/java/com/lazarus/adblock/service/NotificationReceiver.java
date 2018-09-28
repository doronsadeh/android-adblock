package com.lazarus.adblock.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class NotificationReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (AdBlockServiceForegroundNotification.ACTIONS.PLAY.valueOf().equals(action)) {
            AdblockSubsystem.message = AdblockSubsystem.ServiceActions.SET;
        }
        else if (AdBlockServiceForegroundNotification.ACTIONS.PAUSE.valueOf().equals(action)) {
            AdblockSubsystem.message = AdblockSubsystem.ServiceActions.CLEAR;
        }
        else if (AdBlockServiceForegroundNotification.ACTIONS.UPDATE.valueOf().equals(action)) {
            AdblockSubsystem.message = AdblockSubsystem.ServiceActions.UPDATE;
        }
    }
}