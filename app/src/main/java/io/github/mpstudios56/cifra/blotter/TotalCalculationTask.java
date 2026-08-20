/*
 * Copyright (c) 2012 Denis Solonenko.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */
package io.github.mpstudios56.cifra.blotter;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;
import io.github.mpstudios56.cifra.R;
import io.github.mpstudios56.cifra.db.DatabaseAdapter;
import io.github.mpstudios56.cifra.model.Currency;
import io.github.mpstudios56.cifra.model.Total;
import io.github.mpstudios56.cifra.utils.CurrencyCache;
import io.github.mpstudios56.cifra.utils.MyPreferences;
import io.github.mpstudios56.cifra.utils.Utils;

public abstract class TotalCalculationTask extends AsyncTask<Object, Total, Total> {

	protected final DatabaseAdapter db;

	private volatile boolean isRunning = true;

	private final Context context;
	private final TextView totalText;
	private final Utils u;

	public TotalCalculationTask(Context context, DatabaseAdapter db, TextView totalText) {
		this.context = context;
		this.db = db;
		this.totalText = totalText;
		this.u = new Utils(context);
	}

    @Override
	protected Total doInBackground(Object... params) {
		try {
			return getTotalInHomeCurrency();
		} catch (Exception ex) {
			Log.e("TotalBalance", "Unexpected error", ex);
			return Total.ZERO;
		}
	}

    public Total getTotalInHomeCurrency() {
		Total[] totals = getTotals();
		return u.calculateTotalInCurrency(totals, db.getLatestRates(), CurrencyCache.getHomeCurrency());
	}

    public abstract Total[] getTotals();

	@Override
	protected void onPostExecute(Total result) {
		if (isRunning && context != null) {
            if (result.currency == Currency.EMPTY) {
				if (CurrencyCache.getHomeCurrency() == Currency.EMPTY) {
					Toast.makeText(context, R.string.currency_make_default_warning, Toast.LENGTH_LONG).show();
				}
            }
            Utils u = new Utils(context);
    	    u.setTotal(totalText, result);
			if (MyPreferences.isBlurBalances()) {
				u.applyBlur(totalText);
				totalText.invalidate();
			}
			else {
				totalText.getPaint().setMaskFilter(null);
				totalText.invalidate();
			}
		}
	}
	
	public void stop() {
		isRunning = false;
	}
	
}
