/*
 * This program is made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */
package tw.tib.financisto.activity;

import android.database.Cursor;
import android.graphics.drawable.GradientDrawable;
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
    /** Months back from this one. Zero is the month in progress. */
    private int monthsBack = 0;

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
            // Nothing stands between this and the status bar since the strip moved
            // to the bottom, so it keeps clear of it itself.
            v.setPadding(0, insets.top, 0, 0);
            ((ViewGroup) v).setClipToPadding(true);
            return WindowInsetsCompat.CONSUMED;
        });

        view.findViewById(R.id.summary_prev).setOnClickListener(v -> {
            monthsBack++;
            refresh();
        });
        view.findViewById(R.id.summary_next).setOnClickListener(v -> {
            if (monthsBack > 0) {
                monthsBack--;
                refresh();
            }
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
        from.add(Calendar.MONTH, -monthsBack);
        long start = from.getTimeInMillis();

        Calendar next = (Calendar) from.clone();
        next.add(Calendar.MONTH, 1);
        // A month still running ends now, not at a date that has not arrived.
        long end = Math.min(next.getTimeInMillis() - 1, System.currentTimeMillis());

        String month = new DateFormatSymbols(Locale.getDefault())
                .getMonths()[from.get(Calendar.MONTH)];
        ((TextView) view.findViewById(R.id.summary_period))
                .setText(month + " " + from.get(Calendar.YEAR));
        // Nothing to see ahead of the month in progress.
        View forward = view.findViewById(R.id.summary_next);
        forward.setEnabled(monthsBack > 0);
        forward.setAlpha(monthsBack > 0 ? 1f : 0.3f);

        long income = sum(start, end, true);
        long expense = sum(start, end, false);
        long saved = income + expense;          // expense is already negative

        u.setAmountText(view.findViewById(R.id.summary_income), home, income, false);
        u.setAmountText(view.findViewById(R.id.summary_expense), home, expense, false);
        u.setAmountText(view.findViewById(R.id.summary_saved), home, saved, false);

        // How the two compare, side by side. Absolute figures say which is bigger;
        // the bar says by how much without anyone having to do the arithmetic.
        fillBar(view.findViewById(R.id.summary_split_bar),
                new long[]{income, -expense}, new int[]{IN, OUT});

        TextView note = view.findViewById(R.id.summary_saved_note);
        LinearLayout savedBar = view.findViewById(R.id.summary_saved_bar);
        if (income > 0 && saved > 0) {
            long share = 100 * saved / income;
            note.setText(getString(R.string.summary_saved_share, share));
            fillBar(savedBar, new long[]{saved, income - saved}, new int[]{IN, TRACK});
        } else {
            note.setText("");
            // Nothing kept, or nothing came in: a bar would only be an empty box.
            savedBar.setVisibility(View.GONE);
        }

        showTopCategories(view, home, start, end, expense);
        showNetWorth(view, home);
    }

    /**
     * Only the accounts that count. An account can be kept out of the totals or out
     * of the reports, and the reports honour both; counting everything here made
     * this screen disagree with them, which is worse than either answer alone.
     */
    private static final String COUNTED_ACCOUNTS =
            " and exists (select 1 from account a where a._id = t.from_account_id"
                    + " and a.is_include_into_totals = 1 and a.is_include_into_reports = 1)";

    /**
     * Income or spending for the period. Transfers are left out on purpose:
     * moving money between one's own accounts is neither.
     */
    private long sum(long start, long end, boolean income) {
        String sql = "select coalesce(sum(t.from_amount), 0) from transactions t"
                + " where t.is_template = 0 and t.parent_id = 0 and t.to_account_id = 0"
                + " and t.datetime between ? and ?"
                + (income ? " and t.from_amount > 0" : " and t.from_amount < 0")
                + COUNTED_ACCOUNTS;
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
                + COUNTED_ACCOUNTS
                + " group by c._id order by s asc limit " + TOP_CATEGORIES;
        try (Cursor c = db.db().rawQuery(sql,
                new String[]{String.valueOf(start), String.valueOf(end)})) {
            while (c.moveToNext()) {
                String title = c.getString(0);
                long amount = c.getLong(1);
                long share = expense != 0 ? 100 * amount / expense : 0;
                list.addView(categoryRow(title, amount, share, home,
                        SLICES[list.getChildCount() % SLICES.length]));
            }
        }
        if (list.getChildCount() == 0) {
            TextView empty = new TextView(getContext());
            empty.setText(R.string.summary_nothing_yet);
            list.addView(empty);
        }
    }

    /** The name and figure on one line, and under them a bar of that width. */
    private View categoryRow(String title, long amount, long share, Currency home, int colour) {
        LinearLayout block = new LinearLayout(getContext());
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(0, 6, 0, 10);

        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);

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
        block.addView(row);

        LinearLayout bar = new LinearLayout(getContext());
        bar.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(7));
        lp.topMargin = dp(5);
        block.addView(bar, lp);
        fillBar(bar, new long[]{share, 100 - share}, new int[]{colour, TRACK});
        return block;
    }

    // Enough colours for the five rows, none of them red: a category is not a
    // warning, it is just where the money went.
    private static final int[] SLICES = {0xFF5B8DEF, 0xFF3FA96F, 0xFFE9A742,
            0xFFC9709A, 0xFF6ECBC0};
    private static final int IN = 0xFF3FA96F;
    private static final int OUT = 0xFFD0453B;
    private static final int TRACK = 0x33FFFFFF;

    /**
     * Lays out a bar as coloured pieces in proportion to the values given. Weights
     * do the measuring, so it fits whatever width the screen turns out to be.
     */
    private void fillBar(LinearLayout bar, long[] values, int[] colours) {
        bar.removeAllViews();
        bar.setVisibility(View.VISIBLE);
        long total = 0;
        for (long v : values) {
            total += Math.max(v, 0);
        }
        if (total <= 0) {
            bar.setVisibility(View.GONE);
            return;
        }
        for (int i = 0; i < values.length; i++) {
            float weight = Math.max(values[i], 0);
            if (weight <= 0) {
                continue;
            }
            View piece = new View(getContext());
            GradientDrawable shape = new GradientDrawable();
            shape.setColor(colours[i]);
            shape.setCornerRadius(dp(4));
            piece.setBackground(shape);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, weight);
            // A hair of space so the two colours read as two pieces, not a gradient.
            lp.rightMargin = i < values.length - 1 ? dp(2) : 0;
            bar.addView(piece, lp);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
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
