package io.github.mpstudios56.cifra.adapter;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.DateFormat;
import java.util.Date;

import io.github.mpstudios56.cifra.R;
import io.github.mpstudios56.cifra.adapter.dragndrop.ItemTouchHelperAdapter;
import io.github.mpstudios56.cifra.adapter.dragndrop.ItemTouchHelperViewHolder;
import io.github.mpstudios56.cifra.datetime.DateUtils;
import io.github.mpstudios56.cifra.db.DatabaseHelper;
import io.github.mpstudios56.cifra.model.Account;
import io.github.mpstudios56.cifra.model.AccountSeparator;
import io.github.mpstudios56.cifra.model.AccountType;
import io.github.mpstudios56.cifra.model.CardIssuer;
import io.github.mpstudios56.cifra.model.ElectronicPaymentType;
import io.github.mpstudios56.cifra.utils.MyPreferences;
import io.github.mpstudios56.cifra.utils.AccountIcon;
import io.github.mpstudios56.cifra.utils.Utils;
import io.github.mpstudios56.cifra.orb.EntityManager;

public class AccountRecyclerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder>
        implements ItemTouchHelperAdapter
{
    /** An account. */
    private static final int ROW_ACCOUNT = 0;
    /** A heading gathering the accounts under it. */
    private static final int ROW_SEPARATOR = 1;
    public static final String TAG = "AccountRecyclerAdapter";

    protected final Context context;
    protected final Cursor cursor;

    private long created = 0;
    private final Utils u;
    private DateFormat df;
    private MyPreferences.AccountListDateType accountListDateType;
    private boolean blurBalances;
    private boolean showSortOrder;
    private View.OnClickListener onClickListener = null;
    private View.OnLongClickListener onLongClickListener = null;
    /** Which accounts are held together with somebody, worked out once. */
    private java.util.Set<Long> sharedAccounts = java.util.Collections.emptySet();
    /** And in whose colour, by account. */
    private java.util.Map<Long, java.util.List<Integer>> sharedColours =
            java.util.Collections.emptyMap();

    public void setSharedAccounts(java.util.Set<Long> ids) {
        this.sharedAccounts = ids == null ? java.util.Collections.emptySet() : ids;
    }

    public void setSharedColours(java.util.Map<Long, java.util.List<Integer>> colours) {
        this.sharedColours = colours == null ? java.util.Collections.emptyMap() : colours;
    }

    /**
     * What the list shows, in order.
     * <p>
     * An entry is either a place in the cursor - an account - or a heading. The
     * accounts of a folded group are not in here at all: folding is done by
     * building the list without them, which is simpler to be sure of than
     * hiding rows that are still counted.
     */
    private final java.util.List<Object> rows = new java.util.ArrayList<>();

    /** Headings by the account each one stands above. */
    private java.util.Map<Long, AccountSeparator> separators = java.util.Collections.emptyMap();

    private OnSeparatorAction onSeparatorAction;

    /** What the screen wants to happen when a heading is touched. */
    public interface OnSeparatorAction {
        void onSeparatorClicked(AccountSeparator separator);

        void onSeparatorHeldDown(AccountSeparator separator);
    }

    public void setOnSeparatorAction(OnSeparatorAction action) {
        this.onSeparatorAction = action;
    }

    /**
     * Hands over the headings and works out what the list is made of.
     * <p>
     * The cursor is walked once: before each account, the heading fastened to
     * it if there is one; and while a heading is folded, the accounts that
     * follow it are left out until the next heading opens the list again.
     */
    public void setSeparators(java.util.Map<Long, AccountSeparator> separators) {
        this.separators = separators != null ? separators : java.util.Collections.emptyMap();
        rebuildRows();
    }

    private void rebuildRows() {
        rows.clear();
        AccountSeparator folding = null;
        int counted = 0;
        for (int i = 0; i < cursor.getCount(); i++) {
            cursor.moveToPosition(i);
            long accountId = cursor.getLong(DatabaseHelper.BlotterColumns._id.ordinal());
            AccountSeparator heading = separators.get(accountId);
            if (heading != null) {
                // A heading closes the group before it and opens its own.
                if (folding != null) {
                    folding.hidden = counted;
                }
                rows.add(heading);
                folding = heading.folded ? heading : null;
                counted = 0;
            }
            if (folding != null) {
                counted++;
                continue;
            }
            rows.add(i);
        }
        if (folding != null) {
            folding.hidden = counted;
        }
        notifyDataSetChanged();
    }

    public AccountRecyclerAdapter(Context context, Cursor c, boolean showSortOrder, View.OnClickListener onClickListener,
                                  View.OnLongClickListener onLongClickListener) {
        this.u = new Utils(context);
        this.df = DateUtils.getShortDateFormat(context);
        this.accountListDateType = MyPreferences.getAccountListDateType();
        this.blurBalances = MyPreferences.isBlurBalances();
        this.showSortOrder = showSortOrder;
        this.context = context;
        this.cursor = c;
        this.onClickListener = onClickListener;
        this.onLongClickListener = onLongClickListener;
        rebuildRows();
    }

    @Override
    public long getItemId(int position) {
        Object row = rows.get(position);
        if (row instanceof AccountSeparator) {
            // Below zero, where no account's number can reach: the two kinds of
            // row share one list and must not be mistaken for each other.
            return -1 - ((AccountSeparator) row).id;
        }
        cursor.moveToPosition((Integer) row);
        return cursor.getLong(DatabaseHelper.BlotterColumns._id.ordinal());
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position) instanceof AccountSeparator ? ROW_SEPARATOR : ROW_ACCOUNT;
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        created++;
        Log.d(TAG, "onCreateViewHolder " + created);
        if (viewType == ROW_SEPARATOR) {
            return new SeparatorViewHolder(LayoutInflater.from(context)
                    .inflate(R.layout.account_separator_item, parent, false));
        }
        View view = LayoutInflater.from(context).inflate(R.layout.account_list_item, parent, false);
        return new AccountRecyclerAdapter.ViewHolder(view, this.onClickListener, this.onLongClickListener);
    }

    /** A heading: the words, how many are folded under it, and the mark. */
    public static class SeparatorViewHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView count;
        final ImageView fold;

        SeparatorViewHolder(View v) {
            super(v);
            title = v.findViewById(R.id.separator_title);
            count = v.findViewById(R.id.separator_count);
            fold = v.findViewById(R.id.separator_fold);
        }
    }

    /**
     * A dot for each person the account is held with, in a square block: one
     * alone, four two by two, nine three by three - the shape of the keys on a
     * keypad, which is where the eye already knows to read a small grid.
     */
    private void drawSharedDots(android.widget.LinearLayout box, java.util.List<Integer> colours) {
        box.removeAllViews();
        if (colours == null || colours.isEmpty()) {
            colours = java.util.Collections.singletonList(
                    io.github.mpstudios56.cifra.utils.Identity.COLOURS[1]);
        }
        int many = Math.min(colours.size(), 9);
        int perRow = many <= 1 ? 1 : (many <= 4 ? 2 : 3);
        float density = context.getResources().getDisplayMetrics().density;
        int size = Math.round((many <= 1 ? 8 : (many <= 4 ? 6 : 4)) * density);
        int gap = Math.round(1.5f * density);
        android.widget.LinearLayout row = null;
        for (int i = 0; i < many; i++) {
            if (i % perRow == 0) {
                row = new android.widget.LinearLayout(context);
                row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
                box.addView(row);
            }
            View dot = new View(context);
            android.widget.LinearLayout.LayoutParams lp =
                    new android.widget.LinearLayout.LayoutParams(size, size);
            lp.setMargins(gap, gap, gap, gap);
            dot.setLayoutParams(lp);
            dot.setBackgroundResource(R.drawable.dot);
            dot.getBackground().setTint(colours.get(i));
            row.addView(dot);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object row = rows.get(position);
        if (row instanceof AccountSeparator) {
            bindSeparator((SeparatorViewHolder) holder, (AccountSeparator) row);
            return;
        }
        bindAccount((ViewHolder) holder, (Integer) row);
    }

    private void bindSeparator(SeparatorViewHolder v, AccountSeparator separator) {
        v.title.setText(separator.title);
        // How many are put away, said only while they are: a count beside an
        // open group would be a number nobody asked for.
        v.count.setText(separator.folded && separator.hidden > 0
                ? String.valueOf(separator.hidden) : "");
        v.fold.setImageResource(separator.folded
                ? R.drawable.ic_unfold_years : R.drawable.ic_fold_years);
        v.itemView.setOnClickListener(x -> {
            if (onSeparatorAction != null) {
                onSeparatorAction.onSeparatorClicked(separator);
            }
        });
        v.itemView.setOnLongClickListener(x -> {
            if (onSeparatorAction != null) {
                onSeparatorAction.onSeparatorHeldDown(separator);
            }
            return true;
        });
    }

    private void bindAccount(@NonNull ViewHolder v, int cursorPosition) {
        v.used++;
        Log.d(TAG, "onBindViewHolder used " + v.used);
        long t0 = System.nanoTime();

        cursor.moveToPosition(cursorPosition);

        Account a = EntityManager.loadFromCursor(cursor, Account.class);

        v.icon.setTag(R.id.account, a.getId());
        v.iconText.setTag(R.id.account, a.getId());
        v.centerTouch.setTag(R.id.account, a.getId());
        v.balanceTouch.setTag(R.id.account, a.getId());

        v.center.setText(a.title);

        // Held together with somebody: a dot in their colour. Without it a
        // shared account looks exactly like a private one, and the difference
        // matters most when somebody is about to write something in it.
        if (sharedAccounts.contains(a.getId())) {
            v.sharedDots.setVisibility(View.VISIBLE);
            drawSharedDots(v.sharedDots, sharedColours.get(a.getId()));
        } else {
            v.sharedDots.setVisibility(View.GONE);
        }

        AccountType type = AccountType.valueOf(a.type);
        boolean stripeAnyway = false;

        // Falls back to the icon's own grey when no accent is set or it is not a
        // colour, rather than leaving the symbol invisible.
        AccountIcon chosen = AccountIcon.parse(a.icon);
        if (chosen != null) {
            // A symbol picked for this account, whatever kind it is, tinted with the
            // accent colour so two accounts at the same institution look related.
            v.icon.setVisibility(View.VISIBLE);
            v.iconText.setVisibility(View.INVISIBLE);
            v.icon.setImageResource(chosen.iconId);
            AccountIcon.Target target = AccountIcon.targetOf(a);
            if (!chosen.tintable || target == AccountIcon.Target.BAR) {
                v.icon.clearColorFilter();
            } else {
                v.icon.setColorFilter(accentOrNull(a.accentColor));
            }
        }
        else if (!Utils.isEmpty(a.icon)) {
            v.icon.setVisibility(View.INVISIBLE);
            v.iconText.setVisibility(View.VISIBLE);
            v.iconText.setText(a.icon);
        }
        else {
            // No symbol chosen: the account wears the mark of its kind. This is
            // every account restored from an old backup, and until now the mark
            // was left white whatever colour the account was given - the colour
            // could be chosen, saved, and never seen.
            v.icon.setVisibility(View.VISIBLE);
            v.iconText.setVisibility(View.INVISIBLE);

            boolean itIsALogo = false;
            if (type.isCard && a.cardIssuer != null) {
                CardIssuer cardIssuer = CardIssuer.valueOf(a.cardIssuer);
                v.icon.setImageResource(cardIssuer.iconId);
                itIsALogo = true;
            } else if (type.isElectronic && a.cardIssuer != null) {
                ElectronicPaymentType electronicPaymentType = ElectronicPaymentType.valueOf(a.cardIssuer);
                v.icon.setImageResource(electronicPaymentType.iconId);
                itIsALogo = true;
            } else {
                v.icon.setImageResource(type.iconId);
            }

            // A logo keeps its own colours: a recoloured logo is a wrong logo.
            // But then the colour chosen for the account would show nowhere, so
            // on those accounts it goes to the stripe even when the choice said
            // the symbol - a choice that cannot be honoured must not simply
            // vanish.
            if (itIsALogo && !Utils.isEmpty(a.accentColor)) {
                stripeAnyway = true;
            }
            if (itIsALogo || AccountIcon.targetOf(a) == AccountIcon.Target.BAR
                    || Utils.isEmpty(a.accentColor)) {
                v.icon.clearColorFilter();
            } else {
                v.icon.setColorFilter(accentOrNull(a.accentColor));
            }
        }

        if (a.isActive) {
            v.icon.getDrawable().mutate().setAlpha(0xFF);
            v.iconText.setAlpha(1.0f);
            v.activeIcon.setVisibility(View.INVISIBLE);
        } else {
            v.icon.getDrawable().mutate().setAlpha(0x77);
            v.iconText.setAlpha(0.5f);
            v.activeIcon.setVisibility(View.VISIBLE);
        }

        if (showSortOrder) {
            v.sortOrder.setVisibility(View.VISIBLE);
            v.sortOrder.setText(Integer.toString(a.sortOrder));
        }
        else {
            v.sortOrder.setVisibility(View.GONE);
        }

        StringBuilder sb = new StringBuilder();
        if (!Utils.isEmpty(a.issuer)) {
            sb.append(a.issuer);
        }
        if (!Utils.isEmpty(a.number)) {
            sb.append(" #").append(a.number);
        }
        if (sb.length() == 0) {
            sb.append(context.getString(type.titleId));
        }
        v.top.setText(sb.toString());

        switch (accountListDateType) {
            case LAST_TX:
                v.bottom.setText(df.format(new Date(a.lastTransactionDate)));
                break;
            case ACCOUNT_CREATION:
                if (a.creationDate != 0) {
                    v.bottom.setText(df.format(new Date(a.creationDate)));
                }
                else {
                    v.bottom.setText("");
                }
                break;
            case ACCOUNT_UPDATE:
                if (a.updatedOn != 0) {
                    v.bottom.setText(df.format(new Date(a.updatedOn)));
                }
                else {
                    v.bottom.setText("");
                }
                break;
            default:
            case HIDDEN:
                v.bottom.setVisibility(View.GONE);
        }

        long amount = a.totalAmount;
        if (type == AccountType.CREDIT_CARD && a.limitAmount != 0) {
            long limitAmount = Math.abs(a.limitAmount);
            long balance = limitAmount + amount;
            long balancePercentage = 10000*balance/limitAmount;
            u.setAmountText(v.rightCenter, a.currency, amount, false);
            u.setAmountText(v.right, a.currency, balance, false);
            v.right.setVisibility(View.VISIBLE);
            v.progress.setMax(10000);
            v.progress.setProgress((int)balancePercentage);
            v.progress.setVisibility(View.VISIBLE);
        } else {
            u.setAmountText(v.rightCenter, a.currency, amount, false);
            v.right.setVisibility(View.GONE);
            v.progress.setVisibility(View.GONE);
        }
        if (blurBalances) {
            u.applyBlur(v.rightCenter);
            u.applyBlur(v.right);
            v.balanceTouch.setOnClickListener((r) -> {
                if (v.rightCenter.getPaint().getMaskFilter() == null) {
                    u.applyBlur(v.rightCenter);
                    u.applyBlur(v.right);
                }
                else {
                    v.rightCenter.getPaint().setMaskFilter(null);
                    v.right.getPaint().setMaskFilter(null);
                }
                v.rightCenter.invalidate();
                v.right.invalidate();
            });
        }
        else {
            v.balanceTouch.setOnClickListener(v.onClickListener);
        }

        try {
            // Painting the symbol only means the stripe stays out of the way.
            boolean stripeWanted = stripeAnyway || AccountIcon.targetOf(a) != AccountIcon.Target.ICON;
            if (stripeWanted && !Utils.isEmpty(a.accentColor)) {
                int color = Color.parseColor(a.accentColor);
                v.accent.setVisibility(View.VISIBLE);
                v.accent.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                        new int[]{color, 0}));
            }
            else {
                v.accent.setVisibility(View.INVISIBLE);
            }
        } catch (Exception e) {
            v.accent.setVisibility(View.INVISIBLE);
        }

        long t1 = System.nanoTime();
        Log.d(TAG, "onBindViewHolder " + (t1 - t0) / 1000 + " us");
    }

    @Override
    public void onItemMove(int fromPosition, int toPosition) {

    }

    @Override
    public void onItemDismiss(int position, int direction) {

    }

    public static class ViewHolder extends RecyclerView.ViewHolder
            implements ItemTouchHelperViewHolder
    {
        public long used = 0;

        public final View view;
        public final TextView center;
        public final ImageView icon;
        public final TextView iconText;
        public final View centerTouch;
        public final View accent;
        public final ImageView activeIcon;
        public final TextView sortOrder;
        public final android.widget.LinearLayout sharedDots;
        public final TextView top;
        public final TextView bottom;
        public final View balanceTouch;
        public final TextView rightCenter;
        public final TextView right;
        public final ProgressBar progress;

        public final View.OnClickListener onClickListener;

        public ViewHolder(View v, View.OnClickListener onClickListener, View.OnLongClickListener onLongClickListener) {
            super(v);
            view = v;
            accent = v.findViewById(R.id.accent);
            center = v.findViewById(R.id.center);
            icon = v.findViewById(R.id.icon);
            iconText = v.findViewById(R.id.icon_text);
            centerTouch = v.findViewById(R.id.center_touch);
            activeIcon = v.findViewById(R.id.active_icon);
            sortOrder = v.findViewById(R.id.sort_order);
            sharedDots = v.findViewById(R.id.shared_dots);
            top = v.findViewById(R.id.top);
            bottom = v.findViewById(R.id.bottom);
            balanceTouch = v.findViewById(R.id.balance_touch);
            rightCenter = v.findViewById(R.id.right_center);
            right = v.findViewById(R.id.right);
            progress = v.findViewById(R.id.progress);
            progress.setVisibility(View.GONE);

            icon.setOnClickListener(onClickListener);
            icon.setOnLongClickListener(onLongClickListener);
            iconText.setOnClickListener(onClickListener);
            iconText.setOnLongClickListener(onLongClickListener);
            centerTouch.setOnClickListener(onClickListener);
            centerTouch.setOnLongClickListener(onLongClickListener);
            balanceTouch.setOnLongClickListener(onLongClickListener);

            this.onClickListener = onClickListener;
        }

        @Override
        public void onItemSelected() {

        }

        @Override
        public void onItemClear() {

        }
    }

    /**
     * The accent colour, or the symbol's own grey when none is set or the value is
     * not a colour: a symbol nobody can see would be worse than an uncoloured one.
     */
    private static int accentOrNull(String accentColor) {
        try {
            return Color.parseColor(accentColor.trim());
        } catch (Exception e) {
            return Color.parseColor("#FFFFFFFF");
        }
    }
}
