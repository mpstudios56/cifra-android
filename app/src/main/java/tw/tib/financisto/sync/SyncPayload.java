/*
 * This program is made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */
package tw.tib.financisto.sync;

import android.content.ContentValues;
import android.database.Cursor;
import android.util.Log;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONObject;

import tw.tib.financisto.db.DatabaseHelper;

/**
 * A movement written down so that the other phone can put it back.
 * <p>
 * Everything it points at is named by the identifier both phones agree on, not
 * by the row number: the number 57 means one thing here and another thing
 * there, and taking it across would attach the money to whatever happened to be
 * 57 on the other side. That is the kind of mistake nobody notices for weeks.
 * <p>
 * A movement whose account the other phone has never heard of is not applied.
 * There is no sensible guess: putting it on a different account would be
 * inventing, and inventing in somebody's accounts is worse than skipping.
 */
public class SyncPayload {

    private static final String TAG = "SyncPayload";

    private SyncPayload() {
    }

    /** The fields that make a movement what it is, and travel with it. */
    private static final String[] PLAIN = {
            "datetime", "from_amount", "to_amount", "note", "status",
            "is_ccard_payment", "original_currency_id", "original_from_amount",
    };

    /**
     * Writes out a movement, or null when it has gone from the database - which
     * is the ordinary case for a deletion: the row is already away, and what
     * the other phone needs is only its identifier.
     */
    public static String of(SQLiteDatabase db, long transactionId) {
        try (Cursor c = db.query(DatabaseHelper.TRANSACTION_TABLE, null,
                "_id=?", new String[]{String.valueOf(transactionId)}, null, null, null)) {
            if (!c.moveToFirst()) {
                return null;
            }
            long fromId = id(c, "from_account_id");
            long toId = id(c, "to_account_id");
            boolean fromShared = SharedThings.isAccountShared(db, fromId);
            boolean toShared = toId > 0 && SharedThings.isAccountShared(db, toId);
            if (!fromShared && !toShared) {
                // Neither end is shared: this is nobody else's business.
                return null;
            }

            JSONObject o = new JSONObject();
            o.put("uuid", string(c, "uuid"));

            if (fromShared) {
                o.put("from_account", uuidOf(db, DatabaseHelper.ACCOUNT_TABLE, fromId));
                // A transfer into an account the other person cannot see goes
                // as money leaving, with no destination named. Their balance
                // stays right - which is the point of sharing an account - and
                // the account that was not shared is not named anywhere.
                o.put("to_account", toShared
                        ? uuidOf(db, DatabaseHelper.ACCOUNT_TABLE, toId) : "");
            } else {
                // Money arriving into the shared account from one that is not:
                // sent as money arriving, with no origin named.
                o.put("from_account", uuidOf(db, DatabaseHelper.ACCOUNT_TABLE, toId));
                o.put("to_account", "");
            }
            o.put("category", uuidOf(db, DatabaseHelper.CATEGORY_TABLE, id(c, "category_id")));
            o.put("payee", uuidOf(db, DatabaseHelper.PAYEE_TABLE, id(c, "payee_id")));
            o.put("project", uuidOf(db, DatabaseHelper.PROJECT_TABLE, id(c, "project_id")));
            o.put("location", uuidOf(db, DatabaseHelper.LOCATIONS_TABLE, id(c, "location_id")));
            for (String field : PLAIN) {
                o.put(field, string(c, field));
            }
            if (!fromShared) {
                // Flipped round: what left an account they cannot see arrived
                // in the one they can, so the amount arrives positive and the
                // other side of it is dropped.
                o.put("from_amount", string(c, "to_amount"));
                o.put("to_amount", "0");
            } else if (!toShared) {
                o.put("to_amount", "0");
            }
            // Travels with it, so the other phone shows it in the writer's
            // colour rather than claiming it as its own.
            o.put("created_by", string(c, "created_by"));
            return o.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /** What the deletion of a movement carries: which one it was. */
    public static String forDeletion(String uuid) {
        try {
            return new JSONObject().put("uuid", uuid).toString();
        } catch (Exception e) {
            return "";
        }
    }

    // ----------------------------------------------------------------- applying

    /** Why a movement could not be put back, or null when it was. */
    public static String apply(SQLiteDatabase db, String operation, String payload) {
        return apply(db, operation, payload, "", "");
    }

    /**
     * @param author who made the change over there, so a deletion can say whose
     *               it was rather than a movement vanishing overnight
     */
    public static String apply(SQLiteDatabase db, String operation, String payload,
                               String author, String title) {
        try {
            JSONObject o = new JSONObject(payload);
            String uuid = o.optString("uuid", "");
            if (uuid.isEmpty()) {
                return "senza identificativo";
            }
            long existing = idOf(db, DatabaseHelper.TRANSACTION_TABLE, uuid);

            if ("DELETE".equals(operation)) {
                if (existing > 0) {
                    // Into the bin, not straight out. Every total is right at
                    // once, because the bin holds what is already gone from the
                    // accounts - and nothing of somebody else's work is
                    // destroyed by a phone doing what it was told from afar.
                    tw.tib.financisto.db.Trash.keep(db, DatabaseHelper.TRANSACTION_TABLE,
                            existing, title, "", author);
                    db.delete(DatabaseHelper.TRANSACTION_TABLE, "_id=?",
                            new String[]{String.valueOf(existing)});
                }
                return null;
            }

            long from = idOf(db, DatabaseHelper.ACCOUNT_TABLE, o.optString("from_account", ""));
            if (from <= 0) {
                return "il conto non esiste su questo telefono";
            }

            ContentValues v = new ContentValues();
            v.put("uuid", uuid);
            v.put("from_account_id", from);
            v.put("to_account_id", orZero(idOf(db, DatabaseHelper.ACCOUNT_TABLE, o.optString("to_account", ""))));
            v.put("category_id", orZero(idOf(db, DatabaseHelper.CATEGORY_TABLE, o.optString("category", ""))));
            v.put("payee_id", orZero(idOf(db, DatabaseHelper.PAYEE_TABLE, o.optString("payee", ""))));
            v.put("project_id", orZero(idOf(db, DatabaseHelper.PROJECT_TABLE, o.optString("project", ""))));
            v.put("location_id", orZero(idOf(db, DatabaseHelper.LOCATIONS_TABLE, o.optString("location", ""))));
            for (String field : PLAIN) {
                v.put(field, o.optString(field, ""));
            }
            // Whoever wrote it keeps it: an entry that came from over there is
            // theirs, and the list marks it as theirs.
            v.put("created_by", o.optString("created_by", ""));

            if (existing > 0) {
                db.update(DatabaseHelper.TRANSACTION_TABLE, v, "_id=?",
                        new String[]{String.valueOf(existing)});
            } else {
                db.insert(DatabaseHelper.TRANSACTION_TABLE, null, v);
            }
            return null;
        } catch (Exception e) {
            return "non si è capito cosa fosse";
        }
    }

    // ------------------------------------------------------------------ lookups

    /**
     * Whether a written-out movement belongs in this person's file.
     * <p>
     * Read from the payload rather than from the movement, because by the time
     * a deletion is being sent the movement is already gone.
     */
    public static boolean isFor(SQLiteDatabase db, String payload, String code) {
        if (payload == null || payload.isEmpty()) {
            return false;
        }
        try {
            org.json.JSONObject o = new org.json.JSONObject(payload);
            String account = o.optString("from_account", "");
            if (account.isEmpty()) {
                return true;
            }
            return SharedWith.reaches(db, account, code);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * The identifier the other phone will know this row by, made now if it has
     * not got one.
     * <p>
     * Rows are named in bulk at the start of a round of exchange, which is too
     * late for anything written between rounds: an account created and used
     * straight away had no name yet, so its movements went out saying they
     * belonged to "", and the other phone - quite rightly - could not place
     * them and dropped them. That is why the first movements after making an
     * account never arrived and later ones did.
     */
    private static String uuidOf(SQLiteDatabase db, String table, long id) {
        if (id <= 0) {
            return "";
        }
        try (Cursor c = db.query(table, new String[]{"uuid"}, "_id=?",
                new String[]{String.valueOf(id)}, null, null, null)) {
            if (!c.moveToFirst()) {
                return "";
            }
            String uuid = c.getString(0);
            if (uuid != null && !uuid.isEmpty()) {
                return uuid;
            }
        } catch (Exception e) {
            return "";
        }
        try {
            String made = java.util.UUID.randomUUID().toString();
            android.content.ContentValues v = new android.content.ContentValues();
            v.put("uuid", made);
            db.update(table, v, "_id=?", new String[]{String.valueOf(id)});
            return made;
        } catch (Exception e) {
            Log.e(TAG, "could not name " + table + " " + id, e);
            return "";
        }
    }

    private static long idOf(SQLiteDatabase db, String table, String uuid) {
        if (uuid == null || uuid.isEmpty()) {
            return 0;
        }
        try (Cursor c = db.query(table, new String[]{"_id"}, "uuid=?",
                new String[]{uuid}, null, null, null)) {
            return c.moveToFirst() ? c.getLong(0) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static long orZero(long id) {
        return Math.max(id, 0);
    }

    private static long id(Cursor c, String column) {
        int i = c.getColumnIndex(column);
        return i < 0 || c.isNull(i) ? 0 : c.getLong(i);
    }

    private static String string(Cursor c, String column) {
        int i = c.getColumnIndex(column);
        return i < 0 || c.isNull(i) ? "" : c.getString(i);
    }
}
