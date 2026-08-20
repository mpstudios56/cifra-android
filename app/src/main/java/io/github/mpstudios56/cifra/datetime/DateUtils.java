package io.github.mpstudios56.cifra.datetime;

import android.content.Context;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * Dates and times, in the two languages the app has to speak.
 * <p>
 * One is for files - backups, calendar rules - where a date has to look the
 * same on every phone in the world, and is fixed here. The other is for the
 * screen, where a date has to look the way this phone's owner expects, and is
 * asked of the phone rather than decided.
 */
public class DateUtils {

    /** For files: the same on every phone, whatever its settings say. */
    public static final DateFormat FORMAT_TIMESTAMP_ISO_8601 =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    public static final DateFormat FORMAT_DATE_ISO_8601 =
            new SimpleDateFormat("yyyy-MM-dd");
    public static final DateFormat FORMAT_TIME_ISO_8601 =
            new SimpleDateFormat("HH:mm:ss");
    /** As the calendar standard writes a moment, for repetition rules. */
    public static final DateFormat FORMAT_DATE_RFC_2445 =
            new SimpleDateFormat("yyyyMMdd'T'HHmmss");

    private DateUtils() {
    }

    /** Where a named period - this month, next week - falls as of now. */
    public static Period getPeriod(PeriodType period) {
        return period.calculatePeriod();
    }

    /** The first instant of that day. */
    public static Calendar startOfDay(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c;
    }

    /** The last instant of that day, a thousandth before midnight. */
    public static Calendar endOfDay(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY, 23);
        c.set(Calendar.MINUTE, 59);
        c.set(Calendar.SECOND, 59);
        c.set(Calendar.MILLISECOND, 999);
        return c;
    }

    public static long atMidnight(long moment) {
        return startOfDay(calendarAt(moment)).getTimeInMillis();
    }

    public static long atDayEnd(long moment) {
        return endOfDay(calendarAt(moment)).getTimeInMillis();
    }

    /**
     * That day, at this other moment's time of day.
     * <p>
     * A repetition keeps its hour: moving it to today means taking today's date
     * and the hour it has always fallen at.
     */
    public static Date atDateAtTime(long day, Calendar timeOfDay) {
        Calendar when = calendarAt(day);
        when.set(Calendar.HOUR_OF_DAY, timeOfDay.get(Calendar.HOUR_OF_DAY));
        when.set(Calendar.MINUTE, timeOfDay.get(Calendar.MINUTE));
        when.set(Calendar.SECOND, timeOfDay.get(Calendar.SECOND));
        when.set(Calendar.MILLISECOND, timeOfDay.get(Calendar.MILLISECOND));
        return when.getTime();
    }

    /** Rounds down to the minute, where seconds would only be noise. */
    public static void zeroSeconds(Calendar when) {
        when.set(Calendar.SECOND, 0);
        when.set(Calendar.MILLISECOND, 0);
    }

    private static Calendar calendarAt(long moment) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(moment);
        return c;
    }

    // ---- For the screen: whatever this phone's owner has chosen to see.

    public static DateFormat getShortDateFormat(Context context) {
        return android.text.format.DateFormat.getDateFormat(context);
    }

    public static DateFormat getLongDateFormat(Context context) {
        return android.text.format.DateFormat.getLongDateFormat(context);
    }

    public static DateFormat getMediumDateFormat(Context context) {
        return android.text.format.DateFormat.getMediumDateFormat(context);
    }

    public static DateFormat getTimeFormat(Context context) {
        return android.text.format.DateFormat.getTimeFormat(context);
    }

    public static boolean is24HourFormat(Context context) {
        return android.text.format.DateFormat.is24HourFormat(context);
    }
}
