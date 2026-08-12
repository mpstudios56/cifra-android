/*
 * This program is made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */
package tw.tib.financisto.sync;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import tw.tib.financisto.db.ChangeLog;
import tw.tib.financisto.db.DatabaseAdapter;
import tw.tib.financisto.db.DatabaseHelper;
import tw.tib.financisto.db.Uuids;
import tw.tib.financisto.utils.MyPreferences;

/**
 * One round of the exchange: put mine out, take theirs in.
 * <p>
 * There is no server and no conversation. Each phone leaves a file in a folder
 * both can reach and reads what the other left. Whatever keeps that folder in
 * step - Dropbox, Drive, a cable - is somebody else's job, and its speed is the
 * speed of all this. The app cannot be quicker than the folder.
 * <p>
 * Everything is decided by identifiers, never by row numbers, and an entry
 * already seen is recognised by its own identifier: applying a change twice
 * would double somebody's rent.
 */
public class SyncEngine {

    private static final String TAG = "SyncEngine";

    /** What a round did, in the words the screen needs. */
    public static class Result {
        public boolean ran;
        public int sent;
        public int received;
        /** Entries that could not be applied, with the reason, at most a few. */
        public final List<String> skipped = new ArrayList<>();
        public String problem;
    }

    private SyncEngine() {
    }

    public static synchronized Result run(Context context, DatabaseAdapter db) {
        Result result = new Result();
        String folderUri = MyPreferences.getSyncFolder();
        if (folderUri.isEmpty()) {
            result.problem = "no folder";
            return result;
        }
        SyncFolder folder = SyncFolder.open(context, folderUri);
        if (folder == null) {
            result.problem = "folder unreachable";
            return result;
        }

        String me = MyPreferences.getSyncDeviceId();
        SQLiteDatabase database = db.db();

        // Anything written before sharing existed still needs a name before it
        // can be spoken about.
        Uuids.fillBlanks(database);

        result.received = takeIn(database, folder.readOthers(me), result);
        result.sent = putOut(database, folder, me);
        result.ran = true;

        if (result.received > 0) {
            // The money moved, so the figures are made again. Once per round,
            // not once per entry.
            db.recalculateAccountsBalances();
            db.rebuildRunningBalances();
        }
        MyPreferences.setSyncLastRun(System.currentTimeMillis());
        return result;
    }

    // -------------------------------------------------------------- putting out

    /** Writes out every change made here that carries something to say. */
    private static int putOut(SQLiteDatabase db, SyncFolder folder, String me) {
        List<String> lines = new ArrayList<>();
        try (Cursor c = db.query(ChangeLog.TABLE, null, "device=? and payload<>''",
                new String[]{me}, null, null, "made_on asc")) {
            while (c.moveToNext()) {
                try {
                    JSONObject o = new JSONObject();
                    o.put("change", c.getString(c.getColumnIndexOrThrow("change_uuid")));
                    o.put("device", me);
                    o.put("author", c.getString(c.getColumnIndexOrThrow("author")));
                    o.put("made_on", c.getLong(c.getColumnIndexOrThrow("made_on")));
                    o.put("entity", c.getString(c.getColumnIndexOrThrow("entity")));
                    o.put("operation", c.getString(c.getColumnIndexOrThrow("operation")));
                    o.put("title", c.getString(c.getColumnIndexOrThrow("title")));
                    o.put("subtitle", c.getString(c.getColumnIndexOrThrow("subtitle")));
                    o.put("payload", c.getString(c.getColumnIndexOrThrow("payload")));
                    lines.add(o.toString());
                } catch (Exception e) {
                    Log.e(TAG, "could not write out a change", e);
                }
            }
        }
        return folder.write(me, lines) ? lines.size() : 0;
    }

    // --------------------------------------------------------------- taking in

    private static int takeIn(SQLiteDatabase db, List<String> lines, Result result) {
        int applied = 0;
        for (String line : lines) {
            try {
                JSONObject o = new JSONObject(line);
                String change = o.optString("change", "");
                if (change.isEmpty() || seen(db, change)) {
                    continue;
                }
                String entity = o.optString("entity", "");
                if (!DatabaseHelper.TRANSACTION_TABLE.equals(entity)) {
                    // Only movements travel for now. An account or a category
                    // arriving on its own would have to be created here, and
                    // creating things in somebody's ledger unasked is a
                    // different decision from copying a payment across.
                    continue;
                }
                db.beginTransaction();
                try {
                    String why = SyncPayload.apply(db, o.optString("operation", ""),
                            o.optString("payload", "{}"));
                    if (why != null) {
                        if (result.skipped.size() < 5) {
                            result.skipped.add(o.optString("title", "") + " — " + why);
                        }
                    } else {
                        applied++;
                    }
                    // Written down either way: a change we could not apply is
                    // still a change we have seen, and trying it again on every
                    // round would report the same problem for ever.
                    remember(db, o);
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            } catch (Exception e) {
                Log.e(TAG, "could not take in a line", e);
            }
        }
        return applied;
    }

    private static boolean seen(SQLiteDatabase db, String changeUuid) {
        try (Cursor c = db.query(ChangeLog.TABLE, new String[]{"_id"},
                "change_uuid=?", new String[]{changeUuid}, null, null, null)) {
            return c.moveToFirst();
        }
    }

    /**
     * Keeps the other phone's entry in our own record, with their name on it -
     * so the record of changes reads as one story told by two people rather
     * than as two halves neither of which makes sense alone.
     */
    private static void remember(SQLiteDatabase db, JSONObject o) {
        ContentValues v = new ContentValues();
        v.put("change_uuid", o.optString("change", ""));
        v.put("device", o.optString("device", ""));
        v.put("author", o.optString("author", ""));
        v.put("made_on", o.optLong("made_on", System.currentTimeMillis()));
        v.put("entity", o.optString("entity", ""));
        v.put("entity_id", 0);
        v.put("operation", o.optString("operation", ""));
        v.put("title", o.optString("title", ""));
        v.put("subtitle", o.optString("subtitle", ""));
        // Not kept: it has been applied, and holding a second copy of every
        // movement the other phone ever made is a second database.
        v.put("payload", "");
        db.insert(ChangeLog.TABLE, null, v);
    }
}
