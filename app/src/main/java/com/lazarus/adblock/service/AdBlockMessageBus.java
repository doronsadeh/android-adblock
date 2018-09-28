package com.lazarus.adblock.service;

import android.app.IntentService;
import android.content.Intent;

import static com.lazarus.adblock.service.AdblockSubsystem.ServiceActions.DEBUG;
import static com.lazarus.adblock.service.AdblockSubsystem.ServiceActions.SET;
import static com.lazarus.adblock.service.AdblockSubsystem.ServiceActions.STOP;
import static com.lazarus.adblock.service.AdblockSubsystem.ServiceActions.CLEAR;
import static com.lazarus.adblock.service.AdblockSubsystem.ServiceActions.UPDATE;

public class AdBlockMessageBus extends IntentService {

    public AdBlockMessageBus() {
        super("AdBlockMessageBus");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (null != intent) {
            String action = intent.getAction();
            if (String.valueOf(STOP).equals(action)) {
                AdblockSubsystem.message = STOP;
            }
            else if (String.valueOf(SET).equals(action)) {
                AdblockSubsystem.message = SET;
            }
            else if (String.valueOf(CLEAR).equals(action)) {
                AdblockSubsystem.message = CLEAR;
            }
            else if (String.valueOf(UPDATE).equals(action)) {
                AdblockSubsystem.message = UPDATE;
            }
            else if (String.valueOf(DEBUG).equals(action)) {
                AdblockSubsystem.message = DEBUG;
            }

        }
    }
}
