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
        String sql = "select a.title, group_concat(p.name, ', ')"
                + " from account a"
                + " inner join " + TABLE + " s on s.uuid = a.uuid"
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

    /** The people this account goes to, or empty for everybody. */
    public static Set<String> of(SQLiteDatabase db, String accountUuid) {
        Set<String> marks = new HashSet<>();
        if (accountUuid == null || accountUuid.isEmpty()) {
            return marks;
        }
        try (Cursor c = db.query(TABLE, new String[]{"mark"}, "uuid=?",
                new String[]{accountUuid}, null, null, null)) {
            while (c.moveToNext()) {
                marks.add(c.getString(0));
            }
        } catch (Exception e) {
            Log.e(TAG, "could not read who " + accountUuid + " is shared with", e);
        }
        return marks;
    }

    /** Replaces the list. An empty list means everybody. */
    public static void set(SQLiteDatabase db, String accountUuid, List<String> marks) {
        if (accountUuid == null || accountUuid.isEmpty()) {
            return;
        }
        try {
            db.delete(TABLE, "uuid=?", new String[]{accountUuid});
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
        return marks.isEmpty() || marks.contains(mark);
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
}
