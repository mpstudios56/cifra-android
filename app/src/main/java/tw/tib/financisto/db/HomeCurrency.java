/*
 * This program is made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */
package tw.tib.financisto.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

/**
 * Makes sure one currency is marked as the one everything is counted in.
 * <p>
 * Without it every total on every screen reads "N/A". The totals are worked out
 * in the home currency; with none marked, the app looks for an exchange rate
 * from euros into nothing, does not find one, and gives up - correctly, and in a
 * way that looks exactly like the import failed.
 * <p>
 * A backup made by a version that never asked which currency was home carries
 * none. This runs on the way in from a backup <em>and</em> every time the
 * currencies are read, because the people it hurts most are the ones who
 * imported before there was anything to fix it.
 */
public class HomeCurrency {

    private static final String TAG = "HomeCurrency";

    private HomeCurrency() {
    }

    /**
     * @return the id now marked as home, or 0 when there are no currencies at
     *         all - which is a database with nothing in it yet
     */
    public static long ensure(SQLiteDatabase db) {
        try (Cursor c = db.rawQuery(
                "select _id from currency where is_default = 1 limit 1", null)) {
            if (c.moveToFirst()) {
                return c.getLong(0);
            }
        } catch (Exception e) {
            return 0;
        }

        // The one the most accounts are kept in. On the single-currency files
        // this happens to, that is the only one there is; on the others it is
        // the one whose totals will need converting least.
        long chosen = 0;
        try (Cursor c = db.rawQuery(
                "select c._id, count(a._id) n from currency c"
                        + " left join account a on a.currency_id = c._id"
                        + " group by c._id order by n desc, c._id asc limit 1", null)) {
            if (c.moveToFirst()) {
                chosen = c.getLong(0);
            }
        } catch (Exception e) {
            return 0;
        }
        if (chosen <= 0) {
            return 0;
        }
        try {
            ContentValues values = new ContentValues();
            values.put("is_default", 1);
            db.update("currency", values, "_id=?", new String[]{String.valueOf(chosen)});
            Log.i(TAG, "no home currency was set; chose " + chosen);
        } catch (Exception e) {
            Log.e(TAG, "could not mark a home currency", e);
        }
        return chosen;
    }
}
