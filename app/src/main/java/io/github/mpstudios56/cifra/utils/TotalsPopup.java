/*
 * This program is made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */
package io.github.mpstudios56.cifra.utils;

import android.database.Cursor;

import io.github.mpstudios56.cifra.db.DatabaseAdapter;

/**
 * Whether the per-currency totals screen is worth opening.
 * <p>
 * With one currency it listed the figure already on the button and nothing
 * else, so the total does not react at all. Breaking it into income, spending
 * and balance was tried and dropped: three more figures answering nothing
 * anybody had asked.
 */
public class TotalsPopup {

    private TotalsPopup() {
    }

    public static boolean severalCurrencies(DatabaseAdapter db) {
        String sql = "select count(distinct currency_id) from account where is_active = 1";
        try (Cursor c = db.db().rawQuery(sql, null)) {
            return c.moveToFirst() && c.getLong(0) > 1;
        } catch (Exception e) {
            // If it cannot be told, keep the old behaviour rather than hide a screen.
            return true;
        }
    }
}
