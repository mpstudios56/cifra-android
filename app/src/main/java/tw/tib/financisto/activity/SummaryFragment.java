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
import tw.tib.financisto.utils.Identity;
import tw.tib.financisto.utils.MyPreferences;
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
    /** Periods back from this one. Zero is the period in progress. */
    private int periodsBack = 0;
    /** Whether the spending card is showing every category or only the largest. */
    private boolean allCategories = false;
    /** Whether the total is showing the accounts it is made of. */
    private boolean showAccounts = false;

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
            periodsBack++;
            refresh();
        });
        view.findViewById(R.id.summary_next).setOnClickListener(v -> {
            if (periodsBack > 0) {
                periodsBack--;
                refresh();
            }
        });
        // The five largest answer "where did it go" most of the time; the rest
        // are one tap away rather than on a screen of their own.
        view.findViewById(R.id.summary_categories_header).setOnClickListener(v -> {
            allCategories = !allCategories;
            refresh();
        });
        // A total nobody can take apart is a number to be believed rather than
        // read. Opening it says which accounts made it, and how much each put in.
        view.findViewById(R.id.summary_net_worth_header).setOnClickListener(v -> {
            showAccounts = !showAccounts;
            refresh();
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

        String period = MyPreferences.getSummaryPeriod(getContext());
        Calendar from = startOf(period);
        long start = from.getTimeInMillis();

        Calendar next = (Calendar) from.clone();
        step(next, period, 1);
        // A period still running ends now, not at a date that has not arrived.
        long end = Math.min(next.getTimeInMillis() - 1, System.currentTimeMillis());

        ((TextView) view.findViewById(R.id.summary_period)).setText(label(from, period));
        // Nothing to see ahead of the period in progress.
        View forward = view.findViewById(R.id.summary_next);
        forward.setEnabled(periodsBack > 0);
        forward.setAlpha(periodsBack > 0 ? 1f : 0.3f);

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

    // ------------------------------------------------------------------ period

    /**
     * The beginning of the period being shown, counting back from the one in
     * progress. Everything is cut to whole weeks, months, quarters or years:
     * a summary of "the last thirty days" cannot be compared with the thirty
     * days before it, because neither has the same rent day in it.
     */
    private Calendar startOf(String period) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        switch (period) {
            case "WEEK":
                c.set(Calendar.DAY_OF_WEEK, c.getFirstDayOfWeek());
                break;
            case "QUARTER":
                c.set(Calendar.DAY_OF_MONTH, 1);
                c.set(Calendar.MONTH, (c.get(Calendar.MONTH) / 3) * 3);
                break;
            case "YEAR":
                c.set(Calendar.DAY_OF_YEAR, 1);
                break;
            default:
                c.set(Calendar.DAY_OF_MONTH, 1);
                break;
        }
        step(c, period, -periodsBack);
        return c;
    }

    private void step(Calendar c, String period, int by) {
        switch (period) {
            case "WEEK": c.add(Calendar.WEEK_OF_YEAR, by); break;
            case "QUARTER": c.add(Calendar.MONTH, 3 * by); break;
            case "YEAR": c.add(Calendar.YEAR, by); break;
            default: c.add(Calendar.MONTH, by); break;
        }
    }

    /** What to write above the figures, in the words the period deserves. */
    private String label(Calendar from, String period) {
        int year = from.get(Calendar.YEAR);
        switch (period) {
            case "WEEK": {
                Calendar to = (Calendar) from.clone();
                to.add(Calendar.DAY_OF_YEAR, 6);
                java.text.DateFormat d = android.text.format.DateFormat
                        .getMediumDateFormat(getContext());
                return d.format(from.getTime()) + " – " + d.format(to.getTime());
            }
            case "QUARTER":
                return getString(R.string.summary_quarter,
                        from.get(Calendar.MONTH) / 3 + 1, year);
            case "YEAR":
                return String.valueOf(year);
            default:
                return new DateFormatSymbols(Locale.getDefault())
                        .getMonths()[from.get(Calendar.MONTH)] + " " + year;
        }
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
                + " group by c._id order by s asc"
                + (allCategories ? "" : " limit " + TOP_CATEGORIES);
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
        int shown = list.getChildCount();
        if (shown == 0) {
            TextView empty = new TextView(getContext());
            empty.setText(R.string.summary_nothing_yet);
            list.addView(empty);
        }

        // The header says what tapping it will do, and points the way it goes.
        TextView more = view.findViewById(R.id.summary_categories_more);
        android.widget.ImageView chevron = view.findViewById(R.id.summary_categories_chevron);
        // Offering "show every category" when five is every category there is
        // would open nothing at all.
        boolean worthExpanding = allCategories || shown >= TOP_CATEGORIES;
        more.setVisibility(worthExpanding ? View.VISIBLE : View.GONE);
        chevron.setVisibility(worthExpanding ? View.VISIBLE : View.GONE);
        more.setText(allCategories ? R.string.summary_show_top : R.string.summary_show_all);
        chevron.setRotation(allCategories ? 180 : 0);
        view.findViewById(R.id.summary_categories_header).setClickable(worthExpanding);
    }

    /**
     * The accounts the total is made of, biggest first, and - once two people
     * are sharing - what belongs to the shared ones and what does not.
     * <p>
     * The two subtotals appear only when something is actually shared. A switch
     * in the settings for a line that means nothing until sharing is set up
     * would be one more thing to find and one more thing to explain.
     */
    private void fillAccounts(LinearLayout into, Currency home) {
        long shared = 0;
        long mine = 0;
        boolean anyShared = false;
        String sql = "select a.title, a.total_amount, a.currency_id,"
                + " (select count(*) from shared_thing s"
                + " where s.kind = ? and s.uuid = a.uuid) as is_shared"
                + ", a._id"
                + " from account a where a.is_active = 1 and a.is_include_into_totals = 1"
                + " order by a.total_amount desc";
        sharedColours = tw.tib.financisto.sync.SharedWith.coloursByAccount(db.db());
        try (Cursor c = db.db().rawQuery(sql, new String[]{"account"})) {
            while (c.moveToNext()) {
                String title = c.getString(0);
                long amount = c.getLong(1);
                Currency currency = CurrencyCache.getCurrency(c.getLong(2));
                boolean isShared = c.getInt(3) > 0;
                long accountId = c.getLong(4);
                anyShared |= isShared;
                if (isShared) {
                    shared += amount;
                } else {
                    mine += amount;
                }
                into.addView(accountRow(title, amount, currency, isShared, accountId));
            }
        } catch (Exception e) {
            return;
        }
        if (anyShared) {
            into.addView(subtotal(getString(R.string.summary_shared), shared, home));
            into.addView(subtotal(getString(R.string.summary_not_shared), mine, home));
        }
    }

    /** One account: its name, and what is on it. */
    private View accountRow(String title, long amount, Currency currency, boolean isShared,
                            long accountId) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(6), 0, dp(6));

        TextView name = new TextView(getContext());
        name.setText(title);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(name, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        if (isShared) {
            // A dot in the other person\'s colour rather than a word: the line
            // is already a name and a figure, and "condiviso" written on half of
            // them would be noise.
            // One dot per person the account is held with, in the same square
            // block as the account list: a single dot said "shared" and hid
            // that it was shared with two.
            java.util.List<Integer> theirs = sharedColours.get(accountId);
            if (theirs == null || theirs.isEmpty()) {
                theirs = java.util.Collections.singletonList(Identity.COLOURS[1]);
            }
            int many = Math.min(theirs.size(), 9);
            int perRow = many <= 1 ? 1 : (many <= 4 ? 2 : 3);
            int size = many <= 1 ? 8 : (many <= 4 ? 6 : 4);

            LinearLayout dots = new LinearLayout(getContext());
            dots.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams blockLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            blockLp.gravity = android.view.Gravity.CENTER_VERTICAL;
            blockLp.rightMargin = dp(8);
            dots.setLayoutParams(blockLp);

            LinearLayout line = null;
            for (int i = 0; i < many; i++) {
                if (i % perRow == 0) {
                    line = new LinearLayout(getContext());
                    line.setOrientation(LinearLayout.HORIZONTAL);
                    dots.addView(line);
                }
                View dot = new View(getContext());
                LinearLayout.LayoutParams lp =
                        new LinearLayout.LayoutParams(dp(size), dp(size));
                lp.setMargins(dp(1), dp(1), dp(1), dp(1));
                dot.setLayoutParams(lp);
                android.graphics.drawable.GradientDrawable shape =
                        new android.graphics.drawable.GradientDrawable();
                shape.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                shape.setColor(theirs.get(i));
                dot.setBackground(shape);
                line.addView(dot);
            }
            row.addView(dots);
        }

        TextView value = new TextView(getContext());
        value.setSingleLine(true);
        u.setAmountText(value, currency, amount, false);
        row.addView(value);
        return row;
    }

    private View subtotal(String label, long amount, Currency home) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(8), 0, 0);

        TextView name = new TextView(getContext());
        name.setText(label);
        name.setAlpha(0.7f);
        row.addView(name, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView value = new TextView(getContext());
        value.setSingleLine(true);
        value.setTypeface(value.getTypeface(), android.graphics.Typeface.BOLD);
        u.setAmountText(value, home, amount, false);
        row.addView(value);
        return row;
    }

    /** The name and figure on one line, and under them a bar of that width. */
    /** The colour of whoever each shared account is held with, read once. */
    private java.util.Map<Long, java.util.List<Integer>> sharedColours =
            java.util.Collections.emptyMap();

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

        LinearLayout accounts = view.findViewById(R.id.summary_accounts);
        accounts.removeAllViews();
        accounts.setVisibility(showAccounts ? View.VISIBLE : View.GONE);
        if (showAccounts) {
            fillAccounts(accounts, home);
        }
        ((TextView) view.findViewById(R.id.summary_net_worth_more)).setText(
                showAccounts ? R.string.summary_accounts_hide : R.string.summary_accounts_show);
        view.findViewById(R.id.summary_net_worth_chevron).setRotation(showAccounts ? 180 : 0);
    }
}
