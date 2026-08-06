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

import tw.tib.financisto.model.Transaction;

/**
 * Keeps the half-filled transaction a user walked away from, so closing the screen
 * or having the app killed in the background does not throw the entry away.
 * <p>
 * Held in its own preferences file rather than the database: a draft is scratch
 * state, it must never appear in reports or backups, and it has to survive the
 * process being killed without a transaction being written.
 * <p>
 * One draft per screen: a second unfinished entry of the same kind replaces the
 * first, which keeps the offer to resume unambiguous.
 */
public class TransactionDraft {

    private static final String TAG = "TransactionDraft";
    private static final String PREFS_NAME = "transaction_drafts";

    private TransactionDraft() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static void save(Context context, String key, Transaction transaction) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
                out.writeObject(transaction);
            }
            prefs(context).edit()
                    .putString(key, Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP))
                    .apply();
            Log.i(TAG, "saved draft " + key);
        } catch (Exception e) {
            // A draft is a convenience: failing to store one must never break the screen.
            Log.e(TAG, "could not save draft " + key, e);
        }
    }

    public static Transaction load(Context context, String key) {
        String encoded = prefs(context).getString(key, null);
        if (encoded == null) {
            return null;
        }
        try {
            byte[] bytes = Base64.decode(encoded, Base64.NO_WRAP);
            try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
                return (Transaction) in.readObject();
            }
        } catch (Exception e) {
            // Most likely a draft written by an older version whose model has changed.
            Log.e(TAG, "could not read draft " + key + ", discarding it", e);
            clear(context, key);
            return null;
        }
    }

    public static void clear(Context context, String key) {
        prefs(context).edit().remove(key).apply();
    }

    public static boolean exists(Context context, String key) {
        return prefs(context).contains(key);
    }
}
