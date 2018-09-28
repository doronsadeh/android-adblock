package libcore.tlswire.util;

import java.text.SimpleDateFormat;
import java.util.Date;

public class TimeConversions {
    public static String dateFromEpochMili(long epochMili) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM HH:mm");
        return sdf.format(new Date(epochMili));
    }
}
