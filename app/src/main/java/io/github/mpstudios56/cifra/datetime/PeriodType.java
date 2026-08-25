package io.github.mpstudios56.cifra.datetime;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import io.github.mpstudios56.cifra.R;
import io.github.mpstudios56.cifra.utils.LocalizableEnum;
import io.github.mpstudios56.cifra.utils.MyPreferences;

/**
 * The stretches of time the app can be asked about, by name.
 * <p>
 * "This month" is a name, not a pair of dates: it has to mean something
 * different in September from what it meant in August, so what is kept is the
 * name and the two moments are worked out afresh each time from whatever
 * instant is being asked about.
 * <p>
 * Where a week begins and where the fiscal year opens are not the same
 * everywhere, and both are settings: they are read here, at the moment of the
 * question, so changing them changes what every stored filter means without any
 * of them having to be touched.
 * <p>
 * Two of these stretches have an edge that is not a whole day - a fiscal year
 * that opens on the sixth of April ends on the fifth, the instant before the
 * next one opens - so they are marked out by the millisecond before the
 * opening rather than by the end of a day.
 */
public enum PeriodType implements LocalizableEnum {

    TODAY(R.string.period_today, true, false) {
        @Override
        public Period calculatePeriod(long refTime) {
            return wholeDay(at(refTime));
        }
    },
    YESTERDAY(R.string.period_yesterday, true, false) {
        @Override
        public Period calculatePeriod(long refTime) {
            Calendar day = at(refTime);
            day.add(Calendar.DAY_OF_MONTH, -1);
            return wholeDay(day);
        }
    },
    THIS_WEEK(R.string.period_this_week, true, true) {
        @Override
        public Period calculatePeriod(long refTime) {
            return wholeWeek(backToFirstDayOfWeek(at(refTime)));
        }
    },
    THIS_MONTH(R.string.period_this_month, true, true) {
        @Override
        public Period calculatePeriod(long refTime) {
            return wholeMonths(firstOfTheMonth(at(refTime)), 1);
        }
    },
    THIS_YEAR(R.string.period_this_year, true, true) {
        @Override
        public Period calculatePeriod(long refTime) {
            return wholeYear(firstOfTheYear(at(refTime)));
        }
    },
    THIS_FISCAL_YEAR(R.string.period_this_fiscal_year, true, true) {
        @Override
        public Period calculatePeriod(long refTime) {
            Calendar opening = fiscalOpeningInTheSameCalendarYear(refTime);
            if (opensLaterThan(opening, refTime)) {
                // This calendar year's opening has not come round yet, so the
                // year one is standing in opened during the year before.
                long end = dayBegins(opening) - 1;
                opening.add(Calendar.YEAR, -1);
                return new Period(this, dayBegins(opening), end);
            }
            long start = dayBegins(opening);
            opening.add(Calendar.YEAR, 1);
            return new Period(this, start, dayBegins(opening) - 1);
        }
    },
    LAST_WEEK(R.string.period_last_week, true, false) {
        @Override
        public Period calculatePeriod(long refTime) {
            Calendar week = at(refTime);
            week.add(Calendar.DAY_OF_YEAR, -7);
            return wholeWeek(backToFirstDayOfWeek(week));
        }
    },
    LAST_MONTH(R.string.period_last_month, true, false) {
        @Override
        public Period calculatePeriod(long refTime) {
            Calendar month = at(refTime);
            month.add(Calendar.MONTH, -1);
            return wholeMonths(firstOfTheMonth(month), 1);
        }
    },
    LAST_YEAR(R.string.period_last_year, true, false) {
        @Override
        public Period calculatePeriod(long refTime) {
            Calendar year = at(refTime);
            year.add(Calendar.YEAR, -1);
            return wholeYear(firstOfTheYear(year));
        }
    },
    LAST_FISCAL_YEAR(R.string.period_last_fiscal_year, true, false) {
        @Override
        public Period calculatePeriod(long refTime) {
            Calendar opening = fiscalOpeningInTheSameCalendarYear(refTime);
            if (opensLaterThan(opening, refTime)) {
                // The year that has closed opened two calendar years back.
                opening.add(Calendar.YEAR, -1);
            }
            long end = dayBegins(opening) - 1;
            opening.add(Calendar.YEAR, -1);
            return new Period(this, dayBegins(opening), end);
        }
    },
    THIS_AND_LAST_WEEK(R.string.period_this_and_last_week, true, false) {
        @Override
        public Period calculatePeriod(long refTime) {
            return new Period(this,
                    LAST_WEEK.calculatePeriod(refTime).start,
                    THIS_WEEK.calculatePeriod(refTime).end);
        }
    },
    THIS_AND_LAST_MONTH(R.string.period_this_and_last_month, true, false) {
        @Override
        public Period calculatePeriod(long refTime) {
            return new Period(this,
                    LAST_MONTH.calculatePeriod(refTime).start,
                    THIS_MONTH.calculatePeriod(refTime).end);
        }
    },
    THIS_AND_LAST_YEAR(R.string.period_this_and_last_year, true, false) {
        @Override
        public Period calculatePeriod(long refTime) {
            return new Period(this,
                    LAST_YEAR.calculatePeriod(refTime).start,
                    THIS_YEAR.calculatePeriod(refTime).end);
        }
    },
    THIS_AND_LAST_FISCAL_YEAR(R.string.period_this_and_last_fiscal_year, true, false) {
        @Override
        public Period calculatePeriod(long refTime) {
            Calendar opening = fiscalOpeningInTheSameCalendarYear(refTime);
            if (!opensLaterThan(opening, refTime)) {
                // The year one is standing in closes when the next one opens.
                opening.add(Calendar.YEAR, 1);
            }
            long end = dayBegins(opening) - 1;
            opening.add(Calendar.YEAR, -2);
            return new Period(this, dayBegins(opening), end);
        }
    },
    TOMORROW(R.string.period_tomorrow, false, false) {
        @Override
        public Period calculatePeriod(long refTime) {
            Calendar day = at(refTime);
            day.add(Calendar.DAY_OF_MONTH, 1);
            return wholeDay(day);
        }
    },
    NEXT_WEEK(R.string.period_next_week, false, true) {
        @Override
        public Period calculatePeriod(long refTime) {
            // Taken as this week moved on seven days, so it begins on the same
            // weekday this one did whatever that weekday has been set to.
            Period thisWeek = THIS_WEEK.calculatePeriod(refTime);
            Calendar start = at(thisWeek.start);
            start.add(Calendar.DAY_OF_MONTH, 7);
            Calendar end = at(thisWeek.end);
            end.add(Calendar.DAY_OF_MONTH, 7);
            return new Period(this, start.getTimeInMillis(), end.getTimeInMillis());
        }
    },
    NEXT_MONTH(R.string.period_next_month, false, true) {
        @Override
        public Period calculatePeriod(long refTime) {
            Calendar month = at(refTime);
            month.add(Calendar.MONTH, 1);
            return wholeMonths(firstOfTheMonth(month), 1);
        }
    },
    THIS_AND_NEXT_MONTH(R.string.period_this_and_next_month, false, true) {
        @Override
        public Period calculatePeriod(long refTime) {
            return wholeMonths(firstOfTheMonth(at(refTime)), 2);
        }
    },
    NEXT_3_MONTHS(R.string.period_next_3_months, false, true) {
        @Override
        public Period calculatePeriod(long refTime) {
            return wholeMonths(firstOfTheMonth(at(refTime)), 3);
        }
    },
    /** Two dates picked by hand: there is nothing to work out. */
    CUSTOM(R.string.period_custom, true, true) {
        @Override
        public Period calculatePeriod(long refTime) {
            return null;
        }
    };

    public final int titleId;

    /** Offered where one is looking back: the filters on a list of movements. */
    public final boolean inPast;

    /**
     * Offered where one is looking forward: the planner.
     * <p>
     * A single day, one's own or tomorrow's, plans nothing; and a fiscal year
     * that has already closed is not something still to come.
     */
    public final boolean inFuture;

    PeriodType(int titleId, boolean inPast, boolean inFuture) {
        this.titleId = titleId;
        this.inPast = inPast;
        this.inFuture = inFuture;
    }

    @Override
    public int getTitleId() {
        return titleId;
    }

    /** Where this stretch falls, taking the given instant as the present. */
    public abstract Period calculatePeriod(long refTime);

    /** Where it falls now. */
    public Period calculatePeriod() {
        return calculatePeriod(System.currentTimeMillis());
    }

    public static PeriodType[] allRegular() {
        return those(true);
    }

    public static PeriodType[] allPlanner() {
        return those(false);
    }

    private static PeriodType[] those(boolean lookingBack) {
        List<PeriodType> offered = new ArrayList<>();
        for (PeriodType type : values()) {
            if (lookingBack ? type.inPast : type.inFuture) {
                offered.add(type);
            }
        }
        return offered.toArray(new PeriodType[0]);
    }

    // ------------------------------------------------------- working it out
    //
    // The same handful of moves, made over and over by the entries above: put a
    // calendar at an instant, walk it to the edge of a day, a week, a month or
    // a year. They are gathered here so that each entry above reads as the one
    // sentence it is.
    //
    // The calendars are handed round and changed in place, which is how
    // Calendar works; each of these takes one that belongs to the caller and
    // nobody else.

    private static Calendar at(long moment) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(moment);
        return c;
    }

    private static long dayBegins(Calendar c) {
        return DateUtils.startOfDay(c).getTimeInMillis();
    }

    private static long dayEnds(Calendar c) {
        return DateUtils.endOfDay(c).getTimeInMillis();
    }

    Period wholeDay(Calendar day) {
        // Begins first, ends second: both work on the same calendar, and the
        // second undoes the hour the first set.
        return new Period(this, dayBegins(day), dayEnds(day));
    }

    /**
     * Walks back to the day the week began on.
     * <p>
     * Which day that is comes from the settings - Monday here, Sunday
     * elsewhere - so the same Wednesday belongs to a week that started two days
     * ago or four, depending on the answer.
     */
    private static Calendar backToFirstDayOfWeek(Calendar c) {
        int today = c.get(Calendar.DAY_OF_WEEK);
        int weekOpensOn = MyPreferences.getFirstDayOfWeek();
        if (today != weekOpensOn) {
            int daysBack = today > weekOpensOn ? today - weekOpensOn : today - weekOpensOn + 7;
            c.add(Calendar.DAY_OF_MONTH, -daysBack);
        }
        return c;
    }

    Period wholeWeek(Calendar firstDay) {
        long start = dayBegins(firstDay);
        firstDay.add(Calendar.DAY_OF_MONTH, 6);
        return new Period(this, start, dayEnds(firstDay));
    }

    private static Calendar firstOfTheMonth(Calendar c) {
        c.set(Calendar.DAY_OF_MONTH, 1);
        return c;
    }

    /**
     * From the first of a month to the last day of the month that many on.
     * <p>
     * Counted in months rather than in days, so February is as whole as March
     * and a leap year takes care of itself.
     */
    Period wholeMonths(Calendar firstDay, int months) {
        long start = dayBegins(firstDay);
        firstDay.add(Calendar.MONTH, months);
        firstDay.add(Calendar.DAY_OF_MONTH, -1);
        return new Period(this, start, dayEnds(firstDay));
    }

    private static Calendar firstOfTheYear(Calendar c) {
        c.set(Calendar.DAY_OF_YEAR, 1);
        return c;
    }

    Period wholeYear(Calendar firstDay) {
        long start = dayBegins(firstDay);
        firstDay.add(Calendar.YEAR, 1);
        firstDay.add(Calendar.DAY_OF_YEAR, -1);
        return new Period(this, start, dayEnds(firstDay));
    }

    /**
     * The day the fiscal year opens, placed in the calendar year of that
     * instant - which may be an opening still to come.
     * <p>
     * The setting holds the month and the day as one number, the month above
     * the hundreds and the day below: April the sixth is 306, the fourth month
     * counted from zero.
     */
    private static Calendar fiscalOpeningInTheSameCalendarYear(long moment) {
        int opensOn = MyPreferences.getFiscalYearStart();
        Calendar c = at(moment);
        c.set(Calendar.MONTH, opensOn / 100);
        c.set(Calendar.DAY_OF_MONTH, opensOn % 100);
        return c;
    }

    /** Whether that opening is still ahead of the instant in question. */
    private static boolean opensLaterThan(Calendar opening, long moment) {
        return dayBegins(opening) > moment;
    }
}
