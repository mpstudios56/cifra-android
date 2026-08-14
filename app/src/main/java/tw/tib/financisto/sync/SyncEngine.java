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
        resignOldArrivals(database);

        // One round per person: their own file, holding only what is theirs.
        // The pairs are kept apart by which file each can read rather than by a
        // list of names inside the lines - what somebody is not meant to see is
        // then not written where they can reach it at all.
        List<People.Person> people = People.all(database);
        if (people.isEmpty()) {
            result.problem = "nobody";
            MyPreferences.setSyncLastRun(System.currentTimeMillis());
            return result;
        }
        for (People.Person person : people) {
            List<String> theirs = folder.readPair(myName, person.mark, me);
            Log.i(TAG, "round with " + person.label() + ": " + theirs.size() + " lines");
            result.received += takeIn(database, theirs, result, person.mark);
            result.sent += putOut(database, folder, me, myName, person);
        }
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

    /**
     * Puts the pair code on movements that arrived before signatures used one.
     * <p>
     * They carry whatever the sender's phone was called at the time, which
     * matches nobody here, so they showed in the spare colour instead of the
     * colour chosen for the person who wrote them. Each one is re-signed with
     * the code of the person its account is held with - which is who sent it.
     */
    private static void resignOldArrivals(SQLiteDatabase db) {
        String sql = "update transactions set created_by = ("
                + " select s.mark from shared_with s"
                + " inner join account a on a.uuid = s.uuid"
                + " where a._id = transactions.from_account_id limit 1)"
                + " where created_by <> '' and created_by <> ?"
                + " and created_by not in (select mark from person)"
                + " and exists (select 1 from shared_with s2"
                + "   inner join account a2 on a2.uuid = s2.uuid"
                + "   where a2._id = transactions.from_account_id)";
        try {
            db.execSQL(sql, new Object[]{MyPreferences.getSyncDeviceId()});
        } catch (Exception e) {
            Log.e(TAG, "could not re-sign what arrived before codes", e);
        }
    }

    // -------------------------------------------------------------- putting out

    /** Writes out every change made here that carries something to say. */
    private static int putOut(SQLiteDatabase db, SyncFolder folder, String me, String myName,
                              People.Person person) {
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
        List<String> things = SyncEntities.lines(db, person.mark);
        lines.addAll(things);
        Log.i(TAG, "sending " + things.size() + " shared things to " + person.label());
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
                    String payload = c.getString(c.getColumnIndexOrThrow("payload"));
                    // Written before movements were named on demand: no
                    // identifier, so the far end can do nothing with it but
                    // refuse it, round after round. Emptied here so it stops
                    // travelling and stops being reported.
                    if (!hasIdentifier(payload)) {
                        forget(db, c.getString(c.getColumnIndexOrThrow("change_uuid")));
                        continue;
                    }
                    if (!SyncPayload.isFor(db, payload, person.mark)) {
                        continue;
                    }
                    o.put("payload", payload);
                    lines.add(o.toString());
                } catch (Exception e) {
                    Log.e(TAG, "could not write out a change", e);
                }
            }
        }
        // Written only when it would say something different from what is
        // already there. A round used to rewrite the whole file every time -
        // two hundred identical lines, four seconds, and a cloud folder told to
        // sync a file that had not changed. Now the same content is recognised
        // and left alone, so the folder moves only when something has happened.
        if (unchangedSinceLastTime(person.mark, lines)) {
            Log.i(TAG, "nothing new for " + person.label() + ", file left as it is");
            return 0;
        }
        boolean written = folder.write(myName, person.mark, me, lines);
        Log.i(TAG, "wrote " + lines.size() + " lines: " + (written ? "yes" : "FAILED"));
        if (written) {
            rememberWhatWasWritten(person.mark, lines);
        }
        return written ? lines.size() : 0;
    }

    /**
     * Whether the file for this person would come out exactly as it went in last
     * time.
     * <p>
     * Compared by a number worked out from the content rather than by keeping a
     * copy of it: the file runs to hundreds of lines and there is no reason to
     * hold a second one in the settings. Every few hours the answer is "no"
     * whatever the content says, so that a file lost from the cloud, or a folder
     * emptied by hand, comes back on its own instead of never being written
     * again.
     */
    private static final long WRITE_ANYWAY_AFTER_MS = 6 * 60 * 60 * 1000L;

    private static boolean unchangedSinceLastTime(String mark, List<String> lines) {
        long since = System.currentTimeMillis() - MyPreferences.getSyncLastWrite(mark);
        if (since > WRITE_ANYWAY_AFTER_MS) {
            return false;
        }
        return MyPreferences.getSyncLastFingerprint(mark) == fingerprint(lines);
    }

    private static void rememberWhatWasWritten(String mark, List<String> lines) {
        MyPreferences.setSyncLastWritten(mark, fingerprint(lines),
                System.currentTimeMillis());
    }

    private static long fingerprint(List<String> lines) {
        long h = 1125899906842597L;
        for (String line : lines) {
            h = 31 * h + line.hashCode();
        }
        return 31 * h + lines.size();
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

    /** Whether a written-out change carries the name of what it is about. */
    private static boolean hasIdentifier(String payload) {
        if (payload == null || payload.isEmpty()) {
            return false;
        }
        try {
            return !new JSONObject(payload).optString("uuid", "").isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /** Leaves the line in the record, with nothing left to send. */
    private static void forget(SQLiteDatabase db, String changeUuid) {
        try {
            ContentValues v = new ContentValues();
            v.put("payload", "");
            db.update(ChangeLog.TABLE, v, "change_uuid=?", new String[]{changeUuid});
        } catch (Exception e) {
            Log.e(TAG, "could not clear " + changeUuid, e);
        }
    }

    // --------------------------------------------------------------- taking in

    private static int takeIn(SQLiteDatabase db, List<String> lines, Result result, String from) {
        int applied = 0;

        // Two passes over the same file. The things a movement refers to have to
        // be here before the movement is, and one file holds both.
        int things = 0;
        for (String line : lines) {
            try {
                JSONObject o = new JSONObject(line);
                if (o.optString("thing", "").isEmpty() || !addressedToUs(o)) {
                    continue;
                }
                db.beginTransaction();
                try {
                    if (SyncEntities.take(db, o, from)) {
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
                if (!addressedToUs(o)) {
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
                            o.optString("author", ""), o.optString("title", ""), from);
                    if (why != null) {
                        // In the log as well as on screen: five reasons in a
                        // toast is what somebody sees, and the sixth is the one
                        // that explains the other five.
                        Log.i(TAG, "skipped '" + o.optString("title", "") + "': " + why);
                        if (result.skipped.size() < 5) {
                            result.skipped.add(o.optString("title", "") + " — " + why);
                        }
                    } else {
                        applied++;
                    }
                    // Only what actually landed is written down as seen. It
                    // used to be written either way, on the grounds that a
                    // change we cannot apply is still one we have seen - but
                    // that made every failure permanent: the account it needed
                    // arrived a minute later and the movement never came back
                    // to be tried again. The file is rewritten whole at every
                    // round, so a line that failed is still there next time.
                    if (why == null) {
                        remember(db, o);
                    }
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

    /**
     * Whether a line is for this phone.
     * <p>
     * It is, if it is in a file this phone can read: one file per pair, found
     * by the code the two of them share, is what keeps two sharings apart. The
     * list of recipients inside each line is left over from when everybody
     * wrote into one file, and it was being compared against the phone's own
     * identifier while the lines carry the pair code - so every addressed line
     * was refused on arrival, accounts included, and the movements that needed
     * those accounts had nowhere to go.
     */
    private static boolean addressedToUs(JSONObject o) {
        return true;
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
