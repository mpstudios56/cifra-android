/*
 * This program is made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */
package tw.tib.financisto.utils;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import java.text.DecimalFormatSymbols;
import java.util.Locale;

import tw.tib.financisto.db.DatabaseAdapter;
import tw.tib.financisto.model.Currency;
import tw.tib.financisto.model.SymbolFormat;

/**
 * The currency of the phone's own country, and how to write amounts in it.
 * <p>
 * A brand new app had no currency at all: before the first transaction could be
 * entered, one had to be invented by hand, complete with its decimal separator
 * and where the symbol goes. The phone already knows all of that.
 */
public class LocalCurrency {

    private static final String TAG = "LocalCurrency";

    private LocalCurrency() {
    }

    /** The three-letter code the phone's region uses, or EUR when it cannot say. */
    public static String guessCode() {
        try {
            java.util.Currency local = java.util.Currency.getInstance(Locale.getDefault());
            if (local != null && local.getCurrencyCode() != null) {
                return local.getCurrencyCode();
            }
        } catch (Exception e) {
            Log.w(TAG, "no currency for " + Locale.getDefault(), e);
        }
        return "EUR";
    }

    /** How that currency is named where the phone is set. */
    public static String describe(String code) {
        try {
            java.util.Currency c = java.util.Currency.getInstance(code);
            return code + " - " + c.getDisplayName(Locale.getDefault());
        } catch (Exception e) {
            return code;
        }
    }

    /**
     * Creates the currency if the app has none yet, taking the separators and the
     * number of decimals from the phone rather than guessing.
     *
     * @return true when one was created
     */
    public static boolean createIfMissing(Context context, DatabaseAdapter db, String code) {
        try (Cursor c = db.db().rawQuery("select 1 from currency limit 1", null)) {
            if (c.moveToFirst()) {
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "could not look for a currency", e);
            return false;
        }

        Currency currency = new Currency();
        currency.name = code;
        currency.isDefault = true;
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.getDefault());
        // Stored with the quotes around them, the way every other currency in the
        // database carries them.
        currency.decimalSeparator = "'" + symbols.getDecimalSeparator() + "'";
        currency.groupSeparator = "'" + symbols.getGroupingSeparator() + "'";
        try {
            java.util.Currency local = java.util.Currency.getInstance(code);
            currency.title = local.getDisplayName(Locale.getDefault());
            currency.symbol = local.getSymbol(Locale.getDefault());
            currency.decimals = Math.max(0, local.getDefaultFractionDigits());
        } catch (Exception e) {
            currency.title = code;
            currency.symbol = code;
            currency.decimals = 2;
        }
        // Where the symbol goes differs by country, and the phone knows: whether it
        // writes 1,00 € or € 1,00.
        currency.symbolFormat = symbolComesFirst() ? SymbolFormat.LS : SymbolFormat.RS;
        db.saveOrUpdate(currency);
        CurrencyCache.initialize(db);
        return true;
    }

    private static boolean symbolComesFirst() {
        try {
            String sample = java.text.NumberFormat.getCurrencyInstance(Locale.getDefault())
                    .format(1);
            return !Character.isDigit(sample.charAt(0));
        } catch (Exception e) {
            return false;
        }
    }
}
