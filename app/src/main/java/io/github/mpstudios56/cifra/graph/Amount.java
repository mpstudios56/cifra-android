package io.github.mpstudios56.cifra.graph;

import io.github.mpstudios56.cifra.model.Currency;
import io.github.mpstudios56.cifra.utils.Utils;

/**
 * One bar on a report: a sum of money, and the room its figure takes up.
 * <p>
 * The two measurements are filled in by whoever draws it. The text has to be
 * measured before the bars can be laid out - the widest figure decides where
 * every bar has to stop - so the answer is kept here rather than worked out
 * again for each row.
 */
public class Amount implements Comparable<Amount> {

    public final Currency currency;
    public final long amount;

    /** Room taken by the figure once drawn, filled in by the widget. */
    public int amountTextWidth;
    public int amountTextHeight;

    public Amount(Currency currency, long amount) {
        this.currency = currency;
        this.amount = amount;
    }

    public String getAmountText() {
        return Utils.amountToString(currency, amount, true);
    }

    /**
     * Largest first, and by size rather than by sign.
     * <p>
     * A report puts its longest bar at the top, and five hundred spent counts
     * as much as five hundred earned: what is being ranked is how much moved,
     * not which way it went.
     */
    @Override
    public int compareTo(Amount that) {
        return Long.compare(Math.abs(that.amount), Math.abs(this.amount));
    }
}
