package tw.tib.financisto.sync;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Which people a given account is held with.
 * <p>
 * With two people this is the tick box that was here before: shared, or not.
 * With three it stops being a yes or no - the joint account is everybody's, the
 * one for the flat is two of the three - and a tick cannot say that.
 * <p>
 * No rows for an account means "everybody in the group", which is what the tick
 * used to mean, so nothing set up before this needs touching.
 */
public class SharedWith {

    private static final String TAG = "SharedWith";
    private static final String TABLE = "shared_with";

    private SharedWith() {
    }

    /**
     * The colour of the first person each shared account is held with, by
     * account. For drawing the list without a query per row.
     */
    public static java.util.Map<Long, java.util.List<Integer>> coloursByAccount(SQLiteDatabase db) {
        java.util.Map<Long, java.util.List<Integer>> colours = new java.util.HashMap<>();
        String sql = "select a._id, p.colour from account a"
                + " inner join " + TABLE + " s on s.uuid = a.uuid"
                + " inner join person p on p.mark = s.mark";
        try (Cursor c = db.rawQuery(sql, null)) {
            while (c.moveToNext()) {
                int colour = c.getInt(1);
                if (colour == 0) {
                    continue;
                }
                java.util.List<Integer> theirs = colours.get(c.getLong(0));
                if (theirs == null) {
                    theirs = new java.util.ArrayList<>();
                    colours.put(c.getLong(0), theirs);
                }
                if (!theirs.contains(colour)) {
                    theirs.add(colour);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "could not read the colours", e);
        }
        return colours;
    }

    /**
     * Every shared account with the names it is held with, in the order the
     * accounts are listed. The other way round from the people list: there by
     * person, here by account.
     */
    public static java.util.List<String[]> accountsAndWho(SQLiteDatabase db) {
        java.util.List<String[]> rows = new java.util.ArrayList<>();
        // Starts from the accounts that are shared, not from the people they
        // are assigned to: an account ticked as shared but not yet given to
        // anybody was falling out of the list entirely, which is exactly the
        // account somebody is looking for when they open this.
        String sql = "select a.title, group_concat(p.name, ', ')"
                + " from account a"
                + " inner join shared_thing t on t.uuid = a.uuid and t.kind = 'account'"
                + " left join " + TABLE + " s on s.uuid = a.uuid"
                + " left join person p on p.mark = s.mark"
                + " group by a._id order by a.sort_order, a.title";
        try (Cursor c = db.rawQuery(sql, null)) {
            while (c.moveToNext()) {
                rows.add(new String[]{c.getString(0),
                        c.getString(1) == null ? "" : c.getString(1)});
            }
        } catch (Exception e) {
            Log.e(TAG, "could not read the shared accounts", e);
        }
        return rows;
    }

    /** The people an account is held with, written out, or empty. */
    public static String namesOf(SQLiteDatabase db, String accountUuid) {
        if (accountUuid == null || accountUuid.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        String sql = "select p.name from " + TABLE + " s"
                + " inner join person p on p.mark = s.mark"
                + " where s.uuid = ? order by p.seen_on";
        try (Cursor c = db.rawQuery(sql, new String[]{accountUuid})) {
            while (c.moveToNext()) {
                String name = c.getString(0);
                if (name == null || name.isEmpty()) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(name);
            }
        } catch (Exception e) {
            Log.e(TAG, "could not read who holds " + accountUuid, e);
        }
        return sb.toString();
    }

    /**
     * Notes that this account is held with this person, leaving whoever else it
     * is held with alone.
     * <p>
     * For an account that arrives: it comes from somebody, and that somebody is
     * who it is shared with here too. Without this the account is shared with
     * nobody in particular, and the dot beside it has no colour to take.
     */
    public static void add(SQLiteDatabase db, String accountUuid, String mark) {
        note(db, accountUuid, mark, false);
    }

    /**
     * Notes that this account arrived from that person, which is not the same
     * as being shared with them.
     * <p>
     * It was the same row before, and that is how an account went round in a
     * circle: what Marcello gave to Deborah came back marked as something
     * Deborah was sharing with Marcello, so opening it to change its colour was
     * enough to send its opening balance back and double his. Receiving is now
     * written down as receiving; sharing it onward - to him or to a third
     * person - is a choice made in the account, like any other.
     */
    public static void arrived(SQLiteDatabase db, String accountUuid, String mark) {
        note(db, accountUuid, mark, true);
    }

    private static void note(SQLiteDatabase db, String accountUuid, String mark,
                             boolean incoming) {
        if (accountUuid == null || accountUuid.isEmpty() || mark == null || mark.isEmpty()) {
            return;
        }
        try {
            ContentValues v = new ContentValues();
            v.put("uuid", accountUuid);
            v.put("mark", mark);
            v.put("incoming", incoming ? 1 : 0);
            db.insertWithOnConflict(TABLE, null, v, SQLiteDatabase.CONFLICT_IGNORE);
        } catch (Exception e) {
            Log.e(TAG, "could not note " + accountUuid + " as held with " + mark, e);
        }
    }

    /** The people this account came from, if it came from anybody. */
    public static Set<String> arrivedFrom(SQLiteDatabase db, String accountUuid) {
        return marks(db, accountUuid, "uuid=? and incoming=1");
    }

    /** Whether anything at all has been said about this account. */
    private static boolean anythingAbout(SQLiteDatabase db, String accountUuid) {
        return !marks(db, accountUuid, "uuid=?").isEmpty();
    }

    private static Set<String> marks(SQLiteDatabase db, String accountUuid, String where) {
        Set<String> marks = new HashSet<>();
        if (accountUuid == null || accountUuid.isEmpty()) {
            return marks;
        }
        try (Cursor c = db.query(TABLE, new String[]{"mark"}, where,
                new String[]{accountUuid}, null, null, null)) {
            while (c.moveToNext()) {
                marks.add(c.getString(0));
            }
        } catch (Exception e) {
            Log.e(TAG, "could not read who " + accountUuid + " is shared with", e);
        }
        return marks;
    }

    /**
     * The people this account goes to. Only the ones it is given to: whoever
     * gave it is not on this list, and nothing on it travels back to them
     * unless somebody says so.
     */
    public static Set<String> of(SQLiteDatabase db, String accountUuid) {
        return marks(db, accountUuid, "uuid=? and incoming=0");
    }

    /** Replaces the list. An empty list means everybody. */
    public static void set(SQLiteDatabase db, String accountUuid, List<String> marks) {
        if (accountUuid == null || accountUuid.isEmpty()) {
            return;
        }
        try {
            // Only what this phone gives away is rewritten. Where the account
            // came from is a fact about it, not a choice, and clearing the list
            // must not lose it.
            db.delete(TABLE, "uuid=? and incoming=0", new String[]{accountUuid});
            if (marks == null) {
                return;
            }
            for (String mark : marks) {
                if (mark == null || mark.isEmpty()) {
                    continue;
                }
                ContentValues v = new ContentValues();
                v.put("uuid", accountUuid);
                v.put("mark", mark);
                db.insertWithOnConflict(TABLE, null, v, SQLiteDatabase.CONFLICT_REPLACE);
            }
        } catch (Exception e) {
            Log.e(TAG, "could not write who " + accountUuid + " is shared with", e);
        }
    }

    /**
     * Whether a line about this account should reach the person addressed by
     * this mark.
     */
    public static boolean reaches(SQLiteDatabase db, String accountUuid, String mark) {
        Set<String> marks = of(db, accountUuid);
        if (!marks.isEmpty()) {
            return marks.contains(mark);
        }
        // Nothing said about it at all still means everybody, which is what a
        // tick meant before there were people to tick. But an account that is
        // only known to have arrived reaches nobody: it is not being given to
        // anyone until somebody says it is.
        return !anythingAbout(db, accountUuid);
    }

    /**
     * Everybody an account named by this uuid goes to, for writing on a line.
     * <p>
     * Empty means everybody, and is written as no list at all rather than as a
     * list of everybody: a phone that joins the group later is then included by
     * lines written before it existed.
     */
    public static Set<String> recipientsOf(SQLiteDatabase db, String accountUuid) {
        return of(db, accountUuid);
    }

    /** Sets the ContentValues import used above. */
}
