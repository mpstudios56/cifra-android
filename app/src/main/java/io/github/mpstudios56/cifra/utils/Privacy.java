package io.github.mpstudios56.cifra.utils;

import io.github.mpstudios56.cifra.model.Currency;

/**
 * Whether the figures are on show.
 * <p>
 * Somebody looking up when they paid the dentist should not have to hand over
 * their balance along with the answer. One tap and every amount in the app
 * turns into dots - lists, totals, summary, reports, the home screen widget -
 * and another tap brings them back. It is the same switch every banking app
 * has, and it is remembered, so an app left hidden opens hidden.
 * <p>
 * The mask is a fixed run of dots, not the real number with the digits
 * replaced: a hidden amount of five dots and one of nine are still a small
 * amount and a large one. The currency symbol stays, because it says nothing
 * about how much and it keeps the rows the shape the eye expects.
 */
public class Privacy {

    private static final String DOTS = "••••";

    /** Read on every amount formatted; kept here so it is not a preference lookup each time. */
    private static Boolean hidden;

    public static boolean isHidden() {
        if (hidden == null) {
            hidden = MyPreferences.isAmountsHidden();
        }
        return hidden;
    }

    public static void set(boolean value) {
        hidden = value;
        MyPreferences.setAmountsHidden(value);
    }

    public static boolean toggle() {
        set(!isHidden());
        return isHidden();
    }

    /** What an amount in this currency looks like with the figures off. */
    public static String mask(Currency c) {
        if (c == null || Utils.isEmpty(c.symbol)) {
            return DOTS;
        }
        StringBuilder sb = new StringBuilder();
        if (c.symbolFormat != null) {
            c.symbolFormat.appendSymbol(sb.append(DOTS), c.symbol);
        } else {
            sb.append(DOTS).append(" ").append(c.symbol);
        }
        return sb.toString();
    }
}
