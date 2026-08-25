package io.github.mpstudios56.cifra.graph;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import io.github.mpstudios56.cifra.model.Currency;
import io.github.mpstudios56.cifra.model.TotalError;
import io.github.mpstudios56.cifra.report.IncomeExpense;

/**
 * One row of a report: a name, and the bars drawn beside it.
 * <p>
 * A row is built in two moments. While the movements are being read it only
 * gathers, keeping what came in apart from what went out. Then {@link #flatten}
 * is called, once, and the two halves become the bars that will be drawn - one,
 * two, or none at all when nothing moved.
 * <p>
 * The gathering and the drawing are kept apart because the question the report
 * is answering - income, expense, or the two set against each other - is only
 * settled at the end, and the same gathered figures serve all three.
 */
public class GraphUnit implements Comparable<GraphUnit>, Iterable<Amount> {

    public final long id;
    public final String name;
    public final GraphStyle style;
    public final Currency currency;

    private final IncomeExpenseAmount gathered = new IncomeExpenseAmount();
    private final List<Amount> bars = new ArrayList<>(2);

    /** The longer of this row's bars: what the whole report is scaled against. */
    public long maxAmount;

    /** Set when a rate was missing and the figure could not be trusted. */
    public TotalError error;

    public GraphUnit(long id, String name, Currency currency, GraphStyle style) {
        this.id = id;
        this.name = name != null ? name : "";
        this.style = style;
        this.currency = currency;
    }

    public void addAmount(BigDecimal amount, boolean forceIncome) {
        gathered.add(amount, forceIncome);
    }

    public IncomeExpenseAmount getIncomeExpense() {
        return gathered;
    }

    /**
     * Turns what has been gathered into the bars to draw.
     * <p>
     * Does nothing if it has already been done: a report is laid out more than
     * once - on rotation, on a redraw - and running it twice would add the same
     * money to the row a second time.
     */
    public void flatten(IncomeExpense incomeExpense) {
        if (!bars.isEmpty()) {
            return;
        }
        gathered.filter(incomeExpense);
        addBar(gathered.income.longValue());
        addBar(gathered.expense.longValue());
        Collections.sort(bars);
        maxAmount = gathered.max();
    }

    /** Nothing moved, nothing to draw: a bar of zero length is not a bar. */
    private void addBar(long amount) {
        if (amount != 0) {
            bars.add(new Amount(currency, amount));
        }
    }

    /** The busiest rows first: a report is read from the top. */
    @Override
    public int compareTo(GraphUnit that) {
        return Long.compare(that.maxAmount, this.maxAmount);
    }

    @Override
    public Iterator<Amount> iterator() {
        return bars.iterator();
    }

    public int size() {
        return bars.size();
    }
}
