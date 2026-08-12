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

import java.util.ArrayList;
import java.util.List;

/**
 * Ten rows all called EUR are one currency.
 * <p>
 * Some ledgers arrive from Financisto carrying the same currency over and over.
 * To the app those are ten different currencies, and converting between two of
 * them needs an exchange rate from euros to euros, which nobody has ever
 * written down. So every total that crosses two of them reads "N/A" - the money
 * is all there, the movements are all there, and not one figure adds up.
 * <p>
 * They are joined without asking, which is the opposite of what happens to
 * categories. Two categories with one name may be two different things; two
 * currencies with the code EUR are the euro. There is nothing to decide.
 */
public class Currencies {

    private static final String TAG = "Currencies";

    private Currencies() {
    }

    /**
     * @return how many extra rows were folded away
     */
    public static int mergeSameCode(SQLiteDatabase db) {
        List<String> codes = new ArrayList<>();
        try (Cursor c = db.rawQuery(
                "select name from currency where name is not null and name <> ''"
                        + " group by name having count(*) > 1", null)) {
            while (c.moveToNext()) {
                codes.add(c.getString(0));
            }
        } catch (Exception e) {
            Log.e(TAG, "could not look for repeated currencies", e);
            return 0;
        }
        if (codes.isEmpty()) {
            return 0;
        }

        int folded = 0;
        for (String code : codes) {
            folded += fold(db, code);
        }
        return folded;
    }

    private static int fold(SQLiteDatabase db, String code) {
        long keeper = keeperFor(db, code);
        if (keeper <= 0) {
            return 0;
        }
        List<Long> losers = new ArrayList<>();
        try (Cursor c = db.query("currency", new String[]{"_id"},
                "name=? and _id<>?", new String[]{code, String.valueOf(keeper)},
                null, null, null)) {
            while (c.moveToNext()) {
                losers.add(c.getLong(0));
            }
        } catch (Exception e) {
            return 0;
        }
        if (losers.isEmpty()) {
            return 0;
        }

        db.beginTransaction();
        try {
            for (long loser : losers) {
                String[] args = {String.valueOf(loser)};
                point(db, "account", "currency_id", keeper, args);
                point(db, "budget", "currency_id", keeper, args);
                point(db, DatabaseHelper.TRANSACTION_TABLE, "original_currency_id", keeper, args);
                db.delete("currency_exchange_rate", "from_currency_id=? or to_currency_id=?",
                        new String[]{String.valueOf(loser), String.valueOf(loser)});
                db.delete("currency", "_id=?", args);
            }
            db.setTransactionSuccessful();
            Log.i(TAG, "folded " + losers.size() + " extra rows of " + code + " into " + keeper);
        } catch (Exception e) {
            Log.e(TAG, "could not fold the repeated " + code, e);
            return 0;
        } finally {
            db.endTransaction();
        }
        return losers.size();
    }

    /**
     * The one that stays: the home currency if one of them is it, otherwise the
     * one the most accounts are kept in, otherwise the first. Keeping the most
     * used one means the fewest rows have to be pointed anywhere else.
     */
    private static long keeperFor(SQLiteDatabase db, String code) {
        try (Cursor c = db.query("currency", new String[]{"_id"},
                "name=? and is_default=1", new String[]{code}, null, null, "_id asc")) {
            if (c.moveToFirst()) {
                return c.getLong(0);
            }
        } catch (Exception e) {
            return 0;
        }
        try (Cursor c = db.rawQuery(
                "select c._id, count(a._id) n from currency c"
                        + " left join account a on a.currency_id = c._id"
                        + " where c.name = ? group by c._id order by n desc, c._id asc limit 1",
                new String[]{code})) {
            return c.moveToFirst() ? c.getLong(0) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static void point(SQLiteDatabase db, String table, String column,
                              long keeper, String[] loser) {
        try {
            ContentValues v = new ContentValues();
            v.put(column, keeper);
            db.update(table, v, column + "=?", loser);
        } catch (Exception e) {
            Log.e(TAG, "could not point " + table + "." + column, e);
        }
    }
}
