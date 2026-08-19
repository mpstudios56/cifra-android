package tw.tib.financisto.adapter;

import android.content.Context;
import android.database.Cursor;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;

import tw.tib.financisto.R;
import tw.tib.financisto.model.Account;
import tw.tib.financisto.utils.Utils;
import tw.tib.orb.EntityManager;

/**
 * The accounts as a movement is offered them.
 * <p>
 * The handful used every day stand at the top under a heading of their own,
 * with the rest below under theirs; within each group the order is the one the
 * accounts screen was given. Where no account has been marked as an everyday
 * one there are no headings at all, and the list reads as it always did.
 */
public class AccountPickerAdapter extends ResourceCursorAdapter {

    private final Utils u;
    private final boolean showBalance;
    /** Whether anybody has marked an everyday account: if not, no headings. */
    private boolean anyMain;

    public AccountPickerAdapter(Context context, Cursor c, boolean showBalance) {
        super(context, R.layout.account_picker_item, c);
        this.u = new Utils(context);
        this.showBalance = showBalance;
        countMain(c);
    }

    @Override
    public Cursor swapCursor(Cursor c) {
        countMain(c);
        return super.swapCursor(c);
    }

    private void countMain(Cursor c) {
        anyMain = false;
        if (c == null || c.isClosed() || c.getCount() == 0) {
            return;
        }
        int was = c.getPosition();
        if (c.moveToFirst()) {
            anyMain = EntityManager.loadFromCursor(c, Account.class).isMain;
        }
        c.moveToPosition(was);
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        View view = super.newView(context, cursor, parent);
        view.setTag(new ViewHolder(view));
        return view;
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        ViewHolder vh = (ViewHolder) view.getTag();
        Account a = EntityManager.loadFromCursor(cursor, Account.class);
        if (showBalance) {
            u.setAccountTitleBalance(a, vh.title, vh.balance, vh.limit);
        } else {
            vh.title.setText(a.title);
            vh.balance.setVisibility(View.GONE);
            vh.limit.setVisibility(View.GONE);
        }

        // A heading opens a group: above the first row of all, and above the
        // first row that is no longer an everyday account.
        boolean opensGroup = false;
        String label = null;
        if (anyMain) {
            int position = cursor.getPosition();
            if (position == 0) {
                opensGroup = true;
                label = context.getString(R.string.main_accounts);
            } else if (cursor.moveToPosition(position - 1)) {
                boolean previousWasMain = EntityManager.loadFromCursor(cursor, Account.class).isMain;
                cursor.moveToPosition(position);
                if (previousWasMain && !a.isMain) {
                    opensGroup = true;
                    label = context.getString(R.string.other_accounts);
                }
            }
        }
        vh.section.setVisibility(opensGroup ? View.VISIBLE : View.GONE);
        if (opensGroup) {
            vh.sectionLabel.setText(label);
        }
    }

    static class ViewHolder {
        final TextView title;
        final TextView balance;
        final TextView limit;
        final View section;
        final TextView sectionLabel;

        ViewHolder(View v) {
            title = v.findViewById(R.id.data);
            balance = v.findViewById(R.id.balance);
            limit = v.findViewById(R.id.limit);
            section = v.findViewById(R.id.section);
            sectionLabel = v.findViewById(R.id.section_label);
        }
    }
}
