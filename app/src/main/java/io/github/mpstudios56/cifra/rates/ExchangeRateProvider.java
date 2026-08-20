package io.github.mpstudios56.cifra.rates;

import java.util.List;

import io.github.mpstudios56.cifra.model.Currency;

/**
 * Where an exchange rate comes from.
 * <p>
 * Two of these exist: one asks a service on the network, the other looks in the
 * rates already written down. Both answer the same three questions, so whatever
 * needs a rate does not have to know which it is talking to.
 */
public interface ExchangeRateProvider {

    /** The rate between the two as it stands now. */
    ExchangeRate getRate(Currency from, Currency to);

    /** The rate between the two as it stood at that moment. */
    ExchangeRate getRate(Currency from, Currency to, long when);

    /** Every currency against the home one, in a single round. */
    List<ExchangeRate> getRates(Currency homeCurrency, List<Currency> currencies);
}
