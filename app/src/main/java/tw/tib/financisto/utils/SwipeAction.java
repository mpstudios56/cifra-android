package tw.tib.financisto.utils;

import tw.tib.financisto.R;

/**
 * What dragging a movement sideways does.
 * <p>
 * Which one belongs to which direction is a setting rather than a decision made
 * here: somebody who tidies a statement all evening wants the tick, somebody
 * entering the week's shopping wants the copy, and somebody who mistypes wants
 * the bin. All of them are right, and none of them are the same person.
 */
public enum SwipeAction {

    NONE(R.string.swipe_nothing, 0, 0, R.string.swipe_nothing),
    EDIT(R.string.edit, 0xFFE0A33C, R.drawable.ic_action_edit, R.string.swipe_done_edit),
    DUPLICATE(R.string.duplicate, 0xFF6C8CF5, R.drawable.ic_action_copy, R.string.swipe_done_duplicate),
    CLEAR(R.string.clear, 0xFF4CAF7D, R.drawable.status_cleared, R.string.swipe_done_clear),
    RECONCILE(R.string.reconcile, 0xFF2FA37A, R.drawable.status_reconciled, R.string.swipe_done_reconcile),
    DELETE(R.string.delete, 0xFFE5484D, R.drawable.ic_action_trash, R.string.swipe_done_delete);

    public final int titleId;
    /** The colour that comes out from under the row as it moves. */
    public final int colour;
    /** The sign that stands in the colour, so the hand knows before it lets go. */
    public final int iconId;
    /** How it reads once it has happened: "Eliminato", not "Elimina". */
    public final int doneId;

    SwipeAction(int titleId, int colour, int iconId, int doneId) {
        this.titleId = titleId;
        this.colour = colour;
        this.iconId = iconId;
        this.doneId = doneId;
    }

    /** Reads back what the settings hold, forgivingly. */
    public static SwipeAction of(String name) {
        if (name == null) {
            return NONE;
        }
        for (SwipeAction a : values()) {
            if (a.name().equals(name)) {
                return a;
            }
        }
        return NONE;
    }
}
