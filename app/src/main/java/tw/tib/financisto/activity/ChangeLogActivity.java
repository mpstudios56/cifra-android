/*
 * This program is made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */
package tw.tib.financisto.activity;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import tw.tib.financisto.R;
import tw.tib.financisto.db.ChangeLog;
import tw.tib.financisto.db.DatabaseAdapter;
import tw.tib.financisto.utils.MyPreferences;
import tw.tib.financisto.utils.PinProtection;

/**
 * Who changed what, and when.
 * <p>
 * Reading only. Nothing here can be undone from here - what was deleted is in
 * the bin, and what was changed is in the ledger. This says who did it, which
 * is the one thing neither of those can say.
 */
public class ChangeLogActivity extends AppCompatActivity {

    /** Enough to answer "what happened this week"; beyond that nobody scrolls. */
    private static final int MOST = 300;

    private DatabaseAdapter db;
    private Adapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.change_log);
        setSupportActionBar(findViewById(R.id.toolbar));
        setTitle(R.string.change_log);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.change_log_base), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        db = new DatabaseAdapter(this);
        db.open();

        ListView list = findViewById(android.R.id.list);
        list.setEmptyView(findViewById(android.R.id.empty));
        adapter = new Adapter();
        adapter.entries = ChangeLog.list(db.db(), MOST);
        list.setAdapter(adapter);

        ((TextView) findViewById(R.id.change_log_note)).setText(note());
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        menu.add(0, 1, 0, R.string.change_log_clear);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == 1) {
            askTwice();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Emptying the record, asked twice.
     * <p>
     * It is not a list of past events to tidy away: it is the memory of what
     * has already been exchanged. Emptied, the other phone's entries look new
     * again and come back round, and this phone's own changes - the ones not
     * yet collected - are gone for good. Two questions, because after this
     * there is nothing to undo it with.
     */
    private void askTwice() {
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.change_log_clear)
                .setMessage(R.string.change_log_clear_why)
                .setPositiveButton(R.string.change_log_clear_go, (d, w) -> new android.app.AlertDialog.Builder(this)
                        .setTitle(R.string.change_log_clear_sure)
                        .setMessage(R.string.change_log_clear_sure_why)
                        .setPositiveButton(R.string.change_log_clear_now, (d2, w2) -> clear())
                        .setNegativeButton(R.string.cancel, null)
                        .show())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void clear() {
        int gone = ChangeLog.clear(db.db());
        adapter.entries = ChangeLog.list(db.db(), MOST);
        adapter.notifyDataSetChanged();
        android.widget.Toast.makeText(this,
                getString(R.string.change_log_cleared, gone),
                android.widget.Toast.LENGTH_LONG).show();
    }

    /** Says whose record this is, or that nobody has said. */
    private String note() {
        String me = MyPreferences.getSyncAuthor();
        return me.isEmpty()
                ? getString(R.string.change_log_note_unsigned)
                : getString(R.string.change_log_note, me);
    }

    private class Adapter extends BaseAdapter {

        List<ChangeLog.Entry> entries = new ArrayList<>();

        @Override public int getCount() { return entries.size(); }
        @Override public Object getItem(int position) { return entries.get(position); }
        @Override public long getItemId(int position) { return entries.get(position).id; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(ChangeLogActivity.this)
                        .inflate(R.layout.change_log_item, parent, false);
            }
            ChangeLog.Entry e = entries.get(position);
            ((TextView) convertView.findViewById(R.id.change_title)).setText(
                    e.title == null || e.title.isEmpty() ? getString(R.string.transaction) : e.title);
            ((TextView) convertView.findViewById(R.id.change_what)).setText(what(e));
            ((TextView) convertView.findViewById(R.id.change_who)).setText(who(e));
            return convertView;
        }
    }

    private String what(ChangeLog.Entry e) {
        int word;
        switch (e.operation) {
            case ChangeLog.INSERT: word = R.string.change_added; break;
            case ChangeLog.DELETE: word = R.string.change_deleted; break;
            default: word = R.string.change_edited; break;
        }
        return getString(word);
    }

    /** "Marcello, 12/08/26 10:24" - and just the date when nobody signed it. */
    private String who(ChangeLog.Entry e) {
        String when = android.text.format.DateFormat.getDateFormat(this)
                .format(new Date(e.madeOn))
                + " " + android.text.format.DateFormat.getTimeFormat(this)
                .format(new Date(e.madeOn));
        String author = e.author == null ? "" : e.author.trim();
        return author.isEmpty() ? when : author + " · " + when;
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
