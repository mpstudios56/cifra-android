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

    NONE(R.string.swipe_nothing, 0, 0),
    EDIT(R.string.edit, 0xFFEDA00D, R.drawable.ic_action_edit),
    DUPLICATE(R.string.duplicate, 0xFF5B8DEF, R.drawable.ic_action_copy),
    CLEAR(R.string.clear, 0xFF79C471, R.drawable.status_cleared),
    RECONCILE(R.string.reconcile, 0xFF006A25, R.drawable.status_reconciled),
    DELETE(R.string.delete, 0xFFDF0024, R.drawable.ic_action_trash);

    public final int titleId;
    /** The colour that comes out from under the row as it moves. */
    public final int colour;
    /** The sign that stands in the colour, so the hand knows before it lets go. */
    public final int iconId;

    SwipeAction(int titleId, int colour, int iconId) {
        this.titleId = titleId;
        this.colour = colour;
        this.iconId = iconId;
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
