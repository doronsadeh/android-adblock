package com.lazarus.adblock.ui;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.lazarus.adblock.configuration.Configuration;
import com.lazarus.adblock.lists.EasyList;
import com.lazarus.adblock.service.AdBlockMessageBus;
import com.lazarus.adblock.service.AdblockSubsystem;
import com.lazarus.adblock.R;

import libcore.tlswire.util.ToastIt;

import static com.lazarus.adblock.service.AdBlockServiceForegroundNotification.modify;
import static com.lazarus.adblock.service.AdblockSubsystem.globalDebug;

public class MainActivity extends Activity {

	private static final String TAG = "adblock";

	private static long debugModeLastClickTimeMili = 0L;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

        findViewById(R.id.initialImage).
            setOnClickListener(view -> {
                long current = System.currentTimeMillis();
                if (current - debugModeLastClickTimeMili < 250) {
                    globalDebug = !globalDebug;
                    ToastIt.toast("Debug Mode " + (globalDebug ? "On" : "Off"), Toast.LENGTH_SHORT, true);
                    modify();
                }
                debugModeLastClickTimeMili = current;
            });

		if (!checkServiceRunning("com.lazarus.adblock.service.AdblockSubsystem"))
		    init();
	}

    private boolean checkServiceRunning(String serviceName){
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceName.equals(service.service.getClassName()))
                return true;
        }
        return false;
    }

	private void init() {
        Intent startIntent = new Intent(MainActivity.this, AdblockSubsystem.class);
        startIntent.setAction(String.valueOf(AdblockSubsystem.ServiceActions.INIT));
        startService(startIntent);
	}
	
}
