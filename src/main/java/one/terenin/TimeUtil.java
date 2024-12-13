package one.terenin;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class TimeUtil {

    public static boolean checkTimeOverlaps(String startTime, String endTime, String startTimeFromRemote, String endTimeFromRemote) {
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        LocalTime start = LocalTime.parse(startTime, timeFormatter);
        LocalTime end = LocalTime.parse(endTime, timeFormatter);
        LocalTime remoteStart = LocalTime.parse(startTimeFromRemote, timeFormatter);
        LocalTime remoteEnd = LocalTime.parse(endTimeFromRemote, timeFormatter);
        return (start.isBefore(remoteEnd) && end.isAfter(remoteStart));
    }

}
