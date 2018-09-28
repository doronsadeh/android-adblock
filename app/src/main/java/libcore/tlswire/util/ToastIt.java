package libcore.tlswire.util;

import android.os.Handler;
import android.widget.Toast;

import com.lazarus.adblock.configuration.Configuration;

import static com.lazarus.adblock.service.AdblockSubsystem.globalDebug;

public class ToastIt {

    public static void toast(String text, int length, boolean force) {
        if (force || globalDebug) {
            Handler h = new Handler(Configuration.context.getMainLooper());
            // Although you need to pass an appropriate context
            h.post(() -> Toast.makeText(Configuration.context, text, length).show());
        }
    }
}
