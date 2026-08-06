/*
 * This program is made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */
package tw.tib.financisto.activity;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.ListFragment;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import tw.tib.financisto.R;
import tw.tib.financisto.db.DatabaseAdapter;
import tw.tib.financisto.model.Account;
import tw.tib.financisto.model.Category;
import tw.tib.financisto.utils.TransactionDraft;
import tw.tib.financisto.utils.Utils;

/**
 * The entries left half filled. Shown as a tab only while there are any, so it
 * costs nothing on the screen once they have all been dealt with.
 */
public class DraftListFragment extends ListFragment {

    private DatabaseAdapter db;
    private List<TransactionDraft.Entry> drafts = new ArrayList<>();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new DatabaseAdapter(getContext());
        db.open();
    }

    @Override
    public void onDestroy() {
        if (db != null) {
            db.close();
        }
        super.onDestroy();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setEmptyText(getString(R.string.no_drafts));
        registerForContextMenu(getListView());
    }

    @Override
    public void onResume() {
        super.onResume();
        reload();
    }

    /** Rebuilds the list from storage; drafts change behind this screen's back. */
    public void reload() {
        if (getContext() == null) {
            return;
        }
        drafts = TransactionDraft.list(getContext());
        List<String[]> rows = new ArrayList<>();
        for (TransactionDraft.Entry entry : drafts) {
            rows.add(new String[]{describe(entry), whenAndWhere(entry)});
        }
        setListAdapter(new DraftAdapter(getContext(), rows));
    }

    /** What was being entered: the amount, and whatever names it. */
    private String describe(TransactionDraft.Entry entry) {
        StringBuilder sb = new StringBuilder();
        Account account = db.getAccount(entry.transaction.fromAccountId);
        if (account != null && account.currency != null) {
            sb.append(Utils.amountToString(account.currency, entry.transaction.fromAmount));
        } else {
            sb.append(entry.transaction.fromAmount / 100.0);
        }
        String name = nameFor(entry);
        if (name != null) {
            sb.append("  ").append(name);
        }
        return sb.toString();
    }

    private String nameFor(TransactionDraft.Entry entry) {
        if (entry.transaction.categoryId > 0) {
            Category category = db.getCategoryWithParent(entry.transaction.categoryId);
            if (category != null && category.title != null) {
                return category.title;
            }
        }
        if (entry.transaction.note != null && !entry.transaction.note.trim().isEmpty()) {
            return entry.transaction.note.trim();
        }
        return null;
    }

    private String whenAndWhere(TransactionDraft.Entry entry) {
        DateFormat df = android.text.format.DateFormat.getDateFormat(getContext());
        DateFormat tf = android.text.format.DateFormat.getTimeFormat(getContext());
        Date left = new Date(entry.id);
        StringBuilder sb = new StringBuilder(getString(R.string.draft_left_at,
                df.format(left) + " " + tf.format(left)));
        Account account = db.getAccount(entry.transaction.fromAccountId);
        if (account != null) {
            sb.append(" · ").append(account.title);
        }
        return sb.toString();
    }

    @Override
    public void onListItemClick(@NonNull ListView l, @NonNull View v, int position, long id) {
        TransactionDraft.Entry entry = drafts.get(position);
        Intent intent = new Intent(getContext(), TransactionActivity.class);
        intent.putExtra(AbstractTransactionActivity.DRAFT_ID_EXTRA, entry.id);
        startActivity(intent);
    }

    @Override
    public void onCreateContextMenu(@NonNull android.view.ContextMenu menu, @NonNull View v,
                                    android.view.ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        android.widget.AdapterView.AdapterContextMenuInfo info =
                (android.widget.AdapterView.AdapterContextMenuInfo) menuInfo;
        TransactionDraft.Entry entry = drafts.get(info.position);
        menu.setHeaderTitle(R.string.drafts);
        menu.add(R.string.delete).setOnMenuItemClickListener(item -> {
            confirmDelete(entry);
            return true;
        });
    }

    private void confirmDelete(TransactionDraft.Entry entry) {
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.delete_draft_title)
                .setMessage(R.string.delete_draft_message)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    TransactionDraft.delete(getContext(), entry.id);
                    reload();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static class DraftAdapter extends ArrayAdapter<String[]> {
        DraftAdapter(Context context, List<String[]> rows) {
            super(context, android.R.layout.simple_list_item_2, android.R.id.text1, rows);
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            View view = convertView != null ? convertView
                    : LayoutInflater.from(getContext())
                    .inflate(android.R.layout.simple_list_item_2, parent, false);
            String[] row = getItem(position);
            ((TextView) view.findViewById(android.R.id.text1)).setText(row[0]);
            ((TextView) view.findViewById(android.R.id.text2)).setText(row[1]);
            return view;
        }
    }
}
