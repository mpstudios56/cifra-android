package io.github.mpstudios56.cifra.blotter;

import static io.github.mpstudios56.cifra.db.DatabaseAdapter.filterOnlyShowSplitSummaryInSameAccount;

import android.content.Context;
import android.widget.TextView;

import io.github.mpstudios56.cifra.db.DatabaseAdapter;
import io.github.mpstudios56.cifra.db.TransactionsTotalCalculator;
import io.github.mpstudios56.cifra.filter.WhereFilter;
import io.github.mpstudios56.cifra.model.Total;

/**
 * The figure at the head of one account.
 * <p>
 * An account keeps a single currency, so its total needs no conversion and is
 * asked for directly rather than being built out of several sums.
 * <p>
 * A movement split into parts is counted once: the filter is narrowed so that
 * the summary line stands for its pieces inside the account it belongs to, and
 * the pieces themselves are left out. Without that, a split of fifty euros into
 * two halves would be counted twice over.
 */
public class AccountTotalCalculationTask extends TotalCalculationTask {

    private final WhereFilter filter;

    public AccountTotalCalculationTask(Context context, DatabaseAdapter db, WhereFilter filter,
                                       TextView totalText) {
        super(context, db, totalText);
        this.filter = filterOnlyShowSplitSummaryInSameAccount(filter);
    }

    @Override
    public Total getTotalInHomeCurrency() {
        return new TransactionsTotalCalculator(db, filter).getAccountTotal();
    }

    @Override
    public Total[] getTotals() {
        return new TransactionsTotalCalculator(db, filter).getTransactionsBalance();
    }
}
