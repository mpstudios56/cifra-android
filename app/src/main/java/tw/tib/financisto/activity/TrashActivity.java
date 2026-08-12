/*
 * This program is made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */
package tw.tib.financisto.activity;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Date;
import java.util.List;

import tw.tib.financisto.R;
import tw.tib.financisto.datetime.DateUtils;
import tw.tib.financisto.db.DatabaseAdapter;
import tw.tib.financisto.db.DatabaseHelper;
import tw.tib.financisto.db.Trash;
import tw.tib.financisto.utils.MyPreferences;
import tw.tib.financisto.utils.PinProtection;

/**
 * What has been deleted, while it can still be brought back.
 * <p>
 * Everything here is already gone from the accounts: the balances, the reports
 * and the budgets were worked out without it the moment it was deleted. Putting
 * something back therefore has to make the figures again, which is why
 * restoring is slower than deleting was.
 */
public class TrashActivity extends AppCompatActivity {

    private DatabaseAdapter db;
    private ListView list;
    private TrashAdapter adapter;
    private TextView note;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.trash);
        setSupportActionBar(findViewById(R.id.toolbar));
        setTitle(R.string.trash);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.trash_base), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            // Left and right as well: held sideways the navigation bar is down
            // one side of the screen, and padding only the top and bottom slid
            // the content under it.
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        db = new DatabaseAdapter(this);
        db.open();

        note = findViewById(R.id.trash_note);
        // android.R.id.list, not one of ours: the layout uses the framework id
        // so the empty view is wired up the way a list screen normally is.
        list = findViewById(android.R.id.list);
        list.setEmptyView(findViewById(android.R.id.empty));
        adapter = new TrashAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) ->
                askWhatToDo(adapter.items.get(position)));

        Button emptyNow = findViewById(R.id.trash_empty_now);
        emptyNow.setOnClickListener(v -> askToEmpty());

        refresh();
    }

    private void refresh() {
        // Anything past its keeping time goes now, so what is on screen is what
        // is really still recoverable.
        Trash.purge(db.db(), MyPreferences.getTrashDays());
        adapter.items = Trash.list(db.db());
        adapter.notifyDataSetChanged();

        int days = MyPreferences.getTrashDays();
        note.setText(days > 0
                ? getString(R.string.trash_note_days, days)
                : getString(R.string.trash_note_forever));
        findViewById(R.id.trash_empty_now).setVisibility(
                adapter.items.isEmpty() ? View.GONE : View.VISIBLE);
    }

    // ------------------------------------------------------------------ actions

    private void askWhatToDo(Trash.Item item) {
        new AlertDialog.Builder(this)
                .setTitle(item.title == null || item.title.isEmpty()
                        ? getString(R.string.trash) : item.title)
                .setItems(new CharSequence[]{
                        getString(R.string.trash_restore),
                        getString(R.string.trash_forget)}, (dialog, which) -> {
                    if (which == 0) {
                        restore(item);
                    } else {
                        askToForget(item);
                    }
                })
                .show();
    }

    private void restore(Trash.Item item) {
        Trash.Restored restored = Trash.restore(db.db(), item.id);
        if (restored.outcome == Trash.Outcome.NO_ACCOUNT) {
            // Left in the bin on purpose: recreating the account puts it back
            // within reach, and restoring it onto nothing would not.
            new AlertDialog.Builder(this)
                    .setTitle(R.string.trash_restore_failed)
                    .setMessage(R.string.trash_no_account)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }
        if (restored.outcome != Trash.Outcome.DONE) {
            Toast.makeText(this, R.string.trash_restore_failed, Toast.LENGTH_LONG).show();
            refresh();
            return;
        }
        String entity = restored.entity;
        if (DatabaseHelper.TRANSACTION_TABLE.equals(entity)) {
            // The money moved back, so the figures have to be made again. Doing
            // it here rather than trusting an increment: a restored transaction
            // is rare, and a balance quietly out by one entry is not worth the
            // speed.
            db.recalculateAccountsBalances();
            db.rebuildRunningBalances();
        }
        // Said plainly when something it pointed at had gone: the movement is
        // back, but not quite as it was, and that is worth knowing.
        Toast.makeText(this, restored.lostSomething
                ? R.string.trash_restored_partly : R.string.trash_restored,
                restored.lostSomething ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
        refresh();
    }

    private void askToForget(Trash.Item item) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.trash_forget)
                .setMessage(R.string.trash_forget_confirm)
                .setPositiveButton(R.string.trash_forget, (d, which) -> {
                    Trash.forget(db.db(), item.id);
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void askToEmpty() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.trash_empty_now)
                // A count, so "1 item" is not written as though it were several
                .setMessage(getResources().getQuantityString(
                        R.plurals.trash_empty_confirm,
                        adapter.items.size(), adapter.items.size()))
                .setPositiveButton(R.string.trash_empty_now, (d, which) -> {
                    Trash.empty(db.db());
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // ------------------------------------------------------------------ the list

    private class TrashAdapter extends BaseAdapter {

        List<Trash.Item> items = new java.util.ArrayList<>();

        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return items.get(position).id; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(TrashActivity.this)
                        .inflate(R.layout.trash_list_item, parent, false);
            }
            Trash.Item item = items.get(position);
            TextView title = convertView.findViewById(R.id.trash_title);
            TextView subtitle = convertView.findViewById(R.id.trash_subtitle);
            TextView when = convertView.findViewById(R.id.trash_when);

            title.setText(item.title == null || item.title.isEmpty()
                    ? getString(nameOf(item.entity)) : item.title);
            subtitle.setText(item.subtitle);
            subtitle.setVisibility(item.subtitle == null || item.subtitle.isEmpty()
                    ? View.GONE : View.VISIBLE);
            when.setText(getString(R.string.trash_deleted_on,
                    DateUtils.getShortDateFormat(TrashActivity.this)
                            .format(new Date(item.deletedOn))));
            return convertView;
        }
    }

    /** What to call a row whose own name was empty. */
    private int nameOf(String entity) {
        if (DatabaseHelper.TRANSACTION_TABLE.equals(entity)) {
            return R.string.transaction;
        }
        if (DatabaseHelper.CATEGORY_TABLE.equals(entity)) {
            return R.string.category;
        }
        if (DatabaseHelper.BUDGET_TABLE.equals(entity)) {
            return R.string.budget;
        }
        return R.string.trash;
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
