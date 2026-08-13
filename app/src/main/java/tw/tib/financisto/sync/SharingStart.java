package tw.tib.financisto.sync;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import tw.tib.financisto.db.ChangeLog;
import tw.tib.financisto.db.DatabaseHelper;
import tw.tib.financisto.utils.MyPreferences;

/**
 * What the other person is told about an account at the moment it is shared.
 * <p>
 * The exchange carries changes, and a change is written down when somebody
 * makes one - so an account shared today sends only what happens from today.
 * That is right for a joint account opened now and wrong for one that has been
 * running for years: the other person opens their phone, sees the account, and
 * it is empty.
 * <p>
 * Three answers, and none of them is obviously the right one for everybody,
 * which is why it is asked rather than decided here.
 */
public class SharingStart {

    private static final String TAG = "SharingStart";

    /** Every movement the account has ever had. */
    public static final int EVERYTHING = 0;
    /** One opening line for what is in it today, then whatever comes next. */
    public static final int BALANCE_ONLY = 1;
    /** Nothing of the past at all. */
    public static final int FROM_NOW = 2;

    private SharingStart() {
    }

    /**
     * Writes out what was chosen, as entries waiting to go on the next round.
     *
     * @return how many movements were queued
     */
    public static int apply(SQLiteDatabase db, long accountId, String accountUuid,
                            String accountTitle, int choice) {
        if (choice == FROM_NOW) {
            return 0;
        }
        if (choice == BALANCE_ONLY) {
            return openingLine(db, accountId, accountUuid, accountTitle) ? 1 : 0;
        }
        return everything(db, accountId);
    }

    /**
     * Queues every movement of the account, as though each had just been
     * written.
     * <p>
     * The identifier of each is its own, so a movement that somehow reaches the
     * other phone twice is still recognised as one movement and not written
     * down twice.
     */
    private static int everything(SQLiteDatabase db, long accountId) {
        List<Long> ids = new ArrayList<>();
        try (Cursor c = db.query(DatabaseHelper.TRANSACTION_TABLE, new String[]{"_id"},
                "from_account_id=? and is_template=0 and parent_id=0",
                new String[]{String.valueOf(accountId)}, null, null, "datetime asc")) {
            while (c.moveToNext()) {
                ids.add(c.getLong(0));
            }
        } catch (Exception e) {
            Log.e(TAG, "could not read the account's movements", e);
            return 0;
        }

        int queued = 0;
        String device = MyPreferences.getSyncDeviceId();
        String author = MyPreferences.getSyncAuthor();
        for (Long id : ids) {
            if (alreadyQueued(db, id)) {
                // Already waiting to go: queueing it again would write the same
                // movement into the file twice, which costs the other phone a
                // second read of something it has and gains nobody anything.
                continue;
            }
            String payload = SyncPayload.of(db, id);
            if (payload == null) {
                continue;
            }
            ChangeLog.record(db, device, author, DatabaseHelper.TRANSACTION_TABLE, id,
                    ChangeLog.INSERT, titleOf(db, id), "", payload);
            queued++;
        }
        Log.i(TAG, "queued " + queued + " movements of account " + accountId);
        return queued;
    }

    /**
     * Queues a single line for what is in the account today.
     * <p>
     * It is made here and never written into this phone's own ledger: the
     * balance here already comes from the movements that produced it, and
     * adding a line for it would count the money twice. On the other phone it
     * is the account's starting figure, which is what was asked for.
     */
    private static boolean openingLine(SQLiteDatabase db, long accountId,
                                       String accountUuid, String accountTitle) {
        if (accountUuid == null || accountUuid.isEmpty()) {
            return false;
        }
        long balance = 0;
        try (Cursor c = db.query(DatabaseHelper.ACCOUNT_TABLE, new String[]{"total_amount"},
                "_id=?", new String[]{String.valueOf(accountId)}, null, null, null)) {
            if (c.moveToFirst()) {
                balance = c.getLong(0);
            }
        } catch (Exception e) {
            Log.e(TAG, "could not read the balance", e);
            return false;
        }
        if (balance == 0) {
            return false;
        }
        try {
            JSONObject o = new JSONObject();
            o.put("uuid", UUID.randomUUID().toString());
            o.put("from_account", accountUuid);
            o.put("to_account", "");
            o.put("category", "");
            o.put("payee", "");
            o.put("project", "");
            o.put("location", "");
            o.put("datetime", String.valueOf(System.currentTimeMillis()));
            o.put("from_amount", String.valueOf(balance));
            o.put("to_amount", "0");
            // Worded exactly as the app words it when somebody types an
            // opening balance into a new account, because it is the same thing:
            // "Saldo iniziale (Contanti)". A balance in this app is the sum of
            // its movements, so the figure travels as the movement that makes
            // it - which is what the account screen has always done.
            o.put("note", tw.tib.financisto.Application.getInstance()
                    .getString(tw.tib.financisto.R.string.opening_amount)
                    + " (" + accountTitle + ")");
            o.put("status", "UR");
            o.put("is_ccard_payment", "0");
            o.put("original_currency_id", "0");
            o.put("original_from_amount", "0");
            o.put("created_by", MyPreferences.getSyncAuthor());

            ChangeLog.record(db, MyPreferences.getSyncDeviceId(), MyPreferences.getSyncAuthor(),
                    DatabaseHelper.TRANSACTION_TABLE, 0, ChangeLog.INSERT,
                    accountTitle, "", o.toString());
            return true;
        } catch (Exception e) {
            Log.e(TAG, "could not make the opening line", e);
            return false;
        }
    }

    /** Whether this movement is already sitting in the queue with something to say. */
    private static boolean alreadyQueued(SQLiteDatabase db, long transactionId) {
        try (Cursor c = db.query(ChangeLog.TABLE, new String[]{"_id"},
                "entity=? and entity_id=? and payload<>''",
                new String[]{DatabaseHelper.TRANSACTION_TABLE, String.valueOf(transactionId)},
                null, null, null, "1")) {
            return c.moveToFirst();
        } catch (Exception e) {
            return false;
        }
    }

    private static String titleOf(SQLiteDatabase db, long transactionId) {
        try (Cursor c = db.query(DatabaseHelper.TRANSACTION_TABLE, new String[]{"note"},
                "_id=?", new String[]{String.valueOf(transactionId)}, null, null, null)) {
            if (c.moveToFirst() && c.getString(0) != null) {
                return c.getString(0);
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    /** Forgets what was queued for an account, when sharing is switched off again. */
    public static void unqueue(SQLiteDatabase db, long accountId) {
        try {
            ContentValues v = new ContentValues();
            v.put("payload", "");
            db.update(ChangeLog.TABLE, v, "entity=? and entity_id=?",
                    new String[]{DatabaseHelper.TRANSACTION_TABLE, String.valueOf(accountId)});
        } catch (Exception e) {
            Log.e(TAG, "could not unqueue", e);
        }
    }
}
