package io.github.mpstudios56.cifra.recur;

import com.google.ical.iter.RecurrenceIterator;
import com.google.ical.iter.RecurrenceIteratorFactory;
import com.google.ical.values.RRule;

import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;

import static io.github.mpstudios56.cifra.recur.RecurrencePeriod.dateToDateValue;
import static io.github.mpstudios56.cifra.recur.RecurrencePeriod.dateValueToDate;

/**
 * Walks through the dates a rule produces, one after the next.
 * <p>
 * The calendar library underneath speaks in its own kind of date and always
 * starts from the beginning of the rule; this walks it forward to the moment
 * asked for and hands back ordinary dates from there on.
 */
public class DateRecurrenceIterator {

    private final RecurrenceIterator dates;
    /** The one already taken out of the walk while looking for the moment. */
    private Date held;
    /** Whether the rule started in summer time, which shifts the hour. */
    private boolean startedInDaylight;

    private DateRecurrenceIterator(RecurrenceIterator dates) {
        this.dates = dates;
    }

    public boolean hasNext() {
        return held != null || dates.hasNext();
    }

    public Date next() {
        if (held != null) {
            Date date = held;
            held = null;
            return date;
        }
        return dateValueToDate(dates.next(), startedInDaylight);
    }

    /**
     * A walk over the rule, standing at the first date that is not before
     * {@code from}.
     * <p>
     * When the rule has already run out before that moment, the last date it
     * produced is what stands ready - which is how the scheduler learns that
     * there is nothing further ahead.
     */
    public static DateRecurrenceIterator create(RRule rule, Date from, Date start)
            throws ParseException {
        Calendar here = Calendar.getInstance();
        boolean startedInDaylight = here.getTimeZone().inDaylightTime(start);
        RecurrenceIterator dates = RecurrenceIteratorFactory.createRecurrenceIterator(
                rule, dateToDateValue(start), here.getTimeZone());
        Date date = null;
        while (dates.hasNext()) {
            date = dateValueToDate(dates.next(), startedInDaylight);
            if (!date.before(from)) {
                break;
            }
        }
        DateRecurrenceIterator walk = new DateRecurrenceIterator(dates);
        walk.startedInDaylight = startedInDaylight;
        walk.held = date;
        return walk;
    }

    /** A walk with nothing in it, for a movement that does not come back. */
    public static DateRecurrenceIterator empty() {
        return new Empty();
    }

    private static class Empty extends DateRecurrenceIterator {
        Empty() {
            super(null);
        }

        @Override
        public boolean hasNext() {
            return false;
        }

        @Override
        public Date next() {
            return null;
        }
    }
}
