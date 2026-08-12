/*
 * This program is made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */
package tw.tib.financisto.activity;

import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.List;

import tw.tib.financisto.R;
import tw.tib.financisto.db.DatabaseAdapter;
import tw.tib.financisto.sync.SharedThings;
import tw.tib.financisto.utils.PinProtection;

/**
 * What goes across, chosen by the person it belongs to.
 * <p>
 * Nothing is shared until it is ticked here. Two people splitting the shopping
 * do not necessarily want the other to see the personal account, and copying
 * everything the moment a folder is chosen would make that decision for them.
 * <p>
 * Sharing an account shares what happens on it. Sharing a category or a name
 * only means the other phone has it to hand when a movement mentions it - a
 * category on its own tells nobody anything about money.
 */
public class SharePickerActivity extends AppCompatActivity {

    private DatabaseAdapter db;
    private final List<Row> rows = new ArrayList<>();

    private static class Row {
        final String kind;
        final SharedThings.Thing thing;
        final CheckBox box;

        Row(String kind, SharedThings.Thing thing, CheckBox box) {
            this.kind = kind;
            this.thing = thing;
            this.box = box;
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.share_picker);
        setSupportActionBar(findViewById(R.id.toolbar));
        setTitle(R.string.share_picker);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.share_base), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        db = new DatabaseAdapter(this);
        db.open();

        build();

        CheckBox all = findViewById(R.id.share_all);
        all.setChecked(!rows.isEmpty() && allTicked());
        all.setOnCheckedChangeListener((b, checked) -> {
            if (b.isPressed()) {
                tickAll(checked);
            }
        });
    }

    private void build() {
        LinearLayout list = findViewById(R.id.share_list);
        list.removeAllViews();
        rows.clear();
        for (String kind : SharedThings.KINDS) {
            List<SharedThings.Thing> things = SharedThings.list(db.db(), kind);
            if (things.isEmpty()) {
                continue;
            }
            TextView heading = (TextView) getLayoutInflater()
                    .inflate(R.layout.share_picker_heading, list, false);
            heading.setText(headingFor(kind));
            list.addView(heading);
            for (SharedThings.Thing thing : things) {
                View row = getLayoutInflater().inflate(R.layout.share_picker_row, list, false);
                CheckBox box = row.findViewById(R.id.share_box);
                ((TextView) row.findViewById(R.id.share_name)).setText(
                        thing.name.isEmpty() ? getString(R.string.no_title) : thing.name);
                box.setChecked(thing.shared);
                box.setOnCheckedChangeListener((b, checked) -> {
                    SharedThings.set(db.db(), kind, thing.uuid, checked);
                    // The tick at the top follows what is underneath it, or it
                    // would sit there claiming everything is chosen when one has
                    // just been taken off.
                    CheckBox all = findViewById(R.id.share_all);
                    if (all != null && all.isChecked() != allTicked()) {
                        all.setChecked(allTicked());
                    }
                });
                row.setOnClickListener(v -> box.setChecked(!box.isChecked()));
                list.addView(row);
                rows.add(new Row(kind, thing, box));
            }
        }
    }

    private void tickAll(boolean shared) {
        for (Row row : rows) {
            row.box.setChecked(shared);
        }
    }

    /** Whether the tick at the top should be on: everything below already is. */
    private boolean allTicked() {
        for (Row row : rows) {
            if (!row.box.isChecked()) {
                return false;
            }
        }
        return true;
    }

    private int headingFor(String kind) {
        if (SharedThings.ACCOUNT.equals(kind)) return R.string.accounts;
        if (SharedThings.CATEGORY.equals(kind)) return R.string.categories;
        if (SharedThings.PAYEE.equals(kind)) return R.string.payees;
        if (SharedThings.PROJECT.equals(kind)) return R.string.projects;
        return R.string.locations;
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
