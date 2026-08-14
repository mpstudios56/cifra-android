package tw.tib.financisto.sync;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import tw.tib.financisto.R;
import tw.tib.financisto.activity.SharingActivity;
import tw.tib.financisto.db.DatabaseAdapter;
import tw.tib.financisto.service.NotificationChannelService;

/**
 * The one thing a round of sharing cannot decide on its own.
 * <p>
 * Movements, accounts and labels arrive and are simply taken in. Two things are
 * not: a payment that looks like one already written down here, and two labels
 * that may or may not mean the same thing - "Casa" can be the mortgage for one
 * person and the cleaning for the other. Both wait on a screen nobody has any
 * reason to open, so they waited unseen. Now the phone says so.
 */
public class SyncNotice {

    private static final String TAG = "SyncNotice";
    private static final int ID = 0x5A1E;

    private SyncNotice() {
    }

    /** Counts what is waiting and, if anything is, puts it on the phone. */
    public static void tellIfAnythingNeedsLookingAt(Context context, DatabaseAdapter db) {
        int review;
        int merge;
        try {
            review = Duplicates.waiting(db.db());
            merge = Merger.candidates(db.db()).size();
        } catch (Exception e) {
            Log.e(TAG, "could not count what is waiting", e);
            return;
        }
        if (review == 0 && merge == 0) {
            return;
        }

        String text;
        if (review > 0 && merge > 0) {
            text = context.getString(R.string.sync_notice_both, review, merge);
        } else if (review > 0) {
            text = context.getString(R.string.sync_notice_review, review);
        } else {
            text = context.getString(R.string.sync_notice_merge, merge);
        }

        try {
            NotificationChannelService.initialize(context);
            Intent open = new Intent(context, SharingActivity.class);
            PendingIntent go = PendingIntent.getActivity(context, 0, open,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            Notification n = new NotificationCompat.Builder(context,
                    NotificationChannelService.SHARING_CHANNEL)
                    .setContentTitle(context.getString(R.string.sync_notice_title))
                    .setContentText(text)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                    .setSmallIcon(R.mipmap.a_icon_notify)
                    .setContentIntent(go)
                    .setAutoCancel(true)
                    .build();
            NotificationManager manager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            manager.notify(ID, n);
        } catch (SecurityException e) {
            // Notifications turned off for the app: nothing to be done, and
            // nothing worth interrupting a round over.
            Log.i(TAG, "not allowed to say so");
        } catch (Exception e) {
            Log.e(TAG, "could not say so", e);
        }
    }
}
