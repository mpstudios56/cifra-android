package io.github.mpstudios56.cifra.recur;

import android.content.Context;
import android.util.Log;

import com.google.ical.values.RRule;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import io.github.mpstudios56.cifra.R;
import io.github.mpstudios56.cifra.datetime.DateUtils;

/**
 * A movement that comes back: when it starts, how often, and until when.
 * <p>
 * The three answers are kept apart because they are asked apart on the screen,
 * and are written down as one line separated by tildes so a movement can carry
 * its repetition in a single column of the database.
 */
public class Recurrence {

    private static final String TAG = "Recurrence";
    /** What separates the three answers when they are written down. */
    private static final String SEPARATOR = "~";

    /** When the repetition begins. Only its time of day reaches the rule. */
    private Calendar startDate;
    /** How often it comes back. */
    public RecurrencePattern pattern;
    /** When it stops coming back. */
    public RecurrencePeriod period;

    /** A movement that does not come back at all. */
    public static Recurrence noRecur() {
        Recurrence r = new Recurrence();
        r.startDate = Calendar.getInstance();
        r.pattern = RecurrencePattern.noRecur();
        r.period = RecurrencePeriod.noEndDate();
        return r;
    }

    /** Reads back the line written by {@link #stateToString()}. */
    public static Recurrence parse(String written) {
        String[] parts = written.split(SEPARATOR);
        Recurrence r = new Recurrence();
        try {
            Calendar start = Calendar.getInstance();
            start.setTime(DateUtils.FORMAT_TIMESTAMP_ISO_8601.parse(parts[0]));
            r.startDate = start;
        } catch (ParseException unreadable) {
            throw new RuntimeException(written);
        }
        r.pattern = RecurrencePattern.parse(parts[1]);
        r.period = RecurrencePeriod.parse(parts[2]);
        return r;
    }

    public String stateToString() {
        return DateUtils.FORMAT_TIMESTAMP_ISO_8601.format(startDate.getTime())
                + SEPARATOR + pattern.stateToString()
                + SEPARATOR + period.stateToString();
    }

    public Calendar getStartDate() {
        return startDate;
    }

    public void updateStartDate(int year, int month, int day) {
        startDate.set(Calendar.YEAR, year);
        startDate.set(Calendar.MONTH, month);
        startDate.set(Calendar.DAY_OF_MONTH, day);
    }

    public void updateStartTime(int hour, int minute, int second) {
        startDate.set(Calendar.HOUR_OF_DAY, hour);
        startDate.set(Calendar.MINUTE, minute);
        startDate.set(Calendar.SECOND, second);
        startDate.set(Calendar.MILLISECOND, 0);
    }

    /** Every date this repetition falls on between the two given moments. */
    public List<Date> generateDates(Date from, Date to) {
        List<Date> dates = new ArrayList<>();
        DateRecurrenceIterator walk = createIterator(from);
        while (walk.hasNext()) {
            Date date = walk.next();
            if (date.after(to)) {
                break;
            }
            dates.add(date);
        }
        return dates;
    }

    /**
     * A walk over the dates of this repetition, standing at {@code now}.
     * <p>
     * Asking for a moment before the repetition begins is answered from its
     * beginning: nothing falls before the start.
     */
    public DateRecurrenceIterator createIterator(Date now) {
        RRule rule = createRRule();
        try {
            if (now.before(startDate.getTime())) {
                now = startDate.getTime();
            }
            Calendar start = Calendar.getInstance();
            start.setTime(startDate.getTime());
            start.set(Calendar.MILLISECOND, 0);
            return DateRecurrenceIterator.create(rule, now, start.getTime());
        } catch (ParseException unreadable) {
            Log.w(TAG, "Regola di ricorrenza illeggibile: " + rule.toIcal());
            return DateRecurrenceIterator.empty();
        }
    }

    /**
     * The repetition as the calendar library wants it.
     * <p>
     * The last kind carries the rule already written out, so it is handed over
     * as it stands; the others are built from the two halves.
     */
    private RRule createRRule() {
        if (pattern.frequency != RecurrenceFrequency.GEEKY) {
            RRule rule = new RRule();
            pattern.updateRRule(rule);
            period.updateRRule(rule, startDate);
            return rule;
        }
        HashMap<String, String> written = RecurrenceViewFactory.parseState(pattern.params);
        String rule = written.get(RecurrenceViewFactory.P_INTERVAL);
        try {
            return new RRule("RRULE:" + rule.toUpperCase());
        } catch (ParseException unreadable) {
            throw new IllegalArgumentException(pattern.params);
        }
    }

    /** One line saying how often and from when, for the movement's card. */
    public String toInfoString(Context context) {
        return context.getString(pattern.frequency.titleId)
                + ", " + context.getString(R.string.recur_repeat_starts_on) + ": "
                + DateUtils.getShortDateFormat(context).format(startDate.getTime()) + " "
                + DateUtils.getTimeFormat(context).format(startDate.getTime());
    }
}
