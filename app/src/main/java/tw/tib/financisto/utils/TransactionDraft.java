/*
 * This program is made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */
package tw.tib.financisto.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import tw.tib.financisto.model.Transaction;

/**
 * Keeps the half-filled transactions a user walked away from, so closing the screen
 * or having the app killed in the background does not throw the entry away.
 * <p>
 * Held in its own preferences file rather than the database: a draft is scratch
 * state, it must never appear in reports, totals or backups, and it has to survive
 * the process being killed without a transaction ever being written.
 */
public class TransactionDraft {

    private static final String TAG = "TransactionDraft";
    private static final String PREFS_NAME = "transaction_drafts";
    private static final String KEY_PREFIX = "draft_";

    /** A stored draft together with the id needed to replace or remove it. */
    public static class Entry {
        public final long id;
        public final Transaction transaction;

        Entry(long id, Transaction transaction) {
            this.id = id;
            this.transaction = transaction;
        }
    }

    private TransactionDraft() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static String key(long id) {
        return KEY_PREFIX + id;
    }

    /**
     * Stores a draft, replacing the one with this id. Pass a new id to add one.
     * Returns the id it was stored under, or 0 if it could not be stored.
     */
    public static long save(Context context, long id, Transaction transaction) {
        long draftId = id > 0 ? id : System.currentTimeMillis();
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
                out.writeObject(transaction);
            }
            prefs(context).edit()
                    .putString(key(draftId), Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP))
                    .apply();
            return draftId;
        } catch (Exception e) {
            // A draft is a convenience: failing to store one must never break the screen.
            Log.e(TAG, "could not save draft " + draftId, e);
            return 0;
        }
    }

    public static Transaction load(Context context, long id) {
        return decode(context, id, prefs(context).getString(key(id), null));
    }

    private static Transaction decode(Context context, long id, String encoded) {
        if (encoded == null) {
            return null;
        }
        try {
            byte[] bytes = Base64.decode(encoded, Base64.NO_WRAP);
            try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
                return (Transaction) in.readObject();
            }
        } catch (Exception e) {
            // Most likely written by an older version whose model has since changed.
            Log.e(TAG, "could not read draft " + id + ", discarding it", e);
            delete(context, id);
            return null;
        }
    }

    /** Newest first, which is the order they are worth offering back in. */
    public static List<Entry> list(Context context) {
        List<Entry> entries = new ArrayList<>();
        for (Map.Entry<String, ?> stored : prefs(context).getAll().entrySet()) {
            if (!stored.getKey().startsWith(KEY_PREFIX)) {
                continue;
            }
            try {
                long id = Long.parseLong(stored.getKey().substring(KEY_PREFIX.length()));
                Transaction transaction = decode(context, id, (String) stored.getValue());
                if (transaction != null) {
                    entries.add(new Entry(id, transaction));
                }
            } catch (Exception e) {
                Log.e(TAG, "ignoring unreadable draft key " + stored.getKey(), e);
            }
        }
        Collections.sort(entries, (a, b) -> Long.compare(b.id, a.id));
        return entries;
    }

    public static int count(Context context) {
        int n = 0;
        for (String key : prefs(context).getAll().keySet()) {
            if (key.startsWith(KEY_PREFIX)) {
                n++;
            }
        }
        return n;
    }

    public static void delete(Context context, long id) {
        prefs(context).edit().remove(key(id)).apply();
    }
}
