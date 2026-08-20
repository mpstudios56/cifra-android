/*
 * This program is made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */
package io.github.mpstudios56.cifra.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.RemoteViews;

import androidx.core.app.NotificationCompat;

import io.github.mpstudios56.cifra.R;
import io.github.mpstudios56.cifra.activity.QuickTransactionActivity;
import io.github.mpstudios56.cifra.activity.TransactionActivity;
import io.github.mpstudios56.cifra.activity.TransferActivity;
import io.github.mpstudios56.cifra.utils.MyPreferences;

/**
 * A quiet, permanent notification with the three ways of adding something on it.
 * <p>
 * A quick settings tile holds one action; this holds three, and sits in the same
 * pull-down. Silent, at the lowest priority, and not dismissable by a swipe -
 * otherwise it would vanish the first time somebody cleared their notifications
 * and never come back.
 */
public class QuickBar {

    private static final String CHANNEL = "quick_bar";
    private static final int ID = 4711;

    private QuickBar() {
    }

    /** Puts the bar up or takes it down, to match the setting. */
    public static void refresh(Context context) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        if (!MyPreferences.isQuickBarEnabled(context)) {
            manager.cancel(ID);
            return;
        }
        createChannel(manager, context);

        RemoteViews bar = new RemoteViews(context.getPackageName(), R.layout.notification_quick_bar);
        bar.setOnClickPendingIntent(R.id.bar_quick,
                open(context, QuickTransactionActivity.class, 1));
        bar.setOnClickPendingIntent(R.id.bar_transaction,
                open(context, TransactionActivity.class, 2));
        bar.setOnClickPendingIntent(R.id.bar_transfer,
                open(context, TransferActivity.class, 3));

        NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.actionbar_quick_add)
                .setContentTitle(context.getString(R.string.quick_bar_title))
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setOngoing(true)
                .setShowWhen(false)
                .setSilent(true)
                .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(bar)
                .setCustomBigContentView(bar);
        try {
            manager.notify(ID, b.build());
        } catch (SecurityException e) {
            // Notifications not granted; the setting stays on and it appears once
            // the permission is given.
        }
    }

    private static PendingIntent open(Context context, Class<?> screen, int request) {
        Intent intent = new Intent(context, screen);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, request, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static void createChannel(NotificationManager manager, Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(CHANNEL,
                context.getString(R.string.quick_bar), NotificationManager.IMPORTANCE_MIN);
        channel.setShowBadge(false);
        channel.setSound(null, null);
        manager.createNotificationChannel(channel);
    }
}
