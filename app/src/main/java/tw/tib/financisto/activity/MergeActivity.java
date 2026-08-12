/*
 * This program is made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */
package tw.tib.financisto.activity;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.List;

import tw.tib.financisto.R;
import tw.tib.financisto.db.DatabaseAdapter;
import tw.tib.financisto.db.DatabaseHelper;
import tw.tib.financisto.sync.Merger;
import tw.tib.financisto.utils.PinProtection;

/**
 * Two labels that mean one thing, put back together.
 * <p>
 * The screen proposes and somebody decides, the way a phone book proposes
 * duplicate contacts. Written-alike pairs come first: those are the ones people
 * say yes to without thinking, and the doubtful ones should not be in the way.
 * <p>
 * Nothing here touches the money, and the screen says so - "unisci" over a list
 * of spending categories reads like something that might add two columns
 * together, and somebody about to press it deserves to know that it will not.
 */
public class MergeActivity extends AppCompatActivity {

    private DatabaseAdapter db;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.merge);
        setSupportActionBar(findViewById(R.id.toolbar));
        setTitle(R.string.merge);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.merge_base), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        db = new DatabaseAdapter(this);
        db.open();
        build();
    }

    private void build() {
        LinearLayout list = findViewById(R.id.merge_list);
        list.removeAllViews();
        List<Merger.Pair> pairs = Merger.candidates(db.db());
        findViewById(android.R.id.empty).setVisibility(pairs.isEmpty() ? View.VISIBLE : View.GONE);

        for (Merger.Pair pair : pairs) {
            View row = getLayoutInflater().inflate(R.layout.merge_row, list, false);
            ((TextView) row.findViewById(R.id.merge_kind)).setText(kindOf(pair.kind));
            ((TextView) row.findViewById(R.id.merge_left)).setText(
                    getString(R.string.merge_side, pair.leftName, pair.leftUses));
            ((TextView) row.findViewById(R.id.merge_right)).setText(
                    getString(R.string.merge_side, pair.rightName, pair.rightUses));
            row.findViewById(R.id.merge_maybe).setVisibility(
                    pair.identical ? View.GONE : View.VISIBLE);
            row.findViewById(R.id.merge_do).setOnClickListener(v -> ask(pair));
            list.addView(row);
        }
    }

    /** Which of the two names survives. When they read alike it makes no odds. */
    private void ask(Merger.Pair pair) {
        if (pair.identical) {
            // Same name either way: keep the one already used more, so the
            // fewest movements change hands.
            merge(pair, pair.leftUses >= pair.rightUses);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.merge_which)
                .setItems(new CharSequence[]{pair.leftName, pair.rightName},
                        (d, which) -> merge(pair, which == 0))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void merge(Merger.Pair pair, boolean keepLeft) {
        long keeper = keepLeft ? pair.leftId : pair.rightId;
        long loser = keepLeft ? pair.rightId : pair.leftId;
        int moved = Merger.merge(db, pair.kind, keeper, loser);
        if (moved < 0) {
            Toast.makeText(this, R.string.merge_has_children, Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, getString(R.string.merge_done, moved), Toast.LENGTH_SHORT).show();
        build();
    }

    private int kindOf(String kind) {
        if (DatabaseHelper.CATEGORY_TABLE.equals(kind)) return R.string.category;
        if (DatabaseHelper.PAYEE_TABLE.equals(kind)) return R.string.payee;
        if (DatabaseHelper.PROJECT_TABLE.equals(kind)) return R.string.project;
        return R.string.location;
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
