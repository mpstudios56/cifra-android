package tw.tib.financisto.activity;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import tw.tib.financisto.R;

/**
 * The one widget: four ways of writing something down.
 * <p>
 * It replaces three that each showed the balance of a chosen account. A balance
 * on a home screen is a figure one cannot trust without opening the app anyway,
 * and all three needed a setting turned on somewhere before they would work at
 * all - so they sat there looking broken. This needs nothing: it is put down
 * and it works.
 */
public class EntryWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_entry);
            views.setOnClickPendingIntent(R.id.widget_transaction,
                    opens(context, TransactionActivity.class, 1));
            views.setOnClickPendingIntent(R.id.widget_transfer,
                    opens(context, TransferActivity.class, 2));
            views.setOnClickPendingIntent(R.id.widget_quick,
                    opens(context, QuickTransactionActivity.class, 3));
            views.setOnClickPendingIntent(R.id.widget_templates,
                    opens(context, MainActivity.class, 4));
            manager.updateAppWidget(id, views);
        }
    }

    private PendingIntent opens(Context context, Class<?> screen, int which) {
        Intent intent = new Intent(context, screen);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, which, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
