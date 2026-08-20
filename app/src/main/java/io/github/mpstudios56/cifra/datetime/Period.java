package io.github.mpstudios56.cifra.datetime;

/**
 * A stretch of time, and the name it was chosen by.
 * <p>
 * The two moments are what a query needs; the name is what lets "this month" be
 * worked out again next month instead of staying the fortnight it was when it
 * was first chosen, and lets a screen say the period in words.
 */
public class Period {

    public PeriodType type;
    public long start;
    public long end;

    public Period(PeriodType type, long start, long end) {
        this.type = type;
        this.start = start;
        this.end = end;
    }

    /** Whether it covers exactly those two moments. */
    public boolean isSame(long start, long end) {
        return this.start == start && this.end == end;
    }

    /** Whether it was picked by hand rather than chosen from the list. */
    public boolean isCustom() {
        return type == PeriodType.CUSTOM;
    }
}
