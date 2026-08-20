package io.github.mpstudios56.cifra.rates;

import android.content.Context;
import android.util.Log;

import java.util.Arrays;
import java.util.List;

import io.github.mpstudios56.cifra.db.DatabaseAdapter;
import io.github.mpstudios56.cifra.model.Currency;
import io.github.mpstudios56.cifra.utils.CurrencyCache;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

/**
 * The most recent rate between any two currencies, worked out from what is
 * already known.
 * <p>
 * Rates are held for one currency against another. When the pair asked for is
 * not among them, three ways round are tried in turn: the same pair read upside
 * down, the two of them against the home currency, and the two of them against
 * the currency the first one is traded in. Only when all three fail is the
 * answer "no rate", and that answer is kept too - so a total made of a hundred
 * movements in an unconvertible currency does not go looking a hundred times.
 * <p>
 * Everything found or worked out is kept for the rest of the round: totals are
 * added up currency by currency, and the same pair comes up again and again.
 */
public class LatestExchangeRates implements ExchangeRateProvider, ExchangeRatesCollection {

    private static final String TAG = "LatestRates";

    protected Context context;
    protected Currency homeCurrency;
    protected DatabaseAdapter db;

    /** From one currency, to another, the rate: kept for this round only. */
    private final Long2ObjectOpenHashMap<Long2ObjectOpenHashMap<ExchangeRate>> rates =
            new Long2ObjectOpenHashMap<>();

    public LatestExchangeRates(Context context) {
        this.context = context;
        this.db = new DatabaseAdapter(context);
    }

    @Override
    public ExchangeRate getRate(Currency from, Currency to) {
        if (from.id == to.id) {
            return ExchangeRate.ONE;
        }
        Long2ObjectOpenHashMap<ExchangeRate> known = ratesFrom(from.id);

        ExchangeRate direct = known.get(to.id);
        if (direct != null) {
            return direct;
        }

        // The same pair the other way round is the same fact upside down.
        ExchangeRate other = ratesFrom(to.id).get(from.id);
        if (other != null) {
            ExchangeRate turned = other.flip();
            known.put(to.id, turned);
            return turned;
        }

        // Both against the home currency: what the app converts through most.
        if (homeCurrency == null) {
            homeCurrency = CurrencyCache.getHomeCurrency();
        }
        if (!homeCurrency.equals(Currency.EMPTY)
                && !from.equals(homeCurrency)
                && !to.equals(homeCurrency)) {
            ExchangeRate through = twoStep(known, from, to, homeCurrency);
            if (through != null) {
                return through;
            }
        }

        // Both against the currency the first one is traded in - the way a
        // fund or a share reaches the money it is finally counted in.
        if (from.tradingCurrencyId != 0) {
            ExchangeRate through = twoStep(known, from, to,
                    CurrencyCache.getCurrency(from.tradingCurrencyId));
            if (through != null) {
                return through;
            }
        }

        // Nothing worked. The failure is kept, so it is not tried again for
        // every movement in the list.
        known.put(to.id, ExchangeRate.NA);
        return ExchangeRate.NA;
    }

    /** Two rates through a middle currency, multiplied into one. */
    private ExchangeRate twoStep(Long2ObjectOpenHashMap<ExchangeRate> known,
                                 Currency from, Currency to, Currency through) {
        ExchangeRate first = getRate(from, through);
        if (first == ExchangeRate.NA) {
            return null;
        }
        ExchangeRate second = getRate(through, to);
        if (second == ExchangeRate.NA) {
            return null;
        }
        ExchangeRate combined = new ExchangeRate();
        combined.fromCurrencyId = from.id;
        combined.toCurrencyId = to.id;
        combined.rate = first.rate * second.rate;
        // Kept so the screen can say where a figure came from.
        combined.derivedFrom = Arrays.asList(first, second);
        known.put(to.id, combined);
        Log.d(TAG, "worked out " + combined + " through " + through);
        return combined;
    }

    /**
     * The most recent rate is the only one this holds, so asking for a moment
     * makes no difference to the answer.
     */
    @Override
    public ExchangeRate getRate(Currency from, Currency to, long when) {
        return getRate(from, to);
    }

    @Override
    public List<ExchangeRate> getRates(Currency homeCurrency, List<Currency> currencies) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addRate(ExchangeRate rate) {
        ratesFrom(rate.fromCurrencyId).put(rate.toCurrencyId, rate);
    }

    private Long2ObjectOpenHashMap<ExchangeRate> ratesFrom(long currencyId) {
        Long2ObjectOpenHashMap<ExchangeRate> known = rates.get(currencyId);
        if (known == null) {
            known = new Long2ObjectOpenHashMap<>();
            rates.put(currencyId, known);
        }
        return known;
    }
}
