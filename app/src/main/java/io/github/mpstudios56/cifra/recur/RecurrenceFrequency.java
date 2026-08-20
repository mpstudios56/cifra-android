package io.github.mpstudios56.cifra.recur;

import io.github.mpstudios56.cifra.R;
import io.github.mpstudios56.cifra.utils.LocalizableEnum;

/**
 * How often a movement comes back.
 * <p>
 * The last one is the way out for everything the other four cannot say - the
 * second Tuesday, the last working day of the month - and asks for the rule
 * itself rather than for a word.
 */
public enum RecurrenceFrequency implements LocalizableEnum {

    NO_RECUR(R.string.recur_interval_no_recur),
    DAILY(R.string.recur_interval_daily),
    WEEKLY(R.string.recur_interval_weekly),
    MONTHLY(R.string.recur_interval_monthly),
    GEEKY(R.string.recur_interval_geeky);

    public final int titleId;

    RecurrenceFrequency(int titleId) {
        this.titleId = titleId;
    }

    @Override
    public int getTitleId() {
        return titleId;
    }
}
