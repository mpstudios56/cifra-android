package tw.tib.financisto.sync;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import tw.tib.financisto.db.DatabaseAdapter;
import tw.tib.financisto.utils.MyPreferences;

/**
 * Rounds of exchange that nobody has to ask for.
 * <p>
 * The exchange used to run from one button on one screen, which meant a change
 * made here reached the other phone only if somebody remembered to go and press
 * it - and a change made there arrived only when the same thing happened in
 * reverse. Two people keeping one ledger will not do that, and should not have
 * to: the folder is theirs, the round is cheap, and the only thing that takes
 * time is the cloud catching up.
 * <p>
 * So: a round when the app comes to the front, a round every couple of minutes
 * while it stays there, and a round straight after something is written down.
 * It cannot be live - it is a folder in somebody's cloud - but it can be close
 * enough that nobody thinks about it.
 */
public class AutoSync {

    private static final String TAG = "AutoSync";

    /** How stale a round has to be before coming back to the app starts another. */
    private static final long WHEN_RETURNING_MS = 30 * 1000L;
    /** And how often while the app is sitting open. */
    private static final long WHILE_OPEN_MS = 2 * 60 * 1000L;

    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static Runnable ticking;
    private static volatile boolean running;

    private AutoSync() {
    }

    /** True when two people have actually set this up. */
    public static boolean configured() {
        return !MyPreferences.getSyncFolder().isEmpty();
    }

    /** A round now, unless one has just happened or one is under way. */
    public static void whenReturning(Context context) {
        long since = System.currentTimeMillis() - MyPreferences.getSyncLastRun();
        if (since >= WHEN_RETURNING_MS) {
            now(context);
        }
    }

    /** A round now whatever the clock says: something was just written down. */
    public static void afterAChange(Context context) {
        now(context);
    }

    /** Keeps them coming while the screen is in front. Idempotent. */
    public static void keepGoing(Context context) {
        if (!configured() || ticking != null) {
            return;
        }
        Context app = context.getApplicationContext();
        ticking = new Runnable() {
            @Override
            public void run() {
                now(app);
                HANDLER.postDelayed(this, WHILE_OPEN_MS);
            }
        };
        HANDLER.postDelayed(ticking, WHILE_OPEN_MS);
    }

    /** Stops them when the screen goes away, so a phone in a pocket is left alone. */
    public static void stop() {
        if (ticking != null) {
            HANDLER.removeCallbacks(ticking);
            ticking = null;
        }
    }

    private static void now(Context context) {
        if (!configured() || running) {
            return;
        }
        Context app = context.getApplicationContext();
        running = true;
        new Thread(() -> {
            DatabaseAdapter db = new DatabaseAdapter(app);
            try {
                db.open();
                SyncEngine.Result result = SyncEngine.run(app, db);
                Log.i(TAG, "round: " + result.received + " in, " + result.sent + " out");
            } catch (Exception e) {
                // Never in somebody's face: a round that fails because the cloud
                // is slow or the folder is briefly gone is not news, and the
                // Sharing screen says what happened for whoever goes to look.
                Log.e(TAG, "a round did not finish", e);
            } finally {
                try {
                    db.close();
                } catch (Exception ignored) {
                }
                running = false;
            }
        }).start();
    }
}
