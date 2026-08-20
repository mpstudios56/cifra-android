package io.github.mpstudios56.cifra.filter;

import io.github.mpstudios56.cifra.blotter.BlotterFilter;
import io.github.mpstudios56.cifra.datetime.DateUtils;
import io.github.mpstudios56.cifra.datetime.Period;
import io.github.mpstudios56.cifra.datetime.PeriodType;

/**
 * The stretch of time a list is looking at.
 * <p>
 * As a question to the database it is nothing but "the date falls between these
 * two moments"; but it remembers, besides the two moments, which period it came
 * from - this month, last week, or a stretch picked by hand. That is what lets
 * "this month" be worked out again when the month has changed, and what lets
 * the screen say the period by its name instead of as two dates.
 */
public class DateTimeCriterion extends Criterion {

    private final Period period;

    public DateTimeCriterion(Period period) {
        super(BlotterFilter.DATETIME, WhereFilter.Operation.BTW,
                String.valueOf(period.start), String.valueOf(period.end));
        this.period = period;
    }

    /** A named period - this month, next week - worked out as of now. */
    public DateTimeCriterion(PeriodType type) {
        this(DateUtils.getPeriod(type));
    }

    /** Two moments picked by hand. */
    public DateTimeCriterion(long start, long end) {
        this(new Period(PeriodType.CUSTOM, start, end));
    }

    public Period getPeriod() {
        return period;
    }
}
