package com.lazarus.adblock.ui;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.view.Window;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.ndk.CrashlyticsNdk;
import com.google.firebase.crash.FirebaseCrash;
import com.lazarus.adblock.Stats;
import com.lazarus.adblock.configuration.Configuration;
import com.lazarus.adblock.lists.EasyList;
import com.lazarus.adblock.service.AdBlockMessageBus;
import com.lazarus.adblock.service.AdblockSubsystem;
import com.lazarus.adblock.R;

import io.fabric.sdk.android.Fabric;
import libcore.tlswire.util.ToastIt;

import static com.lazarus.adblock.service.AdBlockServiceForegroundNotification.modify;
import static com.lazarus.adblock.service.AdblockSubsystem.globalDebug;

public class MainActivity extends Activity {

	private static final String TAG = "adblock";

	private static long debugModeLastClickTimeMili = 0L;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Fabric.with(this, new Crashlytics(), new CrashlyticsNdk());

        requestWindowFeature(Window.FEATURE_NO_TITLE);

		setContentView(R.layout.activity_main);

        findViewById(R.id.InfoText).
            setOnClickListener(view -> {
                // Update the count view
                String currentStats = "\nAds blocked so far: " + Stats.toCountString();
                ((TextView)findViewById(R.id.InfoText)).setText(currentStats);

                // Toggle debug mode
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

		String currentStats = "\nAds blocked so far: " + Stats.toCountString();
        ((TextView)findViewById(R.id.InfoText)).setText(currentStats);

        WebView webView = (WebView) findViewById(R.id.webview);
        webView .getSettings().setJavaScriptEnabled(true);
        webView .getSettings().setLoadWithOverviewMode(true);
        webView .getSettings().setUseWideViewPort(true);
        webView .loadUrl("https://colderlazarus.me");
        // following lines are to show the loader untile downloading
        // file for view.
        webView .setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }

            @Override
            public void onPageFinished(WebView view, final String url) {
            }
        });

        FirebaseCrash.log("Lazarus Activity created");
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
