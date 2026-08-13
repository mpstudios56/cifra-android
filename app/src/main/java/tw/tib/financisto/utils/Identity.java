/*
 * This program is made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */
package tw.tib.financisto.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

import androidx.preference.PreferenceManager;

import tw.tib.financisto.Application;

/**
 * Who is keeping this ledger: a name, a colour and a symbol.
 * <p>
 * Two of them, no more. Not accounts and not logins - nobody signs in to
 * anything - only enough to tell one person's entries from the other's at a
 * glance, without having to read a name on every line.
 * <p>
 * The colour is the part that does the work. A list of forty movements read
 * with the eye rather than with the mind wants one thing that differs, and a
 * coloured edge is read before any word on the row is.
 */
public class Identity {

    /** Enough to be told apart on a dark list, and none of them the red of a loss. */
    public static final int[] COLOURS = {
            0xFF5B8DEF, 0xFFE9A742, 0xFF3FA96F, 0xFFC9709A,
            0xFF6ECBC0, 0xFF9B7EDE, 0xFFD98E5A, 0xFF7FB069,
    };

    public static final int MINE = 0;
    public static final int THEIRS = 1;

    public final String name;
    public final int colour;
    /** A category symbol, borrowed: the same grid, so there is one set to learn. */
    public final String icon;

    private Identity(String name, int colour, String icon) {
        this.name = name;
        this.colour = colour;
        this.icon = icon;
    }

    public static Identity mine(Context context) {
        return read(context, "sync_author", "sync_colour_me", "sync_icon_me", COLOURS[0]);
    }

    public static Identity theirs(Context context) {
        return read(context, "sync_partner", "sync_colour_them", "sync_icon_them", COLOURS[1]);
    }

    private static Identity read(Context context, String nameKey, String colourKey,
                                 String iconKey, int fallback) {
        SharedPreferences p = prefs(context);
        int colour;
        try {
            colour = Color.parseColor(p.getString(colourKey, "").trim());
        } catch (Exception e) {
            colour = fallback;
        }
        return new Identity(p.getString(nameKey, ""), colour, p.getString(iconKey, ""));
    }

    public static void save(Context context, int which, String name, int colour, String icon) {
        boolean me = which == MINE;
        prefs(context).edit()
                .putString(me ? "sync_author" : "sync_partner", name == null ? "" : name.trim())
                .putString(me ? "sync_colour_me" : "sync_colour_them",
                        String.format("#%06X", 0xFFFFFF & colour))
                .putString(me ? "sync_icon_me" : "sync_icon_them", icon == null ? "" : icon)
                .apply();
    }

    /** Whether a movement written on this phone is the reader's own. */
    public static boolean isMine(String createdBy) {
        // Blank means written here before any of this existed, which it was.
        return createdBy == null || createdBy.isEmpty()
                || createdBy.equals(MyPreferences.getSyncDeviceId());
    }

    /**
     * The colour of whoever wrote a movement that came from somewhere else.
     * <p>
     * Movements arriving are signed with the code shared with the person who
     * sent them, so their colour is the one chosen here for that person - and
     * it stays right even if they change phone.
     */
    public static int colourOf(android.database.sqlite.SQLiteDatabase db, String createdBy) {
        if (createdBy != null && !createdBy.isEmpty()) {
            for (tw.tib.financisto.sync.People.Person p : tw.tib.financisto.sync.People.all(db)) {
                if (createdBy.equals(p.mark) && p.colour != 0) {
                    return p.colour;
                }
            }
        }
        return COLOURS[1];
    }

    private static SharedPreferences prefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(
                context == null ? Application.getInstance() : context);
    }
}
