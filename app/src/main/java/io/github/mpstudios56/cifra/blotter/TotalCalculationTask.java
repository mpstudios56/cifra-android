package io.github.mpstudios56.cifra.blotter;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import io.github.mpstudios56.cifra.R;
import io.github.mpstudios56.cifra.db.DatabaseAdapter;
import io.github.mpstudios56.cifra.model.Currency;
import io.github.mpstudios56.cifra.model.Total;
import io.github.mpstudios56.cifra.utils.CurrencyCache;
import io.github.mpstudios56.cifra.utils.MyPreferences;
import io.github.mpstudios56.cifra.utils.Utils;

/**
 * The figure at the head of a list of movements, worked out off the main thread.
 * <p>
 * Adding up a year of movements is not something to do while somebody is
 * scrolling, so the sum is made in the background and written into its field
 * when it is ready. What is being added up is left to whoever extends this: a
 * whole account, or whatever a filter has narrowed the list down to.
 * <p>
 * The screen it belongs to can be closed while the sum is still being made.
 * {@link #stop()} says so, and the answer is then thrown away rather than
 * written into a field nobody is looking at any more.
 */
public abstract class TotalCalculationTask extends AsyncTask<Object, Total, Total> {

    protected final DatabaseAdapter db;

    private final Context context;
    private final TextView field;
    private final Utils format;

    /** False once the screen has gone: the answer is no longer wanted. */
    private volatile boolean wanted = true;

    public TotalCalculationTask(Context context, DatabaseAdapter db, TextView totalText) {
        this.context = context;
        this.db = db;
        this.field = totalText;
        this.format = new Utils(context);
    }

    /** What is being added up. */
    public abstract Total[] getTotals();

    /**
     * The sum, brought into the currency the accounts are kept in.
     * <p>
     * Movements in several currencies cannot simply be added together: each
     * lot is converted at the latest rate known for it, and the results are
     * added. An account of its own can answer more directly, and says so by
     * overriding this.
     */
    public Total getTotalInHomeCurrency() {
        return format.calculateTotalInCurrency(getTotals(), db.getLatestRates(),
                CurrencyCache.getHomeCurrency());
    }

    @Override
    protected Total doInBackground(Object... params) {
        try {
            return getTotalInHomeCurrency();
        } catch (Exception failed) {
            // A sum that cannot be made is shown as nothing rather than
            // bringing down the screen it was going to sit on.
            Log.e("Cifra", "The total could not be worked out", failed);
            return Total.ZERO;
        }
    }

    @Override
    protected void onPostExecute(Total total) {
        if (!wanted || context == null) {
            return;
        }
        // No currency on the answer, and none set as the home one: there is
        // nothing to add up into, and no sum will ever appear until one is
        // chosen. Said out loud, because an empty figure explains nothing.
        if (total.currency == Currency.EMPTY && CurrencyCache.getHomeCurrency() == Currency.EMPTY) {
            Toast.makeText(context, R.string.currency_make_default_warning, Toast.LENGTH_LONG).show();
        }
        format.setTotal(field, total);
        if (MyPreferences.isBlurBalances()) {
            format.applyBlur(field);
        } else {
            field.getPaint().setMaskFilter(null);
        }
        field.invalidate();
    }

    /** Called when the screen goes: whatever comes back is dropped. */
    public void stop() {
        wanted = false;
    }
}
