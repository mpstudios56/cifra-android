/*
 * This program is made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */
package tw.tib.financisto.activity;

import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import java.text.DateFormatSymbols;
import java.util.Calendar;
import java.util.Locale;

import tw.tib.financisto.R;
import tw.tib.financisto.db.DatabaseAdapter;
import tw.tib.financisto.model.Currency;
import tw.tib.financisto.model.Total;
import tw.tib.financisto.utils.CurrencyCache;
import tw.tib.financisto.utils.PinProtection;
import tw.tib.financisto.utils.Utils;

/**
 * The month so far, in the four figures worth knowing: what came in, what went
 * out, what is left of the difference, and where the going out went.
 * <p>
 * The app used to open on the list of accounts, which is a list of balances and
 * not an answer to anything. This is the screen somebody actually wants when
 * they pick up the phone to check how the month is going.
 */
public class SummaryFragment extends Fragment {

    /** More than five rows and it is a report, not a summary. */
    private static final int TOP_CATEGORIES = 5;

    private DatabaseAdapter db;
    private Utils u;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.summary_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        db = new DatabaseAdapter(getContext());
        db.open();
        u = new Utils(getContext());

        ViewCompat.setOnApplyWindowInsetsListener(view, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, 0, 0, insets.bottom);
            ((ViewGroup) v).setClipToPadding(false);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (db != null) {
            db.close();
            db = null;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        PinProtection.unlock(getContext());
        // Rebuilt on every visit rather than cached: a transaction added on the
        // next tab along would otherwise leave stale figures here.
        refresh();
    }

    @Override
    public void onPause() {
        super.onPause();
        PinProtection.lock(getContext());
    }

    private void refresh() {
        View view = getView();
        if (view == null || db == null) {
            return;
        }
        Currency home = CurrencyCache.getHomeCurrency();

        Calendar from = Calendar.getInstance();
        from.set(Calendar.DAY_OF_MONTH, 1);
        from.set(Calendar.HOUR_OF_DAY, 0);
        from.set(Calendar.MINUTE, 0);
        from.set(Calendar.SECOND, 0);
        from.set(Calendar.MILLISECOND, 0);
        long start = from.getTimeInMillis();
        long end = System.currentTimeMillis();

        String month = new DateFormatSymbols(Locale.getDefault())
                .getMonths()[from.get(Calendar.MONTH)];
        ((TextView) view.findViewById(R.id.summary_period))
                .setText(month + " " + from.get(Calendar.YEAR));

        long income = sum(start, end, true);
        long expense = sum(start, end, false);
        long saved = income + expense;          // expense is already negative

        u.setAmountText(view.findViewById(R.id.summary_income), home, income, false);
        u.setAmountText(view.findViewById(R.id.summary_expense), home, expense, false);
        u.setAmountText(view.findViewById(R.id.summary_saved), home, saved, false);

        TextView note = view.findViewById(R.id.summary_saved_note);
        if (income > 0) {
            long share = 100 * saved / income;
            note.setText(getString(R.string.summary_saved_share, share));
        } else {
            note.setText("");
        }

        showTopCategories(view, home, start, end, expense);
        showNetWorth(view, home);
    }

    /**
     * Income or spending for the period. Transfers are left out on purpose:
     * moving money between one's own accounts is neither.
     */
    private long sum(long start, long end, boolean income) {
        String sql = "select coalesce(sum(from_amount), 0) from transactions"
                + " where is_template = 0 and parent_id = 0 and to_account_id = 0"
                + " and datetime between ? and ?"
                + (income ? " and from_amount > 0" : " and from_amount < 0");
        try (Cursor c = db.db().rawQuery(sql,
                new String[]{String.valueOf(start), String.valueOf(end)})) {
            return c.moveToFirst() ? c.getLong(0) : 0;
        }
    }

    private void showTopCategories(View view, Currency home, long start, long end, long expense) {
        LinearLayout list = view.findViewById(R.id.summary_categories);
        list.removeAllViews();
        String sql = "select c.title, sum(t.from_amount) s from transactions t"
                + " inner join category c on c._id = t.category_id"
                + " where t.is_template = 0 and t.parent_id = 0 and t.to_account_id = 0"
                + " and t.from_amount < 0 and t.datetime between ? and ?"
                + " group by c._id order by s asc limit " + TOP_CATEGORIES;
        try (Cursor c = db.db().rawQuery(sql,
                new String[]{String.valueOf(start), String.valueOf(end)})) {
            while (c.moveToNext()) {
                String title = c.getString(0);
                long amount = c.getLong(1);
                long share = expense != 0 ? 100 * amount / expense : 0;
                list.addView(categoryRow(title, amount, share, home));
            }
        }
        if (list.getChildCount() == 0) {
            TextView empty = new TextView(getContext());
            empty.setText(R.string.summary_nothing_yet);
            list.addView(empty);
        }
    }

    private View categoryRow(String title, long amount, long share, Currency home) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 8, 0, 8);

        TextView name = new TextView(getContext());
        name.setText(getString(R.string.summary_category_line, title, share));
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(name, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView value = new TextView(getContext());
        value.setSingleLine(true);
        u.setAmountText(value, home, amount, false);
        row.addView(value);
        return row;
    }

    private void showNetWorth(View view, Currency home) {
        // The app's own calculation, not a plain sum of the account column: with
        // more than one currency in play a sum of raw balances is a wrong number
        // dressed up as a right one.
        Total total = db.getAccountsTotalInHomeCurrencyWithFilter(null);
        u.setAmountText(view.findViewById(R.id.summary_net_worth), home, total.balance, false);
        ((TextView) view.findViewById(R.id.summary_net_worth_note))
                .setText(R.string.summary_net_worth_note);
    }
}
