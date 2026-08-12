/*
 * This program is made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */
package tw.tib.financisto.db;

import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

/**
 * Identifiers that mean the same thing on both phones.
 * <p>
 * A row's number is only a number here: two phones both make their transaction
 * 57, and nothing about 57 says whether they are one movement or two. So every
 * row that can travel carries an identifier made where it was created.
 * <p>
 * They are filled in here rather than at every insert. There are a dozen paths
 * that write a transaction - the form, quick entry, the importer, a restore
 * from the bin, a scheduled pattern coming due - and teaching all of them would
 * mean one of them being forgotten and a row travelling without a name. Filling
 * the blanks in one sweep before the phones talk is one place to get right
 * instead of twelve.
 */
public class Uuids {

    private static final String TAG = "Uuids";

    /** Every table whose rows one phone can tell the other about. */
    private static final String[] TABLES = {
            DatabaseHelper.TRANSACTION_TABLE,
            DatabaseHelper.ACCOUNT_TABLE,
            DatabaseHelper.CATEGORY_TABLE,
            DatabaseHelper.PAYEE_TABLE,
            DatabaseHelper.PROJECT_TABLE,
            DatabaseHelper.LOCATIONS_TABLE,
            DatabaseHelper.CURRENCY_TABLE,
            DatabaseHelper.BUDGET_TABLE,
    };

    private Uuids() {
    }

    /**
     * Gives an identifier to everything that has not got one. Cheap when there
     * is nothing to do, which is the usual case.
     *
     * @return how many rows were named
     */
    public static int fillBlanks(SQLiteDatabase db) {
        int named = 0;
        for (String table : TABLES) {
            try {
                named += fill(db, table);
            } catch (Exception e) {
                Log.e(TAG, "could not name the rows of " + table, e);
            }
        }
        return named;
    }

    private static int fill(SQLiteDatabase db, String table) {
        // randomblob rather than a Java UUID: it is one statement over the
        // whole table instead of a row at a time, and sixteen random bytes is
        // sixteen random bytes whoever produced them.
        db.execSQL("update " + table
                + " set uuid = lower(hex(randomblob(16))) where uuid is null or uuid = ''");
        try (android.database.Cursor c = db.rawQuery("select changes()", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    /** The identifier of a row, made now if it had none. */
    public static String of(SQLiteDatabase db, String table, long id) {
        try (android.database.Cursor c = db.query(table, new String[]{"uuid"},
                "_id=?", new String[]{String.valueOf(id)}, null, null, null)) {
            if (c.moveToFirst()) {
                String uuid = c.getString(0);
                if (uuid != null && !uuid.isEmpty()) {
                    return uuid;
                }
            } else {
                return null;
            }
        }
        String uuid = java.util.UUID.randomUUID().toString();
        android.content.ContentValues values = new android.content.ContentValues();
        values.put("uuid", uuid);
        db.update(table, values, "_id=?", new String[]{String.valueOf(id)});
        return uuid;
    }
}
