package io.github.mpstudios56.cifra.blotter;

import android.content.Context;
import android.widget.TextView;

import io.github.mpstudios56.cifra.db.DatabaseAdapter;
import io.github.mpstudios56.cifra.db.TransactionsTotalCalculator;
import io.github.mpstudios56.cifra.filter.WhereFilter;
import io.github.mpstudios56.cifra.model.Total;

/**
 * The figure at the head of the list of movements.
 * <p>
 * Whatever the filter has left on screen, added up. The list can hold movements
 * from several accounts and so from several currencies: each currency is summed
 * on its own, and the sums are brought together at the latest rates by the
 * class above.
 */
public class BlotterTotalCalculationTask extends TotalCalculationTask {

    private final WhereFilter filter;

    public BlotterTotalCalculationTask(Context context, DatabaseAdapter db, WhereFilter filter,
                                       TextView totalText) {
        super(context, db, totalText);
        this.filter = filter;
    }

    @Override
    public Total[] getTotals() {
        return new TransactionsTotalCalculator(db, filter).getTransactionsBalance();
    }
}
