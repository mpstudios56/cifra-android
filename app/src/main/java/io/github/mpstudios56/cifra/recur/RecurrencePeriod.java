package io.github.mpstudios56.cifra.recur;

import com.google.ical.util.TimeUtils;
import com.google.ical.values.DateTimeValueImpl;
import com.google.ical.values.DateValue;
import com.google.ical.values.DateValueImpl;
import com.google.ical.values.RRule;
import com.google.ical.values.TimeValue;

import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;

import io.github.mpstudios56.cifra.datetime.DateUtils;

/**
 * When a repetition stops.
 * <p>
 * Either never, or after a number of times, or on a chosen day. Like the
 * pattern beside it, it keeps the answers from its panel as one line of text
 * and knows how to write them into the rule.
 * <p>
 * It also holds the two translations between the calendar library's kind of
 * date and an ordinary one, because this is where they were first needed.
 */
public class RecurrencePeriod {

    public final RecurrenceUntil until;
    /** The answers from the panel, as one line. */
    public final String params;

    public RecurrencePeriod(RecurrenceUntil until, String params) {
        this.until = until;
        this.params = params;
    }

    public static RecurrencePeriod noEndDate() {
        return new RecurrencePeriod(RecurrenceUntil.INDEFINETELY, null);
    }

    public static RecurrencePeriod empty(RecurrenceUntil until) {
        return new RecurrencePeriod(until, null);
    }

    public static RecurrencePeriod parse(String written) {
        String[] parts = written.split(":");
        return new RecurrencePeriod(RecurrenceUntil.valueOf(parts[0]), parts[1]);
    }

    public String stateToString() {
        return until.name() + ":" + params;
    }

    /** Writes the end of the repetition into the rule, where there is one. */
    public void updateRRule(RRule rule, Calendar startDate) {
        HashMap<String, String> answers = RecurrenceViewFactory.parseState(params);
        switch (until) {
            case EXACTLY_TIMES:
                rule.setCount(Integer.parseInt(answers.get(RecurrenceViewFactory.P_COUNT)));
                break;
            case STOPS_ON_DATE:
                rule.setUntil(dateToDateValue(
                        lastDay(answers.get(RecurrenceViewFactory.P_DATE), startDate)));
                break;
            default:
                // Never stops: the rule says nothing about an end.
                break;
        }
    }

    /**
     * The chosen last day, at the same time of day the repetition starts at.
     * <p>
     * The day is picked on a calendar, which knows nothing of the hour; taking
     * the hour from the start is what makes the last occurrence fall inside the
     * period instead of just outside it.
     */
    private Date lastDay(String chosen, Calendar startDate) {
        Calendar end = Calendar.getInstance();
        try {
            end.setTime(DateUtils.FORMAT_DATE_RFC_2445.parse(chosen));
        } catch (ParseException unreadable) {
            throw new IllegalArgumentException(params);
        }
        end.set(Calendar.HOUR_OF_DAY, startDate.get(Calendar.HOUR_OF_DAY));
        end.set(Calendar.MINUTE, startDate.get(Calendar.MINUTE));
        end.set(Calendar.SECOND, startDate.get(Calendar.SECOND));
        end.set(Calendar.MILLISECOND, 0);
        return end.getTime();
    }

    /**
     * The library's date, brought back to this phone's clock.
     *
     * @param startedInDaylight kept for the caller's sake; the shift is already
     *                          worked out by the conversion below.
     */
    static Date dateValueToDate(DateValue utc, boolean startedInDaylight) {
        GregorianCalendar here = new GregorianCalendar();
        DateValue local = TimeUtils.fromUtc(utc, here.getTimeZone());
        // Months are counted from one there and from zero here.
        int month = local.month() - 1;
        if (local instanceof TimeValue) {
            TimeValue time = (TimeValue) local;
            here.set(local.year(), month, local.day(),
                    time.hour(), time.minute(), time.second());
        } else {
            here.set(local.year(), month, local.day(), 0, 0, 0);
        }
        return here.getTime();
    }

    /**
     * An ordinary date as the library wants it: a bare day when the time is
     * midnight, a day and a time otherwise.
     */
    static DateValue dateToDateValue(Date date) {
        GregorianCalendar here = new GregorianCalendar();
        here.setTime(date);
        int year = here.get(Calendar.YEAR);
        int month = here.get(Calendar.MONTH) + 1;
        int day = here.get(Calendar.DAY_OF_MONTH);
        int hour = here.get(Calendar.HOUR_OF_DAY);
        int minute = here.get(Calendar.MINUTE);
        int second = here.get(Calendar.SECOND);
        if (hour == 0 && minute == 0 && second == 0) {
            return new DateValueImpl(year, month, day);
        }
        return new DateTimeValueImpl(year, month, day, hour, minute, second);
    }
}
