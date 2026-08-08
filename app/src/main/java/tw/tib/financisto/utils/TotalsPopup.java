/*
 * This program is made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */
package tw.tib.financisto.utils;

import android.app.AlertDialog;
import android.content.Context;
import android.database.Cursor;

import java.util.Calendar;

import tw.tib.financisto.R;
import tw.tib.financisto.db.DatabaseAdapter;
import tw.tib.financisto.db.DatabaseHelper;
import tw.tib.financisto.filter.WhereFilter;
import tw.tib.financisto.model.Currency;

/**
 * What happens when the total is tapped.
 * <p>
 * The two totals in the app are not the same kind of number and never will be.
 * The one on the accounts screen is how much there is: a sum of balances, a
 * standing figure. The one on the ledger is the net of the transactions being
 * looked at, which moves with the filter and is a flow. Shown as identical
 * chips they invite a comparison that cannot come out even, so each now says
 * what it is before saying what it is made of.
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
        boolean filtered = selection != null && !selection.trim().isEmpty();
        String where = filtered ? selection : "1=1";
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
        show(context, R.string.totals_blotter_title,
                context.getString(filtered
                        ? R.string.totals_blotter_explained_filtered
                        : R.string.totals_blotter_explained),
                context.getString(R.string.summary_income), income,
                context.getString(R.string.summary_expense), expense,
                context.getString(R.string.summary_saved), income + expense, currency);
    }

    /** What went in and out this month across the accounts that count towards it. */
    public static void showAccountsBreakdown(Context context, DatabaseAdapter db) {
        Calendar from = Calendar.getInstance();
        from.set(Calendar.DAY_OF_MONTH, 1);
        from.set(Calendar.HOUR_OF_DAY, 0);
        from.set(Calendar.MINUTE, 0);
        from.set(Calendar.SECOND, 0);
        from.set(Calendar.MILLISECOND, 0);

        String sql = "select"
                + " coalesce(sum(case when t.from_amount > 0 then t.from_amount else 0 end), 0),"
                + " coalesce(sum(case when t.from_amount < 0 then t.from_amount else 0 end), 0)"
                + " from transactions t"
                // A transfer between two of your own accounts is neither, and a split
                // counted once per part would double everything it touches.
                + " where t.is_template = 0 and t.parent_id = 0 and t.to_account_id = 0"
                + " and t.datetime >= ?"
                + " and exists (select 1 from account a where a._id = t.from_account_id"
                + "   and a.is_include_into_totals = 1)";
        long income = 0, expense = 0;
        try (Cursor c = db.db().rawQuery(sql,
                new String[]{String.valueOf(from.getTimeInMillis())})) {
            if (c.moveToFirst()) {
                income = c.getLong(0);
                expense = c.getLong(1);
            }
        }
        Currency currency = CurrencyCache.getHomeCurrency();
        show(context, R.string.totals_accounts_title,
                context.getString(R.string.totals_accounts_explained),
                context.getString(R.string.summary_income), income,
                context.getString(R.string.summary_expense), expense,
                context.getString(R.string.summary_saved), income + expense, currency);
    }

    private static void show(Context context, int titleId, String explanation,
                             String firstLabel, long first,
                             String secondLabel, long second,
                             String thirdLabel, long third, Currency currency) {
        String message = explanation + "\n\n"
                + firstLabel + "\n" + Utils.amountToString(currency, first) + "\n\n"
                + secondLabel + "\n" + Utils.amountToString(currency, second) + "\n\n"
                + thirdLabel + "\n" + Utils.amountToString(currency, third);
        new AlertDialog.Builder(context)
                .setTitle(titleId)
                .setMessage(message)
                .setPositiveButton(R.string.ok, null)
                .show();
    }
}
