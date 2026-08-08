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
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.ViewCompat;
import androidx.core.graphics.Insets;
import android.view.ViewGroup;
import android.widget.AbsListView;
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

import tw.tib.financisto.Application;
import tw.tib.financisto.R;
import tw.tib.financisto.db.DatabaseAdapter;
import tw.tib.financisto.model.Account;
import tw.tib.financisto.model.Category;
import tw.tib.financisto.utils.PicturesUtil;
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
        // No bar of its own at the top, and the tab strip that used to sit there
        // has moved to the bottom, so the first row would start under the clock.
        ViewCompat.setOnApplyWindowInsetsListener(getListView(), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, insets.top, 0, 0);
            ((ViewGroup) v).setClipToPadding(true);
            return WindowInsetsCompat.CONSUMED;
        });
        setEmptyText(getString(R.string.no_drafts));
        enableMultiSelect();
    }

    /**
     * Long press starts a selection, and further taps add to it, so several drafts
     * can be cleared in one go rather than one confirmation at a time.
     */
    private void enableMultiSelect() {
        ListView list = getListView();
        list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
        list.setMultiChoiceModeListener(new AbsListView.MultiChoiceModeListener() {
            @Override
            public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked) {
                mode.setTitle(getString(R.string.drafts_selected,
                        getListView().getCheckedItemCount()));
            }

            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                mode.getMenuInflater().inflate(R.menu.draft_list_menu, menu);
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                int id = item.getItemId();
                if (id == R.id.delete_selected_drafts) {
                    confirmDeleteSelected(mode);
                    return true;
                }
                if (id == R.id.select_all_drafts) {
                    for (int i = 0; i < getListView().getCount(); i++) {
                        getListView().setItemChecked(i, true);
                    }
                    return true;
                }
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
            }
        });
    }

    private void confirmDeleteSelected(ActionMode mode) {
        List<TransactionDraft.Entry> selected = new ArrayList<>();
        SparseBooleanArray checked = getListView().getCheckedItemPositions();
        for (int i = 0; i < checked.size(); i++) {
            if (checked.valueAt(i) && checked.keyAt(i) < drafts.size()) {
                selected.add(drafts.get(checked.keyAt(i)));
            }
        }
        if (selected.isEmpty()) {
            mode.finish();
            return;
        }
        new AlertDialog.Builder(getContext())
                .setTitle(getResources().getQuantityString(
                        R.plurals.delete_drafts_title, selected.size(), selected.size()))
                .setMessage(R.string.delete_draft_message)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    for (TransactionDraft.Entry entry : selected) {
                        // The photo was written when it was attached and only the
                        // draft pointed at it, so it goes with the draft.
                        String picture = entry.transaction.attachedPicture;
                        if (picture != null) {
                            Application.getExecutor().execute(
                                    () -> PicturesUtil.deletePictureFile(getContext(), picture));
                        }
                        TransactionDraft.delete(getContext(), entry.id);
                    }
                    mode.finish();
                    reload();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
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
        // Clearing the last draft takes this tab away with it, and nothing else
        // would notice: this list lives inside the main screen, which is not
        // resumed while its own tab is being used.
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).refreshTabs();
        }
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
        // Both ends of a transfer, or the row reads as an ordinary entry on the
        // account the money was leaving.
        if (entry.transaction.toAccountId > 0) {
            Account to = db.getAccount(entry.transaction.toAccountId);
            if (to != null) {
                sb.append(" → ").append(to.title);
            }
        }
        return sb.toString();
    }

    @Override
    public void onListItemClick(@NonNull ListView l, @NonNull View v, int position, long id) {
        TransactionDraft.Entry entry = drafts.get(position);
        // A transfer has to reopen in the transfer screen: the ordinary one has no
        // second account to put the other end of it in.
        boolean isTransfer = entry.transaction.toAccountId > 0;
        Intent intent = new Intent(getContext(),
                isTransfer ? TransferActivity.class : TransactionActivity.class);
        intent.putExtra(AbstractTransactionActivity.DRAFT_ID_EXTRA, entry.id);
        startActivity(intent);
    }

    private static class DraftAdapter extends ArrayAdapter<String[]> {
        DraftAdapter(Context context, List<String[]> rows) {
            // The activated variant shows which rows are picked during a selection.
            super(context, android.R.layout.simple_list_item_activated_2, android.R.id.text1, rows);
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            View view = convertView != null ? convertView
                    : LayoutInflater.from(getContext())
                    .inflate(android.R.layout.simple_list_item_activated_2, parent, false);
            String[] row = getItem(position);
            ((TextView) view.findViewById(android.R.id.text1)).setText(row[0]);
            ((TextView) view.findViewById(android.R.id.text2)).setText(row[1]);
            return view;
        }
    }
}
