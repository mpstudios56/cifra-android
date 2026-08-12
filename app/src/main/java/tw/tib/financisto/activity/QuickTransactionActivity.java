/*
 * This program is made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */
package tw.tib.financisto.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tw.tib.financisto.R;
import tw.tib.financisto.db.DatabaseAdapter;
import tw.tib.financisto.model.Account;
import tw.tib.financisto.model.Currency;
import tw.tib.financisto.model.Transaction;
import tw.tib.financisto.model.TransactionStatus;
import tw.tib.financisto.utils.CategoryIcon;
import tw.tib.financisto.utils.CategoryIcons;
import tw.tib.financisto.utils.MyPreferences;
import tw.tib.financisto.utils.PinProtection;
import tw.tib.financisto.utils.Utils;
import tw.tib.financisto.view.FlowLayout;

/**
 * Records a transaction in as few taps as possible: type the amount, tap a
 * category, save.
 * <p>
 * The full form asks for a dozen things, of which one entry in twenty needs more
 * than two. Everything else is assumed - today, the account last used here, and
 * a cleared status - and can still be corrected afterwards by opening the entry
 * in the ordinary screen.
 */
public class QuickTransactionActivity extends AppCompatActivity {

    private static final String PREFS = "quick_transaction";
    private static final String KEY_ACCOUNT = "account_id";
    /**
     * How many category shortcuts fit before the row is more work than a list.
     * Six, with "other categories" after them, is two lines on a normal screen
     * and never pushes the keypad off the bottom.
     */
    private static final int SHORTCUTS = 6;
    private static final int PICK_CATEGORY = 1;
    private static final int NEW_ACCOUNT = 2;

    private DatabaseAdapter db;
    private Account account;
    private long categoryId = 0;
    /** The amount as typed, in cents: digits accumulate from the right. */
    private long cents = 0;

    /** The shortcut buttons by category, so the chosen one can be filled in. */
    private final Map<Long, Button> chips = new LinkedHashMap<>();

    private TextView amountView;
    private TextView chosenView;
    private Button accountButton;
    private ToggleButton signButton;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(MyPreferences.switchLocale(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (MyPreferences.isSecureWindow()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
        setContentView(R.layout.quick_transaction);
        setTitle(R.string.quick_transaction);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.quick_base), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), insets.top, v.getPaddingRight(), insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        db = new DatabaseAdapter(this);
        db.open();

        amountView = findViewById(R.id.quick_amount);
        chosenView = findViewById(R.id.quick_chosen);
        accountButton = findViewById(R.id.quick_account);
        signButton = findViewById(R.id.quick_sign);

        accountButton.setOnClickListener(v -> pickAccount());
        findViewById(R.id.quick_save).setOnClickListener(v -> save());

        buildKeypad();
        selectAccount(rememberedAccount());
        buildCategoryShortcuts();
        showAmount();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        db.close();
    }

    @Override
    protected void onPause() {
        super.onPause();
        PinProtection.lock(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        PinProtection.unlock(this);
    }

    // ------------------------------------------------------------------ keypad

    private void buildKeypad() {
        GridLayout keypad = findViewById(R.id.quick_keypad);
        String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "C", "0", "<"};
        float density = getResources().getDisplayMetrics().density;
        int margin = Math.round(7 * density);
        // A fixed key rather than one that stretches to fill whatever is left:
        // stretched, the pad took over half the screen and the digits still
        // looked small inside it. Sized here, the keys are smaller and the
        // numbers on them larger, which is the way round it should have been.
        int side = Math.round(66 * density);
        for (String key : keys) {
            Button b = new Button(this);
            b.setText("<".equals(key) ? "⌫" : key);
            b.setTextSize(28);
            b.setTextColor(0xFFFFFFFF);
            // The unlock keypad's round key. Same gesture, same key.
            b.setBackgroundResource(R.drawable.quick_key);
            b.setStateListAnimator(null);
            b.setPadding(0, 0, 0, 0);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = side;
            lp.height = side;
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1);
            lp.rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1);
            lp.setMargins(margin, margin, margin, margin);
            b.setLayoutParams(lp);
            b.setOnClickListener(v -> onKey(key));
            keypad.addView(b);
        }
    }

    private void onKey(String key) {
        switch (key) {
            case "C" -> cents = 0;
            case "<" -> cents /= 10;
            // Stop at ten million: beyond that the display is unreadable and the
            // figure is certainly a mistake.
            default -> {
                if (cents < 100000000L) {
                    cents = cents * 10 + Long.parseLong(key);
                }
            }
        }
        showAmount();
    }

    /**
     * The figure, with the cents set smaller than the rest.
     * <p>
     * There is no comma key and there does not need to be: the digits fill in
     * from the right, the way a till works, so typing 1250 reads 12,50. That is
     * not obvious from a keypad of plain numbers, and the first person to see
     * this screen asked where the comma was. Shrinking everything after the
     * separator shows what the keys are doing without a word of explanation.
     */
    private void showAmount() {
        Currency currency = account != null ? account.currency : Currency.EMPTY;
        String text = Utils.amountToString(currency, cents);
        char separator = separatorOf(currency);
        int cut = text.lastIndexOf(separator);
        if (cut < 0) {
            amountView.setText(text);
            return;
        }
        SpannableString styled = new SpannableString(text);
        styled.setSpan(new RelativeSizeSpan(0.58f), cut, text.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        amountView.setText(styled);
    }

    /** The currency writes it as '.' or ',' - quotes included - so unwrap it. */
    private char separatorOf(Currency currency) {
        String written = currency.decimalSeparator;
        if (written == null || written.isEmpty()) {
            return '.';
        }
        String bare = written.replace("'", "");
        return bare.isEmpty() ? '.' : bare.charAt(0);
    }

    // ----------------------------------------------------------------- account

    private long rememberedAccount() {
        return getSharedPreferences(PREFS, MODE_PRIVATE).getLong(KEY_ACCOUNT, -1);
    }

    private void selectAccount(long id) {
        Account chosen = id > 0 ? db.getAccount(id) : null;
        if (chosen == null || !chosen.isActive) {
            chosen = firstActiveAccount();
        }
        account = chosen;
        if (account == null) {
            accountButton.setText(R.string.select_account);
            return;
        }
        accountButton.setText(account.title);
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putLong(KEY_ACCOUNT, account.id).apply();
        showAmount();
    }

    private Account firstActiveAccount() {
        for (Account a : db.getAllAccountsList()) {
            return a;
        }
        return null;
    }

    private void pickAccount() {
        List<Account> accounts = db.getAllAccountsList();
        if (accounts.isEmpty()) {
            // Nothing to choose from, so choosing is not what is wanted: open the
            // screen that makes one. A list of no accounts, or a message saying
            // there are none, leaves somebody stuck on their first minute without
            // seeing that the missing thing is an account.
            startActivityForResult(new Intent(this, AccountActivity.class), NEW_ACCOUNT);
            return;
        }
        String[] titles = new String[accounts.size()];
        for (int i = 0; i < accounts.size(); i++) {
            titles[i] = accounts.get(i).title;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.account)
                .setItems(titles, (d, which) -> selectAccount(accounts.get(which).id))
                .show();
    }

    // --------------------------------------------------------------- category

    /**
     * The categories this person actually uses, most used first. A fixed list
     * would be somebody else's idea of what people spend money on.
     */
    private void buildCategoryShortcuts() {
        var row = (FlowLayout) findViewById(R.id.quick_categories);
        row.removeAllViews();
        chips.clear();

        float density = getResources().getDisplayMetrics().density;
        int pad = Math.round(14 * density);
        int height = Math.round(40 * density);

        String sql = "select c._id, c.title, c.icon, c.accent_color, count(*) n from transactions t"
                + " inner join category c on c._id = t.category_id"
                + " where t.is_template = 0 and t.parent_id = 0"
                + " and t.to_account_id = 0 and t.category_id > 0"
                + " group by c._id order by n desc limit " + SHORTCUTS;
        try (Cursor c = db.db().rawQuery(sql, null)) {
            while (c.moveToNext()) {
                final long id = c.getLong(0);
                final String title = c.getString(1);
                Button b = chip(title, pad, height);
                // The category's own symbol, in the colour it was given: at a
                // glance the shortcuts are told apart by shape and colour
                // rather than by reading four words of Italian.
                CategoryIcon icon = CategoryIcon.parse(c.getString(2));
                if (icon != null) {
                    b.setCompoundDrawablesRelativeWithIntrinsicBounds(icon.iconId, 0, 0, 0);
                    b.setCompoundDrawablePadding(Math.round(6 * density));
                    b.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(
                            CategoryIcons.colorOf(c.getString(3))));
                }
                b.setOnClickListener(v -> chooseCategory(id, title));
                chips.put(id, b);
                row.addView(b);
            }
        }

        // Last, and inside the same run of shortcuts: everything else the app
        // knows about, for the times the six on show are not the one wanted.
        Button more = chip(getString(R.string.other_categories), pad, height);
        more.setOnClickListener(v -> startActivityForResult(
                new Intent(this, CategorySelectorActivity.class), PICK_CATEGORY));
        row.addView(more);
    }

    /** One shortcut, drawn the way they all are. */
    private Button chip(String text, int pad, int height) {
        Button b = new Button(this);
        b.setText(text);
        b.setSingleLine(true);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTextColor(0xFFF4EFE4);
        b.setBackgroundResource(R.drawable.quick_chip);
        b.setStateListAnimator(null);
        b.setPadding(pad, 0, pad, 0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setLayoutParams(new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, height));
        return b;
    }

    private void chooseCategory(long id, String title) {
        categoryId = id;
        chosenView.setText(title);
        // Filled in means chosen. Picking from "other categories" leaves none
        // of the shortcuts filled, which is right: none of them is the one.
        for (var entry : chips.entrySet()) {
            entry.getValue().setSelected(entry.getKey() == id);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_CATEGORY && resultCode == RESULT_OK && data != null) {
            long id = data.getLongExtra(CategorySelectorActivity.SELECTED_CATEGORY_ID, -1);
            if (id > 0) {
                var category = db.getCategoryWithParent(id);
                chooseCategory(id, category != null ? category.title : "");
            }
        }
        if (requestCode == NEW_ACCOUNT) {
            // Whether or not one was made, pick it up: coming back to a screen still
            // saying "choose an account" after just making one would be absurd.
            selectAccount(rememberedAccount());
            buildCategoryShortcuts();
        }
    }

    // ------------------------------------------------------------------- save

    private void save() {
        if (account == null) {
            Toast.makeText(this, R.string.select_account, Toast.LENGTH_LONG).show();
            return;
        }
        if (cents == 0) {
            Toast.makeText(this, R.string.quick_no_amount, Toast.LENGTH_LONG).show();
            return;
        }
        Transaction t = new Transaction();
        t.fromAccountId = account.id;
        t.categoryId = categoryId;
        // Money leaving is negative; the toggle is the only thing that changes it.
        t.fromAmount = signButton.isChecked() ? cents : -cents;
        t.dateTime = System.currentTimeMillis();
        t.status = TransactionStatus.CL;
        db.insertOrUpdate(t);

        AccountWidget.updateWidgets(this);
        Toast.makeText(this, R.string.transaction_saved, Toast.LENGTH_SHORT).show();
        setResult(Activity.RESULT_OK);
        finish();
    }
}
