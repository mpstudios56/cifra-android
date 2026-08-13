/*
 * This program is made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */
package tw.tib.financisto.sync;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import tw.tib.financisto.db.DatabaseHelper;

/**
 * What each person has agreed to share.
 * <p>
 * Not everything, and not decided by the app. Two people who split the shopping
 * do not necessarily want the other to see the personal account, and an app that
 * copied everything across the moment a folder was chosen would be making that
 * decision for them - once, silently, and irreversibly.
 * <p>
 * So it is a list, and it starts empty. Sharing an account shares the movements
 * on it; sharing a category or a name only means the other phone will have it to
 * hand when a movement mentions it.
 */
public class SharedThings {

    public static final String ACCOUNT = DatabaseHelper.ACCOUNT_TABLE;
    public static final String CATEGORY = DatabaseHelper.CATEGORY_TABLE;
    public static final String PAYEE = DatabaseHelper.PAYEE_TABLE;
    public static final String PROJECT = DatabaseHelper.PROJECT_TABLE;
    public static final String LOCATION = DatabaseHelper.LOCATIONS_TABLE;

    /**
     * Only accounts are chosen by hand.
     * <p>
     * A category or a payee on its own says nothing about money: it is a word.
     * What decides whether the other person sees a payment is the account it is
     * on, and asking four questions where one will do is three questions too
     * many. Everything a shared account's movements mention goes with them.
     */
    public static final String[] KINDS = {ACCOUNT, CATEGORY, PAYEE, PROJECT, LOCATION};

    private static final String TABLE = "shared_thing";

    private SharedThings() {
    }

    public static Set<String> shared(SQLiteDatabase db, String kind) {
        Set<String> uuids = new HashSet<>();
        try (Cursor c = db.query(TABLE, new String[]{"uuid"}, "kind=?",
                new String[]{kind}, null, null, null)) {
            while (c.moveToNext()) {
                uuids.add(c.getString(0));
            }
        } catch (Exception e) {
            // An empty answer means "nothing shared", which is the safe answer.
        }
        return uuids;
    }

    public static boolean isShared(SQLiteDatabase db, String kind, String uuid) {
        if (uuid == null || uuid.isEmpty()) {
            return false;
        }
        try (Cursor c = db.query(TABLE, new String[]{"uuid"}, "kind=? and uuid=?",
                new String[]{kind, uuid}, null, null, null)) {
            return c.moveToFirst();
        } catch (Exception e) {
            return false;
        }
    }

    public static void set(SQLiteDatabase db, String kind, String uuid, boolean shared) {
        if (uuid == null || uuid.isEmpty()) {
            return;
        }
        if (shared) {
            ContentValues v = new ContentValues();
            v.put("kind", kind);
            v.put("uuid", uuid);
            db.insertWithOnConflict(TABLE, null, v, SQLiteDatabase.CONFLICT_REPLACE);
        } else {
            db.delete(TABLE, "kind=? and uuid=?", new String[]{kind, uuid});
        }
    }

    /**
     * Whatever arrives from the other phone is shared here too, without being
     * asked. They have already decided to share it; refusing to keep it would
     * mean holding a movement that mentions something we pretend not to have.
     */
    public static void adopt(SQLiteDatabase db, String kind, String uuid) {
        set(db, kind, uuid, true);
    }

    /**
     * The categories, names, projects and places that the movements on shared
     * accounts actually mention - worked out each time rather than kept in a
     * list, so that using a new category on a shared account is enough to send
     * it, without anybody having to remember to tick it.
     */
    public static Set<String> dependents(SQLiteDatabase db, String kind) {
        Set<String> uuids = new HashSet<>();
        String column;
        if (CATEGORY.equals(kind)) column = "category_id";
        else if (PAYEE.equals(kind)) column = "payee_id";
        else if (PROJECT.equals(kind)) column = "project_id";
        else if (LOCATION.equals(kind)) column = "location_id";
        else return uuids;

        String sql = "select distinct e.uuid from transactions t"
                + " inner join " + kind + " e on e._id = t." + column
                + " inner join account a on a._id = t.from_account_id"
                + " inner join shared_thing s on s.uuid = a.uuid and s.kind = ?"
                + " where t." + column + " > 0";
        try (Cursor c = db.rawQuery(sql, new String[]{ACCOUNT})) {
            while (c.moveToNext()) {
                String uuid = c.getString(0);
                if (uuid != null && !uuid.isEmpty()) {
                    uuids.add(uuid);
                }
            }
        } catch (Exception e) {
            // Nothing found means nothing goes: safe either way.
        }
        return uuids;
    }

    /**
     * The identifiers of the accounts being shared, in one query.
     * <p>
     * For drawing a list: asking row by row would be one query per row on every
     * scroll.
     */
    public static java.util.Set<Long> sharedAccountIds(SQLiteDatabase db) {
        java.util.Set<Long> ids = new HashSet<>();
        String sql = "select a._id from account a"
                + " inner join " + TABLE + " s on s.uuid = a.uuid and s.kind = ?";
        try (Cursor c = db.rawQuery(sql, new String[]{ACCOUNT})) {
            while (c.moveToNext()) {
                ids.add(c.getLong(0));
            }
        } catch (Exception e) {
            // No table yet, or nothing shared: an empty answer is the right one.
        }
        return ids;
    }

    /** Whether movements on this account travel. */
    public static boolean isAccountShared(SQLiteDatabase db, long accountId) {
        try (Cursor c = db.rawQuery(
                "select 1 from account a inner join shared_thing s"
                        + " on s.uuid = a.uuid and s.kind = ? where a._id = ?",
                new String[]{ACCOUNT, String.valueOf(accountId)})) {
            return c.moveToFirst();
        } catch (Exception e) {
            return false;
        }
    }

    /** One thing that can be shared, as the screen needs it. */
    public static class Thing {
        public final long id;
        public final String uuid;
        public final String name;
        public boolean shared;

        Thing(long id, String uuid, String name, boolean shared) {
            this.id = id;
            this.uuid = uuid;
            this.name = name;
            this.shared = shared;
        }
    }

    /** Everything of one kind, with what has been shared already marked. */
    public static List<Thing> list(SQLiteDatabase db, String kind) {
        List<Thing> things = new ArrayList<>();
        Set<String> already = shared(db, kind);
        String order = nameColumn(kind);
        String where = CATEGORY.equals(kind) ? "_id > 0" : null;
        try (Cursor c = db.query(kind, new String[]{"_id", "uuid", nameColumn(kind)},
                where, null, null, null, order)) {
            while (c.moveToNext()) {
                String uuid = c.getString(1);
                things.add(new Thing(c.getLong(0), uuid,
                        c.getString(2) == null ? "" : c.getString(2),
                        already.contains(uuid)));
            }
        } catch (Exception e) {
            // A kind we cannot read is a kind with nothing to show.
        }
        return things;
    }

    /** Places call it a name; everything else calls it a title. */
    /**
     * The column holding what a row is called.
     * <p>
     * Every one of them is "title". Places had a "name" until it was replaced
     * in February 2024, and this went on asking for the old one - so every
     * query about places failed quietly and no place was ever shared or ever
     * proposed for merging.
     */
    public static String nameColumn(String kind) {
        return "title";
    }
}
