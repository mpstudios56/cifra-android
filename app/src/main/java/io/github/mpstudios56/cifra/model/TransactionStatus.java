package io.github.mpstudios56.cifra.model;

import io.github.mpstudios56.cifra.R;
import io.github.mpstudios56.cifra.utils.EntityEnum;

/**
 * How far a movement has got: written down, still to come, checked against the
 * statement.
 * <p>
 * The two letters are what goes in the database, and they are short because
 * they were once written on every row. They are never shown: each carries its
 * own name, mark and colour, so wherever the five are offered they look the way
 * they look on the movements themselves.
 */
public enum TransactionStatus implements EntityEnum {

    /** Brought back from the trash. */
    RS(R.string.transaction_status_restored, R.drawable.status_restored,
            R.color.restored_transaction_color),
    /** Expected, not yet happened. */
    PN(R.string.transaction_status_pending, R.drawable.status_pending,
            R.color.pending_transaction_color),
    /** Happened, and not yet checked against anything. */
    UR(R.string.transaction_status_unreconciled, R.drawable.status_unreconciled,
            R.color.unreconciled_transaction_color),
    /** Seen on the statement. */
    CL(R.string.transaction_status_cleared, R.drawable.status_cleared,
            R.color.cleared_transaction_color),
    /** Seen, and the balance agreed with it. */
    RC(R.string.transaction_status_reconciled, R.drawable.status_reconciled,
            R.color.reconciled_transaction_color);

    public final int titleId;
    public final int iconId;
    public final int colorId;

    TransactionStatus(int titleId, int iconId, int colorId) {
        this.titleId = titleId;
        this.iconId = iconId;
        this.colorId = colorId;
    }

    @Override
    public int getTitleId() {
        return titleId;
    }

    @Override
    public int getIconId() {
        return iconId;
    }
}
