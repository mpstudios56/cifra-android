/*
 * This program is made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */
package tw.tib.financisto.activity;

import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Date;
import java.util.List;

import tw.tib.financisto.R;
import tw.tib.financisto.db.DatabaseAdapter;
import tw.tib.financisto.sync.Duplicates;
import tw.tib.financisto.utils.Identity;
import tw.tib.financisto.utils.PinProtection;

/**
 * Movements that might be one payment written down twice.
 * <p>
 * The screen asks and never decides. Two coffees from the same machine, a bill
 * split down the middle, and the same dinner entered by both people look exactly
 * alike in the rows: the difference is in what happened, and only the two people
 * know that.
 * <p>
 * Answering "two different payments" settles the pair for good. Being asked the
 * same question twice, after already saying they are different, is being told
 * one was wrong.
 */
public class DuplicatesActivity extends AppCompatActivity {

    private DatabaseAdapter db;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.duplicates);
        setSupportActionBar(findViewById(R.id.toolbar));
        setTitle(R.string.duplicates);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.duplicates_base), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        db = new DatabaseAdapter(this);
        db.open();
        build();
    }

    private void build() {
        LinearLayout list = findViewById(R.id.duplicates_list);
        list.removeAllViews();
        List<Duplicates.Pair> pairs = Duplicates.list(db.db());
        findViewById(android.R.id.empty).setVisibility(pairs.isEmpty() ? View.VISIBLE : View.GONE);

        for (Duplicates.Pair pair : pairs) {
            View row = getLayoutInflater().inflate(R.layout.duplicates_row, list, false);
            describe(row, pair);
            row.findViewById(R.id.duplicate_different).setOnClickListener(v -> {
                Duplicates.settle(db.db(), pair.id);
                build();
            });
            row.findViewById(R.id.duplicate_same).setOnClickListener(v -> askToRemove(pair));
            list.addView(row);
        }
    }

    /** What the two are, in one line each: what, how much, who and when. */
    private void describe(View row, Duplicates.Pair pair) {
        String[] a = readOne(pair.uuidA);
        String[] b = readOne(pair.uuidB);
        ((TextView) row.findViewById(R.id.duplicate_title)).setText(a[0]);
        ((TextView) row.findViewById(R.id.duplicate_amount)).setText(a[1]);
        ((TextView) row.findViewById(R.id.duplicate_a)).setText(a[2]);
        ((TextView) row.findViewById(R.id.duplicate_b)).setText(b[2]);
    }

    /** Title, amount, and who wrote it when. */
    private String[] readOne(String uuid) {
        String sql = "select t.datetime, t.from_amount, t.created_by,"
                + " coalesce(c.title, ''), coalesce(p.title, '')"
                + " from transactions t"
                + " left join category c on c._id = t.category_id"
                + " left join payee p on p._id = t.payee_id"
                + " where t.uuid = ?";
        try (Cursor c = db.db().rawQuery(sql, new String[]{uuid})) {
            if (!c.moveToFirst()) {
                return new String[]{"", "", ""};
            }
            long when = c.getLong(0);
            long amount = c.getLong(1);
            String device = c.getString(2);
            String category = c.getString(3);
            String payee = c.getString(4);

            String title = category.isEmpty() ? payee : category;
            if (!payee.isEmpty() && !category.isEmpty()) {
                title = category + " · " + payee;
            }
            String money = tw.tib.financisto.utils.Utils.amountToString(
                    tw.tib.financisto.utils.CurrencyCache.getHomeCurrency(), amount);
            String who = Identity.isMine(device)
                    ? Identity.mine(this).name : Identity.theirs(this).name;
            if (who == null || who.trim().isEmpty()) {
                who = getString(Identity.isMine(device)
                        ? R.string.sharing_me : R.string.sharing_them);
            }
            String time = android.text.format.DateFormat.getTimeFormat(this)
                    .format(new Date(when));
            return new String[]{title, money, getString(R.string.duplicate_written, who, time)};
        } catch (Exception e) {
            return new String[]{"", "", ""};
        }
    }

    /** The later of the two goes; the first one written stays. */
    private void askToRemove(Duplicates.Pair pair) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.duplicate_same)
                .setMessage(R.string.duplicate_same_confirm)
                .setPositiveButton(R.string.duplicate_same, (d, which) -> {
                    long later = laterOf(pair);
                    if (later > 0) {
                        db.deleteTransaction(later);
                    }
                    Duplicates.settle(db.db(), pair.id);
                    db.recalculateAccountsBalances();
                    db.rebuildRunningBalances();
                    Toast.makeText(this, R.string.duplicate_removed, Toast.LENGTH_SHORT).show();
                    build();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private long laterOf(Duplicates.Pair pair) {
        long a = Duplicates.idOf(db.db(), pair.uuidA);
        long b = Duplicates.idOf(db.db(), pair.uuidB);
        return whenOf(b) >= whenOf(a) ? b : a;
    }

    private long whenOf(long id) {
        try (Cursor c = db.db().rawQuery(
                "select datetime from transactions where _id=?",
                new String[]{String.valueOf(id)})) {
            return c.moveToFirst() ? c.getLong(0) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onDestroy() {
        db.close();
        super.onDestroy();
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
}
