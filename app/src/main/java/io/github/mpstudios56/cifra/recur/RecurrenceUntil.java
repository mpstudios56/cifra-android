package io.github.mpstudios56.cifra.recur;

import io.github.mpstudios56.cifra.R;
import io.github.mpstudios56.cifra.utils.LocalizableEnum;

/**
 * When a repetition stops: never, after so many times, or on a given day.
 */
public enum RecurrenceUntil implements LocalizableEnum {

    INDEFINETELY(R.string.recur_indefinitely),
    EXACTLY_TIMES(R.string.recur_exactly_n_times),
    STOPS_ON_DATE(R.string.recur_stops_on_date);

    public final int titleId;

    RecurrenceUntil(int titleId) {
        this.titleId = titleId;
    }

    @Override
    public int getTitleId() {
        return titleId;
    }
}
