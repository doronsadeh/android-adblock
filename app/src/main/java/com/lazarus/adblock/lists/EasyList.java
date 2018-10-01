package com.lazarus.adblock.lists;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.lazarus.adblock.R;
import com.lazarus.adblock.Stats;
import com.lazarus.adblock.configuration.Configuration;
import com.lazarus.adblock.service.AdBlockServiceForegroundNotification;
import com.lazarus.adblock.service.FiltersUpdater;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;

import libcore.tlswire.util.TimeConversions;

import static java.lang.Math.max;

public class EasyList implements Runnable {

    private static final String TAG = "EasyList";

    private static final String filterRulesFile_EL = "_LZRS_filterrules_EasyList";
    private static final String filterRulesFile_DNS = "_LZRS_filterrules_DNS";

    public static final long FILTERS_UPDATE_PERIOD_MILI = 1 * 24 * 60 * 60 * 1000;

    // TODO This is not in use for now
    // private static final String dnslistHostnames = "https://raw.githubusercontent.com/notracking/hosts-blocklists/master/hostnames.txt";

    private static EasyList instance = null;

    private Context context;

    //
    // --- Rule Sets ---
    //

    private Domains whitelistDomainsPatterns;
    private Domains blacklistDomainsPatterns;
    private PathPatterns purePathPatterns;
    private CSSRules cssHidingRules;

    private boolean ready = false;

    private Object doubleBufferSync = new Object();

    public boolean isReady() {
        return ready;
    }

    private void saveToFile(File file, String content) {
        try {
            FileOutputStream outputStream = new FileOutputStream(file);
            outputStream.write(content.getBytes());
            outputStream.close();
            Log.d(TAG, "Saved file: " + file.getAbsolutePath() + File.separator + file.getName());
        } catch (Exception e) {
            Log.e(TAG, "Could not save file: " + file.getAbsolutePath() + File.separator + file.getName());
            e.printStackTrace();
        }
    }

    private String convertStreamToString(InputStream is) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        reader.close();
        return sb.toString();
    }

    private String getStringFromFile(File file) {
        FileInputStream fin = null;
        try {
            fin = new FileInputStream(file);
            String ret = convertStreamToString(fin);
            fin.close();
            return ret;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return "";
    }

    // TODO read urls of filter lists from external url containing the list
    private static final String dnslistDomains = "https://raw.githubusercontent.com/notracking/hosts-blocklists/master/domains.txt";
    private static final String DNSlistPrefix = "address=/";
    private static final String DNSlistPrefixTermination = "/";

//    private static final String dnslistDomains = "https://pgl.yoyo.org/as/serverlist.php?showintro=0;hostformat=hosts";
//    private static final String DNSlistPrefix = "127.0.0.1 ";
//    private static final String DNSlistPrefixTermination = null;

    private void parseDNSDomainsLists(File file, boolean update, Domains _blacklistDomainsPatterns) throws IOException {

        String content;

        if (update)
            content = URLReader(new URL(dnslistDomains));
        else
            content = getStringFromFile(file);

        String[] lines = content.split("\n");

        for (String line : lines) {
            line = line.trim().toLowerCase();
            if (!line.startsWith(DNSlistPrefix))
                continue;

            String domain = null;
            if (null != DNSlistPrefixTermination)
                domain = line.substring(DNSlistPrefix.length(), line.lastIndexOf(DNSlistPrefixTermination)).trim();
            else
                domain = line.substring(DNSlistPrefix.length()).trim();

            if (null != domain)
                _blacklistDomainsPatterns.add(domain + "/^", null);
        }

        if (update)
            saveToFile(file, content);
    }

    // TODO read urls of filter lists from external url containing the list
    private static final String[] easylistUrls = {"https://easylist.to/easylist/easylist.txt",
            "https://easylist-downloads.adblockplus.org/antiadblockfilters.txt"};

    private void parseEasyList(File file,
                               boolean update,
                               Domains _blacklistDomainsPatterns,
                               Domains _whitelistDomainsPatterns,
                               CSSRules _cssHidingRules,
                               PathPatterns _purePathPatterns) throws IOException {
        String content = "";

        if (update)
            for (String easylistUrl : easylistUrls)
                content += this.URLReader(new URL(easylistUrl)) + "\n";
        else
            content = getStringFromFile(file);

        String[] lines = content.split("\n");

        for (String line : lines) {

            line = line.trim().toLowerCase();

            Options options = null;
            if (line.contains("$")) {
                // Options found
                String[] fields = line.split("\\$");
                if (fields.length > 2) {
                    Log.i(TAG, "Malformed EasyList options, extraneous '$' found in line. Rule would not be parsed: " + line);
                    continue;
                }

                // Parse options for this line
                if (fields.length == 2)
                    options = new Options(fields[1]);

                line = fields[0];
            }

            // TODO currently we disregard all options

            if (line.startsWith("!") || line.startsWith("[")) {
                // Comment, do nothing
            } else if (line.startsWith("||")) {
                // Domain name that may be followed by path pattern
                _blacklistDomainsPatterns.add(line.substring(2), options);
            } else if (line.startsWith("|")) {
                // TODO TBD format, investigate
            } else if (line.startsWith("http")) {
                // Exact address
                try {
                    URL u = new URL(line);
                    _blacklistDomainsPatterns.add(u.getHost() + "^", options);
                } catch (MalformedURLException e) {
                    Log.e(TAG, "Bad URL, cannot parse (" + line + ")");
                    e.printStackTrace();
                }
            } else if (line.startsWith("@@||")) {
                // Exceptions (whitelist)
                _whitelistDomainsPatterns.add(line.substring(4), options);
            } else if (line.startsWith("##")) {
                // Element hiding
                // TODO impl. when we inject CSS
                _cssHidingRules.add(line.substring(2));
            } else {
                // Path patterns w/o domain name
                _purePathPatterns.add(line, options);
            }
        }

        if (update)
            saveToFile(file, content);
    }

    public boolean isBlocked(URL url, String referrer) {
        // If EasyList thread is in mid-update defer blocking
        if (!ready)
            return false;

        // Sync. on the double bufferig switch so we don't get semi-lists in flight
        synchronized (doubleBufferSync) {

            // DEBUG removing for now to test apps blocking
            // If Whitelisted, don't block
            if (this.whitelistDomainsPatterns.apply(url, referrer))
                return false;

            // If NOT whitelisted, AND blacklisted, block
            if (this.blacklistDomainsPatterns.apply(url, referrer) || this.purePathPatterns.apply(url, referrer))
                return true;

        }

        // If not found in any, DON'T block
        return false;
    }

    private String URLReader(URL url) throws IOException {
        URLConnection connection = url.openConnection();
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");
        connection.connect();

        BufferedReader r = new BufferedReader(new InputStreamReader(connection.getInputStream(), Charset.forName("UTF-8")));

        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) {
            sb.append(line.trim() + "\n");
        }
        return sb.toString();
    }

    public long lastUpdate = 0L;

    public void updateFilterLists() throws IOException {

        if (!ready) {
            AdBlockServiceForegroundNotification.modifyTickerText(context.getResources().getString(R.string.adblock_updating_first_time));
        } else {
            AdBlockServiceForegroundNotification.modifyTickerText(context.getResources().getString(R.string.adblock_updating));
        }

        AdBlockServiceForegroundNotification.modifyIcon(R.drawable.updating);

        Domains _blacklistDomains = new Domains();
        Domains _whitelistDomains = new Domains();
        CSSRules _cssRules = new CSSRules();
        PathPatterns _purePathPatterns = new PathPatterns();

        long _lastUpdate = 0L;
        try {
            File fileDNS = new File(context.getFilesDir(), filterRulesFile_DNS);
            if (!fileDNS.exists() || (System.currentTimeMillis() - fileDNS.lastModified() > FILTERS_UPDATE_PERIOD_MILI)) {
                // We need to update the blocking file
                this.parseDNSDomainsLists(fileDNS, true, _blacklistDomains);
                _lastUpdate = System.currentTimeMillis();
            } else {
                this.parseDNSDomainsLists(fileDNS, false, _blacklistDomains);
                _lastUpdate = max(_lastUpdate, fileDNS.lastModified());
            }
        } catch (IOException e) {
            Log.e("EasyList", "Failed reading EasyList. Cannot block");
            e.printStackTrace();
            throw e;
        }

        try {
            File fileEL = new File(context.getFilesDir(), filterRulesFile_EL);
            if (!fileEL.exists() || (System.currentTimeMillis() - fileEL.lastModified() > FILTERS_UPDATE_PERIOD_MILI)) {
                // We need to update the blocking file
                this.parseEasyList(fileEL,
                        true,
                        _blacklistDomains,
                        _whitelistDomains,
                        _cssRules,
                        _purePathPatterns);
                _lastUpdate = System.currentTimeMillis();
            } else {
                this.parseEasyList(fileEL,
                        false,
                        _blacklistDomains,
                        _whitelistDomains,
                        _cssRules,
                        _purePathPatterns);
                _lastUpdate = max(_lastUpdate, fileEL.lastModified());
            }
        } catch (IOException e) {
            Log.e("EasyList", "Failed reading EasyList. Cannot block");
            e.printStackTrace();
            throw e;
        }

        lastUpdate = _lastUpdate;

        // Switch the current lists to the updated ones
        synchronized (doubleBufferSync) {
            // Get some more space to begin with
            System.gc();

            _whitelistDomains.build();
            whitelistDomainsPatterns = _whitelistDomains;
            System.gc();

            _blacklistDomains.build();
            blacklistDomainsPatterns = _blacklistDomains;
            System.gc();

            _purePathPatterns.build();
            purePathPatterns = _purePathPatterns;
            System.gc();

            // Not using CSS as most sites are https sites
            // _cssRules.build();
            // cssHidingRules = _cssRules;
            // System.gc();
        }

        ready = true;

        // Start the filter updater service
        Intent startIntent = new Intent(context, FiltersUpdater.class);
        startIntent.setAction("");
        context.startService(startIntent);

        Log.i(TAG, "EasyList finished loading");

        // Register the EasyList class in the config. to allow updater service to update lists periodically
        Configuration.registerFilterLists(this);

        AdBlockServiceForegroundNotification.modifyIcon(R.drawable.running);
        AdBlockServiceForegroundNotification.modifyTickerText(context.getResources().getString(R.string.adblock_active) +
                (null != Configuration.getFilterLists() ? (" " + TimeConversions.dateFromEpochMili(Configuration.getFilterLists().lastUpdate)) : ""));
    }

    @Override
    public void run() {
        // Update the lists and save if not up to date
        for (int retries = 0; retries < 5; retries++) {
            try {
                updateFilterLists();
            } catch (IOException e) {
                Log.e(TAG, "Error in updating filters: " + e.getMessage());
                e.printStackTrace();
            }

            try {
                Thread.sleep(60);
            } catch (InterruptedException e) {
                // Silent
            }
        }
    }

    private EasyList(Context context) {
        this.context = context;
    }

    public static EasyList getInstance() {
        if (null == instance) {
            instance = new EasyList(Configuration.context);
            (new Thread(instance, "easylist-build-thread")).start();
        }

        return instance;
    }
}
