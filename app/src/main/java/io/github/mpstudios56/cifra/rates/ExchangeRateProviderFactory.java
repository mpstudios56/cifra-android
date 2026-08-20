package io.github.mpstudios56.cifra.rates;

import android.content.Context;
import android.content.SharedPreferences;

import io.github.mpstudios56.cifra.http.HttpClientWrapper;
import okhttp3.OkHttpClient;

/**
 * The services that can be asked for exchange rates, and how each is set up.
 * <p>
 * One of them wants a key of its own, which is kept in the settings; the other
 * asks for nothing. Which is used is a choice made in the settings, and the
 * name of the choice is the name of the entry here.
 */
public enum ExchangeRateProviderFactory {

    openexchangerates {
        @Override
        public ExchangeRateProvider createProvider(SharedPreferences settings, Context context) {
            String key = settings.getString("openexchangerates_app_id", "");
            return new OpenExchangeRatesDownloader(network(), key, context);
        }
    },

    freeCurrency {
        @Override
        public ExchangeRateProvider createProvider(SharedPreferences settings, Context context) {
            return new FreeCurrencyRateDownloader(network(), System.currentTimeMillis(), context);
        }
    };

    public abstract ExchangeRateProvider createProvider(SharedPreferences settings, Context context);

    private static HttpClientWrapper network() {
        return new HttpClientWrapper(new OkHttpClient());
    }
}
