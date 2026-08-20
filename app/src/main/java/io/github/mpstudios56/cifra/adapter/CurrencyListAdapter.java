/*******************************************************************************
 * Copyright (c) 2010 Denis Solonenko.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * Contributors:
 *     Denis Solonenko - initial API and implementation
 ******************************************************************************/
package io.github.mpstudios56.cifra.adapter;

import io.github.mpstudios56.cifra.R;
import io.github.mpstudios56.cifra.db.DatabaseAdapter;
import io.github.mpstudios56.cifra.model.Currency;
import io.github.mpstudios56.cifra.utils.Utils;
import io.github.mpstudios56.cifra.orb.EntityManager;
import android.content.Context;
import android.database.Cursor;

import java.math.BigDecimal;

public class CurrencyListAdapter extends AbstractGenericListAdapter {
	Context context;
	DatabaseAdapter db;

	public CurrencyListAdapter(DatabaseAdapter db, Context context, Cursor c) {
		super(db, context, c, R.layout.currency_list_item);
		this.context = context;
		this.db = db;
	}

	@Override
	protected void bindView(GenericViewHolder v, Context context, Cursor cursor) {
		Currency c = EntityManager.loadFromCursor(cursor, Currency.class);
		v.lineView.setText(c.title);
		String nameLine = c.name;
		if (c.tradingCurrencyId != 0) {
			Currency tradingCurrency = db.get(Currency.class, c.tradingCurrencyId);
			if (tradingCurrency != null) {
				nameLine = "(" + context.getString(R.string.trading_currency) + " " + tradingCurrency.name + ") " + nameLine;
			}
		}
		v.numberView.setText(nameLine);
		// a worked example of the format, not anybody's money: always shown
		v.amountView.setText(Utils.amountToStringPlain(new StringBuilder(), c,
				new BigDecimal(1000).movePointRight(c.decimals < 3 ? 2 : c.decimals), false).toString());
		if (c.isDefault) {
			v.iconView.setImageResource(R.drawable.ic_currency_home);
		} else {
			v.iconView.setImageDrawable(null);
		}
	}

}
