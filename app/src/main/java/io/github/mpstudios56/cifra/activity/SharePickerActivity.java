/*
 * This program is made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */
package io.github.mpstudios56.cifra.activity;

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

import io.github.mpstudios56.cifra.R;
import io.github.mpstudios56.cifra.db.DatabaseAdapter;
import io.github.mpstudios56.cifra.sync.SharedThings;
import io.github.mpstudios56.cifra.utils.PinProtection;

/**
 * What goes across, chosen by the person it belongs to.
 * <p>
 * Nothing is shared until it is ticked here. Two people splitting the shopping
 * do not necessarily want the other to see the personal account, and copying
 * everything the moment a folder is chosen would make that decision for them.
 * <p>
 * The same tick sits on the account itself, which is where somebody thinks
 * about what an account is for. This screen is the view of all of them at once,
 * for setting up the first time or checking later what is going across.
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

    /** What of the account's past goes across, asked here as well as in the account. */
    private void askWhatToShareOfThePast(SharedThings.Thing thing) {
        String[] choices = {
                getString(R.string.share_past_everything),
                getString(R.string.share_past_balance),
                getString(R.string.share_past_nothing),
        };
        final int[] picked = {0};
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.share_past_title)
                .setSingleChoiceItems(choices, 0, (d, which) -> picked[0] = which)
                .setPositiveButton(R.string.save, (d, w) -> {
                    int queued = io.github.mpstudios56.cifra.sync.SharingStart.apply(
                            db.db(), thing.id, thing.uuid, thing.name, picked[0]);
                    if (queued > 0) {
                        android.widget.Toast.makeText(this,
                                getString(R.string.share_past_queued, queued),
                                android.widget.Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .setCancelable(false)
                .show();
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
        // Only accounts. Everything a shared account's movements mention
        // follows on its own, and a list of four things to tick where one
        // will do is three questions too many.
        for (String kind : new String[]{SharedThings.ACCOUNT}) {
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

                // Under the name, the people it is held with. An account can be
                // ticked as shared and given to nobody yet, and that is worth
                // seeing here rather than after a round that delivers nothing.
                TextView who = row.findViewById(R.id.share_who);
                if (who != null && SharedThings.ACCOUNT.equals(kind)) {
                    String names = io.github.mpstudios56.cifra.sync.SharedWith.namesOf(db.db(), thing.uuid);
                    who.setVisibility(thing.shared ? View.VISIBLE : View.GONE);
                    who.setText(names.isEmpty()
                            ? getString(R.string.sharing_account_nobody) : names);
                }
                box.setChecked(thing.shared);
                box.setOnCheckedChangeListener((b, checked) -> {
                    boolean wasShared = SharedThings.isShared(db.db(), kind, thing.uuid);
                    SharedThings.set(db.db(), kind, thing.uuid, checked);
                    if (checked && !wasShared && SharedThings.ACCOUNT.equals(kind)) {
                        // Turned on from here, the movements already written
                        // were never queued: the change log is written when
                        // somebody makes a change, and these were made before
                        // the account was shared. So the account travelled, the
                        // categories travelled, and not one movement did.
                        askWhatToShareOfThePast(thing);
                    }
                    if (!checked) {
                        // Taking the tick off here stops the account for
                        // everybody, so the list of who it was held with goes
                        // with it: otherwise the account would still be named
                        // in the recap under people who no longer receive it.
                        io.github.mpstudios56.cifra.sync.SharedWith.set(db.db(), thing.uuid, null);
                    }
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
