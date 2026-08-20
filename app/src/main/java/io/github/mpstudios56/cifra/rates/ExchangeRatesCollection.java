package io.github.mpstudios56.cifra.rates;

/**
 * Somewhere a downloaded rate can be put: the table of rates, or a list being
 * built up while several currencies are fetched at once.
 */
public interface ExchangeRatesCollection {

    void addRate(ExchangeRate rate);
}
