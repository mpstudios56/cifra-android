/*
 * This program is made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */
package tw.tib.financisto.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import tw.tib.financisto.R;

/**
 * Asks for a donation now and then, and remembers the answer.
 * <p>
 * The whole point of the entry in the menu is that nobody goes looking for it,
 * so it has to come up on its own once in a while. It also has to be easy to
 * refuse for good: an app that keeps asking after being told no is worse than
 * one that never asks.
 */
public class DonatePrompt {

    private static final String PREFS = "donate_prompt";
    private static final String KEY_DUE = "due_at";

    private static final long DAY = 24L * 60 * 60 * 1000;
    /** Long enough that the app has proved useful before it asks anything. */
    private static final long FIRST_WAIT = 21 * DAY;
    private static final long LATER_WAIT = 60 * DAY;
    /** Someone who has just given should not be asked again for years. */
    private static final long AFTER_GIVING = 730 * DAY;

    private static boolean askedThisLaunch = false;

    private DonatePrompt() {
    }

    /** Opens the payment page in a browser. Nothing is ever asked for in the app. */
    public static void open(Context context) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse(context.getString(R.string.donate_url)));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, R.string.donate_error, Toast.LENGTH_LONG).show();
        }
    }

    public static void maybeAsk(Activity activity) {
        if (askedThisLaunch || activity.isFinishing()) {
            return;
        }
        SharedPreferences prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        long due = prefs.getLong(KEY_DUE, 0);

        if (due == 0) {
            // First run: start the clock, say nothing.
            prefs.edit().putLong(KEY_DUE, now + FIRST_WAIT).apply();
            return;
        }
        if (now < due) {
            return;
        }
        askedThisLaunch = true;
        show(activity, prefs, now);
    }

    /**
     * Shows the card whether or not it is due. The menu entry uses this, so the
     * thing that comes up on its own can also be reached on purpose.
     */
    public static void ask(Activity activity) {
        show(activity, activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE),
                System.currentTimeMillis());
    }

    private static void show(Activity activity, SharedPreferences prefs, long now) {
        View card = LayoutInflater.from(activity).inflate(R.layout.donate_dialog, null);
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(card)
                // Dismissing without choosing is not a no, so it counts as "later".
                .setOnCancelListener(d -> postpone(prefs, now + LATER_WAIT))
                .create();

        card.findViewById(R.id.donate_go).setOnClickListener(v -> {
            postpone(prefs, now + AFTER_GIVING);
            open(activity);
            dialog.dismiss();
        });
        card.findViewById(R.id.donate_later).setOnClickListener(v -> {
            postpone(prefs, now + LATER_WAIT);
            dialog.dismiss();
        });
        card.findViewById(R.id.donate_never).setOnClickListener(v -> {
            postpone(prefs, Long.MAX_VALUE);
            dialog.dismiss();
        });
        dialog.show();
    }

    private static void postpone(SharedPreferences prefs, long when) {
        prefs.edit().putLong(KEY_DUE, when).apply();
    }
}
