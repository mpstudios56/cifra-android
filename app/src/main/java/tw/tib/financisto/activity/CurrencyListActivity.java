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
package tw.tib.financisto.activity;

import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Toast;
import android.widget.ListAdapter;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.List;

import tw.tib.financisto.R;
import tw.tib.financisto.adapter.CurrencyListAdapter;
import tw.tib.financisto.model.Currency;
import tw.tib.financisto.utils.MenuItemInfo;

public class CurrencyListActivity extends AbstractListActivity<Cursor> {

	private static final int NEW_CURRENCY_REQUEST = 1;
	private static final int EDIT_CURRENCY_REQUEST = 2;
	private static final int MENU_MAKE_DEFAULT = MENU_ADD + 1;

	public CurrencyListActivity() {
		super(R.layout.currency_list);
	}

	@Override
	protected void internalOnCreate(Bundle savedInstanceState) {
		super.internalOnCreate(savedInstanceState);
		ImageButton bRates = findViewById(R.id.bRates);
		bRates.setOnClickListener(view -> {
			Intent intent = new Intent(CurrencyListActivity.this, ExchangeRatesListActivity.class);
			startActivity(intent);
		});

		ImageButton bPeriods = findViewById(R.id.bPeriods);
		if (bPeriods != null) {
			// The week, the fiscal year and the source of the rates: they were
			// three screens away in the settings, and they belong here.
			bPeriods.setOnClickListener(view -> {
				Intent intent = new Intent(CurrencyListActivity.this,
						tw.tib.financisto.activity.PreferencesActivity2.class);
				intent.putExtra(tw.tib.financisto.activity.PreferencesActivity2.SCREEN_EXTRA,
						"tw.tib.financisto.preference.PeriodsCurrencyPreferencesFragment");
				startActivity(intent);
			});
		}

		ImageButton bTidy = findViewById(R.id.bTidy);
		bTidy.setOnClickListener(view -> tidyCurrencies());

		ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.currency_list), (v, windowInsets) -> {
			Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
					| WindowInsetsCompat.Type.statusBars()
					| WindowInsetsCompat.Type.captionBar());
			var lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
			lp.topMargin = insets.top;
			lp.bottomMargin = insets.bottom;
			v.setLayoutParams(lp);
			return WindowInsetsCompat.CONSUMED;
		});
	}

	@Override
	protected List<MenuItemInfo> createContextMenus(long id) {
		List<MenuItemInfo> menus = super.createContextMenus(id);
		for (MenuItemInfo m : menus) {
			if (m.menuId == MENU_VIEW) {
				m.enabled = false;
				break;
			}
		}
		menus.add(new MenuItemInfo(MENU_MAKE_DEFAULT, R.string.currency_make_default));
		return menus;
	}

	@Override
	public boolean onPopupItemSelected(int itemId, View view, int position, long id) {
		if (super.onPopupItemSelected(itemId, view, position, id)) return true;
		switch (itemId) {
			case MENU_MAKE_DEFAULT: {
				makeCurrencyDefault(id);
				return true;
			}
		}
		return false;
	}

	private void makeCurrencyDefault(long id) {
		Currency c = db.get(Currency.class, id);
		c.isDefault = true;
		db.saveOrUpdate(c);
		recreateCursor();
	}

	/**
	 * Folds rows that are the same currency written twice.
	 * <p>
	 * Some ledgers arrive from Financisto with the euro in them ten times over.
	 * To the app those are ten currencies, and adding up two accounts held in
	 * two of them needs an exchange rate from euros to euros - so every total
	 * reads "N/A" while the money is all plainly there. This is the same tidying
	 * that happens at every start, put behind a button for whoever has just
	 * found out that was the reason.
	 */
	private void tidyCurrencies() {
		int folded = tw.tib.financisto.db.Currencies.mergeSameCode(db.db());
		if (folded == 0) {
			Toast.makeText(this, R.string.currency_tidy_none, Toast.LENGTH_SHORT).show();
			return;
		}
		// The list of currencies is held in memory; after moving rows around
		// underneath it, it has to be read again or the screens go on showing
		// what is no longer there.
		tw.tib.financisto.utils.CurrencyCache.initialize(db);
		db.recalculateAccountsBalances();
		Toast.makeText(this, getString(R.string.currency_tidy_done, folded),
				Toast.LENGTH_LONG).show();
		recreateCursor();
	}

	@Override
	protected void addItem() {
		new CurrencySelector(this, db, currencyId -> {
			if (currencyId == 0) {
				Intent intent = new Intent(CurrencyListActivity.this, CurrencyActivity.class);
				startActivityForResult(intent, NEW_CURRENCY_REQUEST);
			} else {
				recreateCursor();
			}
		}).show();
	}

	@Override
	protected ListAdapter createAdapter(Cursor cursor) {
		return new CurrencyListAdapter(db, this, cursor);
	}

	@Override
	protected Cursor loadInBackground() {
		return db.getAllCurrencies("name");
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (resultCode == RESULT_OK) {
			recreateCursor();
		}
	}

	@Override
	protected void deleteItem(View v, int position, long id) {
		if (db.deleteCurrency(id) == 1) {
			recreateCursor();
		} else {
			new AlertDialog.Builder(this)
					.setTitle(R.string.delete)
					.setIcon(android.R.drawable.ic_dialog_alert)
					.setMessage(R.string.currency_in_use)
					.setNeutralButton(R.string.ok, null).show();
		}
	}

	@Override
	public void editItem(View v, int position, long id) {
		Intent intent = new Intent(this, CurrencyActivity.class);
		intent.putExtra(CurrencyActivity.CURRENCY_ID_EXTRA, id);
		startActivityForResult(intent, EDIT_CURRENCY_REQUEST);
	}

	@Override
	protected void viewItem(View v, int position, long id) {
		editItem(v, position, id);
	}

}

