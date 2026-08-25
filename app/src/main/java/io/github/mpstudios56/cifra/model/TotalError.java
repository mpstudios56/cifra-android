package io.github.mpstudios56.cifra.model;

/**
 * Why a total could not be trusted: a currency, and when the rate was wanted.
 * <p>
 * Adding money kept in different currencies needs a rate between them, and
 * there is not always one. Rather than showing a figure that is quietly wrong,
 * the total carries this instead, and the screen can say which currency it was
 * unable to convert and for what date.
 */
public class TotalError {

    /** No rate at all is known for this currency. */
    public static TotalError lastRateError(Currency currency) {
        return new TotalError(currency, System.currentTimeMillis());
    }

    /** Rates are known, but none as far back as the day being asked about. */
    public static TotalError atDateRateError(Currency currency, long datetime) {
        return new TotalError(currency, datetime);
    }

    public final Currency currency;
    public final long datetime;

    private TotalError(Currency currency, long datetime) {
        this.currency = currency;
        this.datetime = datetime;
    }
}
