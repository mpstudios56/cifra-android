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
import java.util.UUID;

/**
 * Who changed what, and when.
 * <p>
 * When two people keep one ledger and both can do everything, the record of who
 * did what is what takes the place of permissions: nothing is forbidden, but
 * nothing is anonymous either. "Chi ha messo questi ottanta euro?" has an
 * answer, and that turns out to matter more between two people than any set of
 * rules about who may press what.
 * <p>
 * Each entry carries an identifier of its own, made here and never reused. That
 * identifier is what will let two phones tell an entry they have already seen
 * from one they have not, when the exchange between them is built - which is why
 * it is written now rather than added later to a table full of rows that never
 * had one.
 */
public class ChangeLog {

    private static final String TAG = "ChangeLog";
    public static final String TABLE = "change_log";

    public static final String INSERT = "INSERT";
    public static final String UPDATE = "UPDATE";
    public static final String DELETE = "DELETE";

    /** One line of the record. */
    public static class Entry {
        public long id;
        public String uuid;
        public String device;
        public String author;
        public long madeOn;
        public String entity;
        public long entityId;
        public String operation;
        public String title;
        public String subtitle;
    }

    private ChangeLog() {
    }

    /**
     * Notes a change. Never throws: a ledger that refuses to record a payment
     * because it could not write its own diary would be worse than one with a
     * gap in the diary.
     */
    public static void record(SQLiteDatabase db, String device, String author,
                              String entity, long entityId, String operation,
                              String title, String subtitle) {
        record(db, device, author, entity, entityId, operation, title, subtitle, "");
    }

    /**
     * @param payload what the other phone needs to put this change back, or
     *                empty for a change that stays here
     */
    public static void record(SQLiteDatabase db, String device, String author,
                              String entity, long entityId, String operation,
                              String title, String subtitle, String payload) {
        try {
            ContentValues values = new ContentValues();
            values.put("change_uuid", UUID.randomUUID().toString());
            values.put("device", device == null ? "" : device);
            values.put("author", author == null ? "" : author);
            values.put("made_on", System.currentTimeMillis());
            values.put("entity", entity);
            values.put("entity_id", entityId);
            values.put("operation", operation);
            values.put("title", title == null ? "" : title);
            values.put("subtitle", subtitle == null ? "" : subtitle);
            values.put("payload", payload == null ? "" : payload);
            db.insert(TABLE, null, values);
        } catch (Exception e) {
            Log.e(TAG, "could not record " + operation + " on " + entity + " " + entityId, e);
        }
    }

    /** The most recent changes, newest first. */
    public static List<Entry> list(SQLiteDatabase db, int limit) {
        List<Entry> entries = new ArrayList<>();
        try (Cursor c = db.query(TABLE, null, null, null, null, null,
                "made_on desc, _id desc", String.valueOf(limit))) {
            while (c.moveToNext()) {
                Entry e = new Entry();
                e.id = c.getLong(c.getColumnIndexOrThrow("_id"));
                e.uuid = c.getString(c.getColumnIndexOrThrow("change_uuid"));
                e.device = c.getString(c.getColumnIndexOrThrow("device"));
                e.author = c.getString(c.getColumnIndexOrThrow("author"));
                e.madeOn = c.getLong(c.getColumnIndexOrThrow("made_on"));
                e.entity = c.getString(c.getColumnIndexOrThrow("entity"));
                e.entityId = c.getLong(c.getColumnIndexOrThrow("entity_id"));
                e.operation = c.getString(c.getColumnIndexOrThrow("operation"));
                e.title = c.getString(c.getColumnIndexOrThrow("title"));
                e.subtitle = c.getString(c.getColumnIndexOrThrow("subtitle"));
                entries.add(e);
            }
        } catch (Exception e) {
            Log.e(TAG, "could not read the record", e);
        }
        return entries;
    }

    /**
     * Throws away what is older than the kept period. A diary nobody prunes is
     * a second copy of the ledger growing quietly in the corner.
     */
    public static int purge(SQLiteDatabase db, int days) {
        if (days <= 0) {
            return 0;
        }
        long cutoff = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000;
        return db.delete(TABLE, "made_on<?", new String[]{String.valueOf(cutoff)});
    }

    public static void clear(SQLiteDatabase db) {
        db.delete(TABLE, null, null);
    }
}
