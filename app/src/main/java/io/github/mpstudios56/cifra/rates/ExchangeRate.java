package io.github.mpstudios56.cifra.rates;

import android.content.ContentValues;
import android.database.Cursor;

import java.util.List;

import io.github.mpstudios56.cifra.db.DatabaseHelper.ExchangeRateColumns;

/**
 * What one currency was worth in another, on a given day.
 * <p>
 * A rate that could not be fetched is still a rate object, carrying the reason
 * instead of a figure: a total that could not be converted has to say so rather
 * than quietly read as zero.
 */
public class ExchangeRate implements Comparable<ExchangeRate> {

    /** A currency against itself. */
    public static final ExchangeRate ONE = new ExchangeRate();
    /** No rate to be had, and the total that needed it cannot be worked out. */
    public static final ExchangeRate NA = new ExchangeRate();

    static {
        ONE.rate = 1.0d;
        NA.error = "N/A";
    }

    public long fromCurrencyId;
    public long toCurrencyId;
    /** The day the rate belongs to. */
    public long date;
    public double rate;
    /** Whether it was worked out by turning another rate upside down. */
    public int is_flip;
    /** Why there is no figure, when there is none. */
    public String error;
    /** The rates this one was worked out from, when it was not fetched directly. */
    public List<ExchangeRate> derivedFrom;

    public static ExchangeRate fromCursor(Cursor c) {
        ExchangeRate r = new ExchangeRate();
        r.fromCurrencyId = c.getLong(ExchangeRateColumns.from_currency_id.ordinal());
        r.toCurrencyId = c.getLong(ExchangeRateColumns.to_currency_id.ordinal());
        r.date = c.getLong(ExchangeRateColumns.rate_date.ordinal());
        r.rate = c.getFloat(ExchangeRateColumns.rate.ordinal());
        // Older rows were written before the mark existed.
        if (c.getColumnCount() >= 5) {
            r.is_flip = c.getInt(ExchangeRateColumns.is_flip.ordinal());
        }
        return r;
    }

    public ContentValues toValues() {
        ContentValues values = new ContentValues();
        values.put(ExchangeRateColumns.from_currency_id.name(), fromCurrencyId);
        values.put(ExchangeRateColumns.to_currency_id.name(), toCurrencyId);
        values.put(ExchangeRateColumns.rate_date.name(), date);
        values.put(ExchangeRateColumns.rate.name(), rate);
        return values;
    }

    /**
     * The same rate read the other way round.
     * <p>
     * A service quotes euros in dollars; the app often needs dollars in euros,
     * which is the same fact upside down. The turned rate is marked as such, so
     * it is not mistaken for one that was quoted.
     */
    public ExchangeRate flip() {
        ExchangeRate turned = new ExchangeRate();
        turned.fromCurrencyId = toCurrencyId;
        turned.toCurrencyId = fromCurrencyId;
        turned.date = date;
        turned.rate = rate == 0 ? 0 : 1.0d / rate;
        turned.is_flip = is_flip == 0 ? 1 : 0;
        return turned;
    }

    public boolean isOk() {
        return error == null;
    }

    public String getErrorMessage() {
        return error != null ? error : "";
    }

    /** Newest first: a list of rates is read from the most recent backwards. */
    @Override
    public int compareTo(ExchangeRate that) {
        return Long.compare(that.date, this.date);
    }

    @Override
    public String toString() {
        return "ExchangeRate{" + fromCurrencyId + "->" + toCurrencyId
                + " on " + date + " = " + rate
                + (is_flip != 0 ? " (flipped)" : "")
                + (error != null ? " error=" + error : "") + "}";
    }
}
