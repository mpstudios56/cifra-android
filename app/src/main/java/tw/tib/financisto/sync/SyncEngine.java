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
        /** Pairs that might be one payment written down twice. */
        public int duplicates;
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
        String myName = MyPreferences.getSyncAuthor();
        SQLiteDatabase database = db.db();

        // Anything written before sharing existed still needs a name before it
        // can be spoken about.
        Uuids.fillBlanks(database);

        List<String> theirs = folder.readOthers(myName, me);
        Log.i(TAG, "round: " + theirs.size() + " lines from the other phone");
        result.received = takeIn(database, theirs, result);
        result.sent = putOut(database, folder, me, myName);
        result.ran = true;
        Log.i(TAG, "round done: " + result.received + " taken in, "
                + result.sent + " written out, " + result.skipped.size() + " skipped");

        if (result.received > 0) {
            // The money moved, so the figures are made again. Once per round,
            // not once per entry.
            db.recalculateAccountsBalances();
            db.rebuildRunningBalances();
            // And then: did the same payment arrive twice? Asked, never decided.
            result.duplicates = Duplicates.look(database);
        }
        MyPreferences.setSyncLastRun(System.currentTimeMillis());
        return result;
    }

    // -------------------------------------------------------------- putting out

    /** Writes out every change made here that carries something to say. */
    private static int putOut(SQLiteDatabase db, SyncFolder folder, String me, String myName) {
        List<String> lines = new ArrayList<>();
        // The accounts, categories, payees and places being shared go out first,
        // in the same file and ahead of the movements that refer to them. Without
        // them the other phone receives a payment pointing at an account it has
        // never heard of, and can do nothing but skip it - which is exactly what
        // it did, silently, for as long as this was left unconnected.
        // What the movements on shared accounts actually mention - the
        // categories, the payees, the places - is worked out here and marked as
        // shared before anything is written. The working out was in the program
        // from the start and was never called, which is why the other phone
        // received movements with no category, no payee and no place on them:
        // the labels were never sent, so the references pointed at nothing.
        adoptWhatTheMovementsMention(db);
        List<String> things = SyncEntities.lines(db);
        lines.addAll(things);
        Log.i(TAG, "sending " + things.size() + " shared things");
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
        boolean written = folder.write(myName, me, lines);
        Log.i(TAG, "wrote " + lines.size() + " lines: " + (written ? "yes" : "FAILED"));
        return written ? lines.size() : 0;
    }

    /**
     * Marks as shared everything the movements on shared accounts refer to.
     * <p>
     * Done at every round rather than once: using a category for the first time
     * on a shared account is enough to send it, with nobody having to remember
     * to tick anything.
     */
    private static void adoptWhatTheMovementsMention(SQLiteDatabase db) {
        int adopted = 0;
        for (String kind : SharedThings.KINDS) {
            if (SharedThings.ACCOUNT.equals(kind)) {
                // Accounts are shared by hand, on purpose: they are the choice.
                continue;
            }
            for (String uuid : SharedThings.dependents(db, kind)) {
                SharedThings.adopt(db, kind, uuid);
                adopted++;
            }
        }
        Log.i(TAG, "labels the shared accounts depend on: " + adopted);
    }

    // --------------------------------------------------------------- taking in

    private static int takeIn(SQLiteDatabase db, List<String> lines, Result result) {
        int applied = 0;

        // Two passes over the same file. The things a movement refers to have to
        // be here before the movement is, and one file holds both.
        int things = 0;
        for (String line : lines) {
            try {
                JSONObject o = new JSONObject(line);
                if (o.optString("thing", "").isEmpty()) {
                    continue;
                }
                db.beginTransaction();
                try {
                    if (SyncEntities.take(db, o)) {
                        things++;
                    }
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            } catch (Exception e) {
                Log.e(TAG, "could not take in a shared thing", e);
            }
        }
        Log.i(TAG, "shared things created or renamed: " + things);

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
                            o.optString("payload", "{}"),
                            o.optString("author", ""), o.optString("title", ""));
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
