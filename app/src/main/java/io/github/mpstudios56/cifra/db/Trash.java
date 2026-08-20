/*
 * This program is made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */
package io.github.mpstudios56.cifra.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * What was deleted, kept for a while in case it should not have been.
 * <p>
 * The rows are copied out whole and then deleted for real, rather than being
 * marked as gone and filtered out everywhere afterwards. Every query in the app
 * would have had to learn about the flag, and one that forgot would quietly show
 * something that had been thrown away - in balances, in reports, in a budget.
 * Copying costs a table; forgetting costs somebody's figures.
 * <p>
 * A deleted transaction brings its split children and its attributes with it,
 * because it took them with it on the way out.
 */
public class Trash {

    private static final String TAG = "Trash";
    public static final String TABLE = "trash";

    /** What the row was, so the list can say it and the restore can undo it. */
    public static class Item {
        public long id;
        public String entity;
        public long entityId;
        public String title;
        public String subtitle;
        public long deletedOn;
        public String author;
    }

    private Trash() {
    }

    // ------------------------------------------------------------------ saving

    /**
     * Copies a row, and anything that goes with it, into the bin. Call this
     * immediately before deleting it for real, while it is still there to read.
     */
    public static void keep(SQLiteDatabase db, String table, long id,
                            String title, String subtitle) {
        keep(db, table, id, title, subtitle, "");
    }

    /**
     * @param author who deleted it, when that was not the person holding this
     *               phone - so the bin can say "Debora deleted this" instead of
     *               a movement quietly disappearing overnight
     */
    public static void keep(SQLiteDatabase db, String table, long id,
                            String title, String subtitle, String author) {
        try {
            JSONArray rows = new JSONArray();
            copyRow(db, rows, table, "_id=?", args(id));

            if (DatabaseHelper.TRANSACTION_TABLE.equals(table)) {
                // The children go out with the parent, so they come back with it.
                List<Long> family = new ArrayList<>();
                family.add(id);
                try (Cursor c = db.query(DatabaseHelper.TRANSACTION_TABLE,
                        new String[]{"_id"}, "parent_id=?", args(id), null, null, null)) {
                    while (c.moveToNext()) {
                        family.add(c.getLong(0));
                    }
                }
                copyRow(db, rows, DatabaseHelper.TRANSACTION_TABLE, "parent_id=?", args(id));
                for (long member : family) {
                    copyRow(db, rows, DatabaseHelper.TRANSACTION_ATTRIBUTE_TABLE,
                            "transaction_id=?", args(member));
                }
            }

            ContentValues values = new ContentValues();
            values.put("entity", table);
            values.put("entity_id", id);
            values.put("title", title == null ? "" : title);
            values.put("subtitle", subtitle == null ? "" : subtitle);
            values.put("payload", rows.toString());
            values.put("deleted_on", System.currentTimeMillis());
            values.put("author", author == null ? "" : author);
            db.insert(TABLE, null, values);
        } catch (Exception e) {
            // Never stop a deletion because the bin could not record it: the
            // person asked for the row to go, and it goes.
            Log.e(TAG, "could not put " + table + " " + id + " in the bin", e);
        }
    }

    private static void copyRow(SQLiteDatabase db, JSONArray into, String table,
                                String where, String[] args) throws Exception {
        try (Cursor c = db.query(table, null, where, args, null, null, null)) {
            while (c.moveToNext()) {
                JSONObject values = new JSONObject();
                for (int i = 0; i < c.getColumnCount(); i++) {
                    if (!c.isNull(i)) {
                        values.put(c.getColumnName(i), c.getString(i));
                    }
                }
                JSONObject row = new JSONObject();
                row.put("table", table);
                row.put("values", values);
                into.put(row);
            }
        }
    }

    private static String[] args(long id) {
        return new String[]{String.valueOf(id)};
    }

    // --------------------------------------------------------------- restoring

    /** How a restore went, and what it was. */
    public enum Outcome {
        /** Back where it was. */
        DONE,
        /** Its account has been deleted in the meantime, so it has nowhere to go. */
        NO_ACCOUNT,
        /** Something else went wrong; the bin still holds it. */
        FAILED,
    }

    public static class Restored {
        public Outcome outcome = Outcome.FAILED;
        /** The table put back, so the caller knows whether balances must be made again. */
        public String entity;
        /** True when a category, payee, project or place had gone and was dropped. */
        public boolean lostSomething;
    }

    /**
     * Puts the rows back where they came from, with the identifiers they had.
     * <p>
     * The world may have moved on while the row sat here. What it pointed at can
     * have been deleted in the meantime, and putting the identifiers back as
     * they were would give a transaction on an account that does not exist -
     * counted in no balance, shown with a blank name, findable only by somebody
     * wondering why the figures no longer add up.
     * <p>
     * So the account is required: without it there is nowhere to put the money
     * back, and the row stays in the bin with an explanation. A category, a
     * payee, a project or a place is not required - the amount and the date are
     * the movement, and the rest is labelling. Those are dropped and the row
     * comes back without them.
     */
    public static Restored restore(SQLiteDatabase db, long trashId) {
        Restored restored = new Restored();
        db.beginTransaction();
        try (Cursor c = db.query(TABLE, new String[]{"entity", "payload"},
                "_id=?", args(trashId), null, null, null)) {
            if (!c.moveToFirst()) {
                return restored;
            }
            restored.entity = c.getString(0);
            JSONArray rows = new JSONArray(c.getString(1));

            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                if (!DatabaseHelper.TRANSACTION_TABLE.equals(row.getString("table"))) {
                    continue;
                }
                JSONObject values = row.getJSONObject("values");
                if (!stillThere(db, DatabaseHelper.ACCOUNT_TABLE, values, "from_account_id")
                        || !stillThere(db, DatabaseHelper.ACCOUNT_TABLE, values, "to_account_id")) {
                    restored.outcome = Outcome.NO_ACCOUNT;
                    return restored;
                }
            }

            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                JSONObject values = row.getJSONObject("values");
                if (DatabaseHelper.TRANSACTION_TABLE.equals(row.getString("table"))) {
                    restored.lostSomething |= drop(db, DatabaseHelper.CATEGORY_TABLE, values, "category_id");
                    restored.lostSomething |= drop(db, DatabaseHelper.PAYEE_TABLE, values, "payee_id");
                    restored.lostSomething |= drop(db, DatabaseHelper.PROJECT_TABLE, values, "project_id");
                    restored.lostSomething |= drop(db, DatabaseHelper.LOCATIONS_TABLE, values, "location_id");
                }
                ContentValues cv = new ContentValues();
                for (java.util.Iterator<String> it = values.keys(); it.hasNext(); ) {
                    String key = it.next();
                    cv.put(key, values.getString(key));
                }
                db.insertWithOnConflict(row.getString("table"), null, cv,
                        SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.delete(TABLE, "_id=?", args(trashId));
            db.setTransactionSuccessful();
            restored.outcome = Outcome.DONE;
        } catch (Exception e) {
            Log.e(TAG, "could not restore " + trashId, e);
            restored.outcome = Outcome.FAILED;
        } finally {
            db.endTransaction();
        }
        return restored;
    }

    /** Whether what a column points at is still in the database. Zero points at nothing. */
    private static boolean stillThere(SQLiteDatabase db, String table,
                                      JSONObject values, String column) throws Exception {
        if (!values.has(column)) {
            return true;
        }
        long id = parse(values.getString(column));
        return id <= 0 || exists(db, table, id);
    }

    /** Clears a column whose row has gone. Returns true if it had to. */
    private static boolean drop(SQLiteDatabase db, String table,
                                JSONObject values, String column) throws Exception {
        if (stillThere(db, table, values, column)) {
            return false;
        }
        values.put(column, "0");
        return true;
    }

    private static boolean exists(SQLiteDatabase db, String table, long id) {
        try (Cursor c = db.query(table, new String[]{"_id"}, "_id=?", args(id),
                null, null, null)) {
            return c.moveToFirst();
        }
    }

    private static long parse(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception e) {
            return 0;
        }
    }

    // ---------------------------------------------------------------- emptying

    public static void forget(SQLiteDatabase db, long trashId) {
        db.delete(TABLE, "_id=?", args(trashId));
    }

    public static void empty(SQLiteDatabase db) {
        db.delete(TABLE, null, null);
    }

    /** Throws away what has been in the bin longer than it was meant to be. */
    public static int purge(SQLiteDatabase db, int days) {
        if (days <= 0) {
            return 0;
        }
        long cutoff = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000;
        return db.delete(TABLE, "deleted_on<?", new String[]{String.valueOf(cutoff)});
    }

    // ----------------------------------------------------------------- reading

    public static List<Item> list(SQLiteDatabase db) {
        List<Item> items = new ArrayList<>();
        try (Cursor c = db.query(TABLE, null, null, null, null, null, "deleted_on desc")) {
            while (c.moveToNext()) {
                Item item = new Item();
                item.id = c.getLong(c.getColumnIndexOrThrow("_id"));
                item.entity = c.getString(c.getColumnIndexOrThrow("entity"));
                item.entityId = c.getLong(c.getColumnIndexOrThrow("entity_id"));
                item.title = c.getString(c.getColumnIndexOrThrow("title"));
                item.subtitle = c.getString(c.getColumnIndexOrThrow("subtitle"));
                item.deletedOn = c.getLong(c.getColumnIndexOrThrow("deleted_on"));
                item.author = c.getString(c.getColumnIndexOrThrow("author"));
                items.add(item);
            }
        }
        return items;
    }

    public static int count(SQLiteDatabase db) {
        try (Cursor c = db.rawQuery("select count(*) from " + TABLE, null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }
}
