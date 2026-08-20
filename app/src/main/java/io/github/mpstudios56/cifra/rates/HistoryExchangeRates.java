package io.github.mpstudios56.cifra.rates;

import android.content.Context;

import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import io.github.mpstudios56.cifra.model.Currency;
import io.github.mpstudios56.cifra.utils.CurrencyCache;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

/**
 * The rate between two currencies as it stood on a given day.
 * <p>
 * Unlike its neighbour, which keeps only the most recent rate, this holds every
 * rate that was ever written down for a pair, newest first. A movement of three
 * years ago is converted at the rate of three years ago, not at today's - which
 * is the difference between a history and a snapshot.
 * <p>
 * "The rate on that day" means the most recent rate that is not older than the
 * day asked for. When there is none, the same three ways round are tried as
 * elsewhere: the pair upside down, and the two of them through the home
 * currency. A failure is kept as well, so it is not tried again for every
 * movement of the same currency.
 */
public class HistoryExchangeRates implements ExchangeRateProvider, ExchangeRatesCollection {

    protected Context context;
    protected Currency homeCurrency;

    /** From one currency, to another, every rate ever known, newest first. */
    private final Long2ObjectOpenHashMap<Long2ObjectOpenHashMap<SortedSet<ExchangeRate>>> rates =
            new Long2ObjectOpenHashMap<>();

    public HistoryExchangeRates(Context context) {
        this.context = context;
    }

    @Override
    public void addRate(ExchangeRate rate) {
        history(rate.fromCurrencyId, rate.toCurrencyId).add(rate);
    }

    /** The most recent rate of all, whenever it is from. */
    @Override
    public ExchangeRate getRate(Currency from, Currency to) {
        SortedSet<ExchangeRate> known = history(from.id, to.id);
        if (!known.isEmpty()) {
            return known.first();
        }
        SortedSet<ExchangeRate> other = history(to.id, from.id);
        if (!other.isEmpty()) {
            return other.first().flip();
        }
        return ExchangeRate.NA;
    }

    @Override
    public ExchangeRate getRate(Currency from, Currency to, long when) {
        // Rates sort newest first, so everything at or before the moment asked
        // for is the tail of the set, and its first entry is the one wanted.
        ExchangeRate asking = new ExchangeRate();
        asking.date = when;

        SortedSet<ExchangeRate> known = history(from.id, to.id);
        SortedSet<ExchangeRate> byThen = known.tailSet(asking);
        if (!byThen.isEmpty()) {
            return byThen.first();
        }

        SortedSet<ExchangeRate> otherWay = history(to.id, from.id).tailSet(asking);
        if (!otherWay.isEmpty()) {
            ExchangeRate turned = otherWay.first().flip();
            known.add(turned);
            return turned;
        }

        if (homeCurrency == null) {
            homeCurrency = CurrencyCache.getHomeCurrency();
        }
        if (!homeCurrency.equals(Currency.EMPTY)
                && !from.equals(homeCurrency)
                && !to.equals(homeCurrency)) {
            ExchangeRate first = getRate(from, homeCurrency, when);
            if (first != ExchangeRate.NA) {
                ExchangeRate second = getRate(homeCurrency, to, when);
                if (second != ExchangeRate.NA) {
                    asking.fromCurrencyId = from.id;
                    asking.toCurrencyId = to.id;
                    asking.rate = first.rate * second.rate;
                    known.add(asking);
                    return asking;
                }
            }
        }

        known.add(ExchangeRate.NA);
        return ExchangeRate.NA;
    }

    @Override
    public List<ExchangeRate> getRates(Currency homeCurrency, List<Currency> currencies) {
        throw new UnsupportedOperationException();
    }

    private SortedSet<ExchangeRate> history(long fromCurrencyId, long toCurrencyId) {
        Long2ObjectOpenHashMap<SortedSet<ExchangeRate>> against = rates.get(fromCurrencyId);
        if (against == null) {
            against = new Long2ObjectOpenHashMap<>();
            rates.put(fromCurrencyId, against);
        }
        SortedSet<ExchangeRate> known = against.get(toCurrencyId);
        if (known == null) {
            known = new TreeSet<>();
            against.put(toCurrencyId, known);
        }
        return known;
    }
}
