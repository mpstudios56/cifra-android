/*
 * This program is made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */
package tw.tib.financisto.utils;

import android.app.AlertDialog;
import android.content.Context;
import android.database.Cursor;

import tw.tib.financisto.R;
import tw.tib.financisto.db.DatabaseAdapter;
import tw.tib.financisto.db.DatabaseHelper;
import tw.tib.financisto.filter.WhereFilter;
import tw.tib.financisto.model.Currency;

/**
 * What happens when the total is tapped.
 * <p>
 * It used to open a screen listing the total once per currency. With one
 * currency - which is nearly everyone - that screen showed a single line
 * repeating the figure already on the button, so tapping appeared to do
 * nothing. It still opens for anyone actually keeping accounts in more than one
 * currency; otherwise it answers the more useful question, which is what the
 * figure is made of.
 */
public class TotalsPopup {

    private TotalsPopup() {
    }

    /** Whether the per-currency screen has anything to say. */
    public static boolean severalCurrencies(DatabaseAdapter db) {
        String sql = "select count(distinct currency_id) from account where is_active = 1";
        try (Cursor c = db.db().rawQuery(sql, null)) {
            return c.moveToFirst() && c.getLong(0) > 1;
        } catch (Exception e) {
            // If it cannot be told, keep the old behaviour rather than hide a screen.
            return true;
        }
    }

    /** Income and spending behind the figure the ledger is showing. */
    public static void showBlotterBreakdown(Context context, DatabaseAdapter db, WhereFilter filter) {
        String selection = filter.getSelection();
        String where = selection == null || selection.trim().isEmpty() ? "1=1" : selection;
        String sql = "select"
                + " coalesce(sum(case when from_amount > 0 then from_amount else 0 end), 0),"
                + " coalesce(sum(case when from_amount < 0 then from_amount else 0 end), 0)"
                + " from " + DatabaseHelper.V_BLOTTER_FOR_ACCOUNT_WITH_SPLITS
                + " where " + where;
        long income = 0, expense = 0;
        try (Cursor c = db.db().rawQuery(sql, filter.getSelectionArgs())) {
            if (c.moveToFirst()) {
                income = c.getLong(0);
                expense = c.getLong(1);
            }
        }
        Currency currency = CurrencyCache.getHomeCurrency();
        show(context, R.string.total,
                context.getString(R.string.summary_income), income,
                context.getString(R.string.summary_expense), expense,
                context.getString(R.string.summary_saved), income + expense, currency);
    }

    /** What is owned against what is owed, which is what a list of balances adds up to. */
    public static void showAccountsBreakdown(Context context, DatabaseAdapter db) {
        String sql = "select"
                + " coalesce(sum(case when total_amount > 0 then total_amount else 0 end), 0),"
                + " coalesce(sum(case when total_amount < 0 then total_amount else 0 end), 0)"
                + " from account where is_active = 1 and is_include_into_totals = 1";
        long assets = 0, liabilities = 0;
        try (Cursor c = db.db().rawQuery(sql, null)) {
            if (c.moveToFirst()) {
                assets = c.getLong(0);
                liabilities = c.getLong(1);
            }
        }
        Currency currency = CurrencyCache.getHomeCurrency();
        show(context, R.string.total,
                context.getString(R.string.totals_assets), assets,
                context.getString(R.string.totals_liabilities), liabilities,
                context.getString(R.string.totals_net), assets + liabilities, currency);
    }

    private static void show(Context context, int titleId,
                             String firstLabel, long first,
                             String secondLabel, long second,
                             String thirdLabel, long third, Currency currency) {
        String message = firstLabel + "\n" + Utils.amountToString(currency, first) + "\n\n"
                + secondLabel + "\n" + Utils.amountToString(currency, second) + "\n\n"
                + thirdLabel + "\n" + Utils.amountToString(currency, third);
        new AlertDialog.Builder(context)
                .setTitle(titleId)
                .setMessage(message)
                .setPositiveButton(R.string.ok, null)
                .show();
    }
}
