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
import android.view.Gravity;
import android.view.View;
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
import java.util.List;

import tw.tib.financisto.R;
import tw.tib.financisto.db.DatabaseAdapter;
import tw.tib.financisto.model.Account;
import tw.tib.financisto.model.Currency;
import tw.tib.financisto.model.Transaction;
import tw.tib.financisto.model.TransactionStatus;
import tw.tib.financisto.utils.MyPreferences;
import tw.tib.financisto.utils.PinProtection;
import tw.tib.financisto.utils.Utils;

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
    /** How many category shortcuts fit before the row is more work than a list. */
    private static final int SHORTCUTS = 8;

    private DatabaseAdapter db;
    private Account account;
    private long categoryId = 0;
    private String categoryTitle;
    /** The amount as typed, in cents: digits accumulate from the right. */
    private long cents = 0;

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
        for (String key : keys) {
            Button b = new Button(this);
            b.setText("<".equals(key) ? "⌫" : key);
            b.setTextSize(22);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = 0;
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f);
            lp.rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f);
            lp.setMargins(3, 3, 3, 3);
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

    private void showAmount() {
        Currency currency = account != null ? account.currency : Currency.EMPTY;
        amountView.setText(Utils.amountToString(currency, cents));
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
            Toast.makeText(this, R.string.no_accounts, Toast.LENGTH_LONG).show();
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
        var row = (android.widget.LinearLayout) findViewById(R.id.quick_categories);
        row.removeAllViews();
        List<long[]> ids = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        String sql = "select c._id, c.title, count(*) n from transactions t"
                + " inner join category c on c._id = t.category_id"
                + " where t.is_template = 0 and t.category_id > 0"
                + " group by c._id order by n desc limit " + SHORTCUTS;
        try (Cursor c = db.db().rawQuery(sql, null)) {
            while (c.moveToNext()) {
                ids.add(new long[]{c.getLong(0)});
                titles.add(c.getString(1));
            }
        }
        for (int i = 0; i < ids.size(); i++) {
            final long id = ids.get(i)[0];
            final String title = titles.get(i);
            Button b = new Button(this);
            b.setText(title);
            b.setSingleLine(true);
            b.setOnClickListener(v -> chooseCategory(id, title));
            row.addView(b);
        }
        Button more = new Button(this);
        more.setText(R.string.other_categories);
        more.setOnClickListener(v -> startActivityForResult(
                new Intent(this, CategorySelectorActivity.class), 1));
        row.addView(more);
    }

    private void chooseCategory(long id, String title) {
        categoryId = id;
        categoryTitle = title;
        chosenView.setText(title);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1 && resultCode == RESULT_OK && data != null) {
            long id = data.getLongExtra(CategorySelectorActivity.SELECTED_CATEGORY_ID, -1);
            if (id > 0) {
                var category = db.getCategoryWithParent(id);
                chooseCategory(id, category != null ? category.title : "");
            }
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
