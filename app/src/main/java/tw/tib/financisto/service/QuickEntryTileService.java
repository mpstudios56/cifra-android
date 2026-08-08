/*
 * This program is made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */
package tw.tib.financisto.service;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.TileService;

import androidx.annotation.RequiresApi;

import tw.tib.financisto.activity.QuickTransactionActivity;

/**
 * A tile in the pull-down panel that opens the quick entry screen.
 * <p>
 * The third way to reach it, after the button in the ledger and the widget, and
 * the shortest: pull the shade down, type the amount, done, without ever seeing
 * a home screen. Costs one class, and the screen it opens already exists.
 */
@RequiresApi(Build.VERSION_CODES.N)
public class QuickEntryTileService extends TileService {

    @Override
    public void onClick() {
        super.onClick();
        Intent intent = new Intent(this, QuickTransactionActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // From Android 14 the plain intent overload throws: the shade will only
            // collapse for something the app has already committed to launching.
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        } else {
            startActivityAndCollapse(intent);
        }
    }
}
