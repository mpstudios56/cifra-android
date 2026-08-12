/*
 * This program is made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */
package tw.tib.financisto.sync;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import tw.tib.financisto.db.DatabaseHelper;

/**
 * Two movements that might be one payment entered twice.
 * <p>
 * Dinner for sixty, paid from the shared account. She writes it down at a
 * quarter past nine and he, not knowing, writes it down at twenty to eleven.
 * Now the account is out by sixty euros and neither of them can see why.
 * <p>
 * But two coffees from the same machine on the same morning look exactly the
 * same, and so does a bill split down the middle. The difference is not in the
 * rows - it is in what actually happened, and only the two people know that. So
 * this finds the pairs and asks. It never removes anything on its own: a
 * movement deleted on a suspicion is worse than a movement counted twice, because
 * the second one is visible and the first is not.
 */
public class Duplicates {

    private static final String TAG = "Duplicates";
    private static final String TABLE = "duplicate_notice";

    /** One pair, as the screen needs it. */
    public static class Pair {
        public long id;
        public String uuidA;
        public String uuidB;
        public String title;
        public String amount;
        public String whenA;
        public String whenB;
        public String authorA;
        public String authorB;
    }

    private Duplicates() {
    }

    /**
     * Looks for pairs among what has just arrived.
     * <p>
     * Alike means: the same amount, the same category - the same row, not two
     * categories that happen to share a name - the same day, and written on two
     * different phones. The same day rather than the same minute: two people
     * never write the same payment down at the same instant, and a rule to the
     * minute would never once fire.
     *
     * @return how many new pairs were found
     */
    public static int look(SQLiteDatabase db) {
        int found = 0;
        String sql = "select a.uuid, b.uuid from transactions a"
                + " inner join transactions b on"
                + "   a.from_amount = b.from_amount"
                + "   and a.category_id = b.category_id"
                + "   and date(a.datetime/1000, 'unixepoch') = date(b.datetime/1000, 'unixepoch')"
                + "   and a.created_by <> b.created_by"
                + "   and a.uuid < b.uuid"
                + " where a.is_template = 0 and b.is_template = 0"
                + "   and a.parent_id = 0 and b.parent_id = 0"
                + "   and a.created_by <> '' and b.created_by <> ''"
                + "   and a.uuid <> '' and b.uuid <> ''";
        List<String[]> pairs = new ArrayList<>();
        try (Cursor c = db.rawQuery(sql, null)) {
            while (c.moveToNext()) {
                pairs.add(new String[]{c.getString(0), c.getString(1)});
            }
        } catch (Exception e) {
            Log.e(TAG, "could not look for duplicates", e);
            return 0;
        }

        for (String[] pair : pairs) {
            if (known(db, pair[0], pair[1])) {
                continue;
            }
            ContentValues v = new ContentValues();
            v.put("uuid_a", pair[0]);
            v.put("uuid_b", pair[1]);
            v.put("noticed_on", System.currentTimeMillis());
            v.put("settled", 0);
            if (db.insert(TABLE, null, v) > 0) {
                found++;
            }
        }
        return found;
    }

    /** Whether this pair has been raised before - answered or not. */
    private static boolean known(SQLiteDatabase db, String a, String b) {
        try (Cursor c = db.query(TABLE, new String[]{"_id"}, "uuid_a=? and uuid_b=?",
                new String[]{a, b}, null, null, null)) {
            return c.moveToFirst();
        } catch (Exception e) {
            return true;
        }
    }

    public static int waiting(SQLiteDatabase db) {
        try (Cursor c = db.rawQuery(
                "select count(*) from " + TABLE + " where settled = 0", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /** The pairs still to be answered, with enough of each to recognise it. */
    public static List<Pair> list(SQLiteDatabase db) {
        List<Pair> pairs = new ArrayList<>();
        try (Cursor c = db.query(TABLE, null, "settled = 0", null, null, null,
                "noticed_on desc")) {
            while (c.moveToNext()) {
                Pair p = new Pair();
                p.id = c.getLong(c.getColumnIndexOrThrow("_id"));
                p.uuidA = c.getString(c.getColumnIndexOrThrow("uuid_a"));
                p.uuidB = c.getString(c.getColumnIndexOrThrow("uuid_b"));
                // A pair whose halves are no longer both there has answered
                // itself: somebody deleted one of them in the meantime.
                if (!exists(db, p.uuidA) || !exists(db, p.uuidB)) {
                    settle(db, p.id);
                    continue;
                }
                pairs.add(p);
            }
        } catch (Exception e) {
            Log.e(TAG, "could not read the pairs", e);
        }
        return pairs;
    }

    private static boolean exists(SQLiteDatabase db, String uuid) {
        try (Cursor c = db.query(DatabaseHelper.TRANSACTION_TABLE, new String[]{"_id"},
                "uuid=?", new String[]{uuid}, null, null, null)) {
            return c.moveToFirst();
        } catch (Exception e) {
            return false;
        }
    }

    public static long idOf(SQLiteDatabase db, String uuid) {
        try (Cursor c = db.query(DatabaseHelper.TRANSACTION_TABLE, new String[]{"_id"},
                "uuid=?", new String[]{uuid}, null, null, null)) {
            return c.moveToFirst() ? c.getLong(0) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Answered, and never raised again. Somebody who has said these are two
     * different things should not be asked a second time: that is telling them
     * they were wrong.
     */
    public static void settle(SQLiteDatabase db, long id) {
        ContentValues v = new ContentValues();
        v.put("settled", 1);
        db.update(TABLE, v, "_id=?", new String[]{String.valueOf(id)});
    }
}
