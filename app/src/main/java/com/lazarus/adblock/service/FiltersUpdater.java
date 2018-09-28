package com.lazarus.adblock.service;

import android.app.IntentService;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import com.lazarus.adblock.R;
import com.lazarus.adblock.configuration.Configuration;
import com.lazarus.adblock.lists.EasyList;

public class FiltersUpdater extends IntentService {

    private static final String TAG = "FiltersUpdater";

    public FiltersUpdater() {
        super("FiltersUpdater");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        try {
            while (true) {

                EasyList filterLists = Configuration.getFilterLists();

                // Protect against races
                if (null == filterLists) {
                    Thread.sleep(1 * 60);
                    continue;
                }

                long timeSinceUpdateMili = System.currentTimeMillis() - filterLists.lastUpdate;
                if (timeSinceUpdateMili > EasyList.FILTERS_UPDATE_PERIOD_MILI) {
                    // Try to update lists every update period
                    filterLists.updateFilterLists();
                }

                Thread.sleep(EasyList.FILTERS_UPDATE_PERIOD_MILI - timeSinceUpdateMili);
            }
        } catch (Throwable e) {
            // Nothing
            Log.e(TAG, "Error in filter updater: " + e.getMessage());
        }
    }
}
