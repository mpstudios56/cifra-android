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

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import tw.tib.financisto.db.DatabaseHelper;

/**
 * The things a movement points at: accounts, categories, names, places.
 * <p>
 * Sent as they are rather than as a history of changes. There is no interesting
 * story in the life of a category, and sending the current state means a phone
 * that joins late, or that lost its copy, catches up on the first round instead
 * of needing every change ever made to it.
 * <p>
 * <b>Never joined to what is here by its name.</b> Two people each have a
 * "Conto corrente", and after the first round each has two. That looks like a
 * fault and is in fact the safe answer: "Casa" can be the mortgage to one of
 * them and the cleaning to the other, and joining two things because they are
 * spelled alike is a guess that cannot be undone once the movements have moved.
 * The Merge screen proposes the pairs and somebody decides.
 */
public class SyncEntities {

    private static final String TAG = "SyncEntities";

    private SyncEntities() {
    }

    // ------------------------------------------------------------------ sending

    /** A line for everything this phone has agreed to share. */
    public static List<String> lines(SQLiteDatabase db) {
        return lines(db, null);
    }

    /**
     * @param code the pair this file is for, or null for everything shared
     */
    public static List<String> lines(SQLiteDatabase db, String code) {
        List<String> lines = new ArrayList<>();
        for (String kind : SharedThings.KINDS) {
            for (SharedThings.Thing thing : SharedThings.list(db, kind)) {
                if (!thing.shared || thing.uuid == null || thing.uuid.isEmpty()) {
                    continue;
                }
                // An account goes only into the file of the people it is held
                // with. The labels go into every file: they are names, they
                // arrive with the movements that use them, and an account
                // nobody in this pair can see never sends a movement anyway.
                if (code != null && SharedThings.ACCOUNT.equals(kind)
                        && !SharedWith.reaches(db, thing.uuid, code)) {
                    continue;
                }
                try {
                    JSONObject o = new JSONObject();
                    o.put("thing", kind);
                    o.put("uuid", thing.uuid);
                    o.put("name", thing.name);
                    if (SharedThings.ACCOUNT.equals(kind)) {
                        o.put("currency", currencyOf(db, thing.id));
                        o.put("type", stringOf(db, kind, thing.id, "type"));
                        // Who this account is for. Absent means everybody in
                        // the group. The labels below carry no list: they are
                        // names, they arrive with the movements that use them,
                        // and working out the union of the recipients of every
                        // account that mentions a category would be a query per
                        // label on every round.
                        java.util.Set<String> to = SharedWith.recipientsOf(db, thing.uuid);
                        if (!to.isEmpty()) {
                            o.put("to", new org.json.JSONArray(to));
                        }
                    }
                    lines.add(o.toString());
                } catch (Exception e) {
                    Log.e(TAG, "could not write out " + kind + " " + thing.uuid, e);
                }
            }
        }
        return lines;
    }

    // ----------------------------------------------------------------- receiving

    /**
     * Makes sure the thing described is here, under the identifier the other
     * phone knows it by.
     *
     * @return true when something was created or renamed
     */
    public static boolean take(SQLiteDatabase db, JSONObject o) {
        String kind = o.optString("thing", "");
        String uuid = o.optString("uuid", "");
        String name = o.optString("name", "");
        if (kind.isEmpty() || uuid.isEmpty()) {
            return false;
        }

        // Already known by that identifier: nothing to do but keep sharing it.
        if (idByUuid(db, kind, uuid) > 0) {
            SharedThings.adopt(db, kind, uuid);
            return false;
        }

        // Deliberately not joined to whatever is here under the same name.
        // "Casa" can be the mortgage to one person and the cleaning to the
        // other, and a wrong guess inside somebody's accounts does not come
        // undone. The two arrive side by side and the Merge screen proposes
        // putting them together, which somebody then decides.
        boolean made = create(db, kind, uuid, name, o);
        if (made) {
            SharedThings.adopt(db, kind, uuid);
        }
        return made;
    }

    private static boolean create(SQLiteDatabase db, String kind, String uuid,
                                  String name, JSONObject o) {
        try {
            ContentValues v = new ContentValues();
            v.put(SharedThings.nameColumn(kind), name);
            v.put("uuid", uuid);
            if (SharedThings.ACCOUNT.equals(kind)) {
                long currency = currencyByName(db, o.optString("currency", ""));
                if (currency <= 0) {
                    // Without a currency an account is not an account. Better to
                    // leave it out and say so than to invent one.
                    return false;
                }
                v.put("currency_id", currency);
                v.put("type", o.optString("type", "CASH"));
                v.put("is_active", 1);
                v.put("is_include_into_totals", 1);
                v.put("is_include_into_reports", 1);
                v.put("total_amount", 0);
                v.put("creation_date", System.currentTimeMillis());
                v.put("last_transaction_date", 0);
                v.put("sort_order", 0);
            } else if (SharedThings.CATEGORY.equals(kind)) {
                // Placed at the end of the tree, at the top level. The shape of
                // somebody else's categories is theirs; what has to match is the
                // name a movement points at.
                int right = maxRight(db);
                v.put("left", right + 1);
                v.put("right", right + 2);
                v.put("type", 0);
            } else if (SharedThings.LOCATION.equals(kind)) {
                v.put("datetime", System.currentTimeMillis());
                v.put("is_payee", 0);
            }
            return db.insert(kind, null, v) > 0;
        } catch (Exception e) {
            Log.e(TAG, "could not create " + kind + " " + name, e);
            return false;
        }
    }

    // ------------------------------------------------------------------ lookups

    private static long idByUuid(SQLiteDatabase db, String table, String uuid) {
        try (Cursor c = db.query(table, new String[]{"_id"}, "uuid=?",
                new String[]{uuid}, null, null, null)) {
            return c.moveToFirst() ? c.getLong(0) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static String currencyOf(SQLiteDatabase db, long accountId) {
        try (Cursor c = db.rawQuery("select cur.name from account a"
                + " inner join currency cur on cur._id = a.currency_id where a._id=?",
                new String[]{String.valueOf(accountId)})) {
            return c.moveToFirst() && c.getString(0) != null ? c.getString(0) : "";
        } catch (Exception e) {
            return "";
        }
    }

    private static long currencyByName(SQLiteDatabase db, String name) {
        if (name == null || name.isEmpty()) {
            return homeCurrency(db);
        }
        try (Cursor c = db.query(DatabaseHelper.CURRENCY_TABLE, new String[]{"_id"},
                "name=?", new String[]{name}, null, null, null)) {
            if (c.moveToFirst()) {
                return c.getLong(0);
            }
        } catch (Exception e) {
            // fall through
        }
        return homeCurrency(db);
    }

    private static long homeCurrency(SQLiteDatabase db) {
        try (Cursor c = db.query(DatabaseHelper.CURRENCY_TABLE, new String[]{"_id"},
                "is_default=1", null, null, null, null)) {
            return c.moveToFirst() ? c.getLong(0) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int maxRight(SQLiteDatabase db) {
        try (Cursor c = db.rawQuery("select max(right) from category", null)) {
            return c.moveToFirst() ? c.getInt(0) : 1;
        } catch (Exception e) {
            return 1;
        }
    }

    private static String stringOf(SQLiteDatabase db, String table, long id, String column) {
        try (Cursor c = db.query(table, new String[]{column}, "_id=?",
                new String[]{String.valueOf(id)}, null, null, null)) {
            return c.moveToFirst() && c.getString(0) != null ? c.getString(0) : "";
        } catch (Exception e) {
            return "";
        }
    }
}
