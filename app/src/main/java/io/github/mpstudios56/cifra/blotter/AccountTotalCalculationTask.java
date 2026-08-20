/*
 * Copyright (c) 2012 Denis Solonenko.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */

package io.github.mpstudios56.cifra.blotter;

import android.content.Context;
import android.widget.TextView;
import io.github.mpstudios56.cifra.db.DatabaseAdapter;
import io.github.mpstudios56.cifra.db.TransactionsTotalCalculator;
import io.github.mpstudios56.cifra.filter.WhereFilter;
import io.github.mpstudios56.cifra.model.Total;

import static io.github.mpstudios56.cifra.db.DatabaseAdapter.filterOnlyShowSplitSummaryInSameAccount;

public class AccountTotalCalculationTask extends TotalCalculationTask {
	private final WhereFilter filter;

	public AccountTotalCalculationTask(Context context, DatabaseAdapter db, WhereFilter filter, TextView totalText) {
        super(context, db, totalText);
		this.filter = filterOnlyShowSplitSummaryInSameAccount(filter);
	}

    @Override
    public Total getTotalInHomeCurrency() {
        TransactionsTotalCalculator calculator = new TransactionsTotalCalculator(db, filter);
        return calculator.getAccountTotal();
    }

    @Override
    public Total[] getTotals() {
        TransactionsTotalCalculator calculator = new TransactionsTotalCalculator(db, filter);
        return calculator.getTransactionsBalance();
    }

}
