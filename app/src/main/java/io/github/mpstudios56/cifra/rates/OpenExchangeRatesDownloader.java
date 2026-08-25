package io.github.mpstudios56.cifra.rates;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.github.mpstudios56.cifra.R;
import io.github.mpstudios56.cifra.http.HttpClientWrapper;
import io.github.mpstudios56.cifra.model.Currency;

/**
 * Exchange rates from openexchangerates.org, for whoever has an account there.
 * <p>
 * The service answers in one shape only: what one dollar is worth in every
 * currency it knows. A rate between two other currencies is not asked for, it
 * is worked out - through the dollar, dividing one by the first and multiplying
 * by the second. That is why every rate here passes through a currency nobody
 * asked about.
 * <p>
 * The service is not free of charge, so an account key is needed and every
 * request counts against it. One answer holds every currency, so the answer to
 * "today's rates" is fetched once and kept for as long as this object lives -
 * updating twenty currencies costs one request, not twenty.
 * <p>
 * Not to be shared between threads: the answer it holds is not guarded.
 */
public class OpenExchangeRatesDownloader implements ExchangeRateProvider {

    private static final String LATEST = "https://openexchangerates.org/api/latest.json?app_id=";
    private static final String ON_A_DAY =
            "https://openexchangerates.org/api/historical/%s.json?app_id=%s";

    private final String appId;
    private final HttpClientWrapper http;
    private final Context context;
    private final Handler onScreen;

    /** Today's answer, once it has been fetched: asked for once, used many times. */
    private JSONObject today;

    public OpenExchangeRatesDownloader(HttpClientWrapper httpClient, String appId, Context context) {
        this.http = httpClient;
        this.appId = appId;
        this.context = context;
        this.onScreen = new Handler(Looper.getMainLooper());
    }

    // ------------------------------------------------------------ one rate

    @Override
    public ExchangeRate getRate(Currency from, Currency to) {
        ExchangeRate rate = new ExchangeRate();
        rate.fromCurrencyId = from.id;
        rate.toCurrencyId = to.id;
        try {
            fetchToday();
            if (refused(today)) {
                rate.error = whyRefused(today);
                return rate;
            }
            JSONObject perDollar = today.getJSONObject("rates");
            rate.rate = throughTheDollar(perDollar.getDouble(from.name),
                    perDollar.getDouble(to.name));
            rate.date = asOf(today);
        } catch (Exception failed) {
            rate.error = whatWentWrong(failed);
        }
        return rate;
    }

    @Override
    public ExchangeRate getRate(Currency from, Currency to, long atTime) {
        String day = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(atTime));
        ExchangeRate rate = new ExchangeRate();
        rate.fromCurrencyId = from.id;
        rate.toCurrencyId = to.id;
        try {
            // A day of its own is asked for each time: unlike today's rates,
            // which serve every question, this answer serves only this date.
            JSONObject thatDay = http.getAsJson(String.format(ON_A_DAY, day, appId));
            if (refused(thatDay)) {
                rate.error = thatDay.optString("description", "");
                return rate;
            }
            JSONObject perDollar = thatDay.getJSONObject("rates");
            rate.rate = throughTheDollar(perDollar.getDouble(from.name),
                    perDollar.getDouble(to.name));
            rate.date = asOf(thatDay);
        } catch (Exception failed) {
            rate.error = failed.getMessage();
        }
        return rate;
    }

    // ------------------------------------------------------- every currency

    /**
     * The rate from the home currency to each of the others, in one request.
     * <p>
     * Returns nothing at all - not an empty list - when the answer cannot be
     * had, because a caller told "none" would write nothing down, while one
     * told "nothing came back" leaves the rates as they were. What went wrong
     * is put on screen here rather than handed back, since it is a thing to
     * read and not a thing to act on.
     */
    @Override
    public List<ExchangeRate> getRates(Currency homeCurrency, List<Currency> currencies) {
        try {
            fetchToday();
            if (refused(today)) {
                say(whyRefused(today));
                return null;
            }

            JSONObject perDollar = today.getJSONObject("rates");
            long when = asOf(today);

            double homeToDollar;
            try {
                homeToDollar = 1.0d / perDollar.getDouble(homeCurrency.name);
            } catch (Exception unknownHome) {
                // Every rate is worked out from the home currency: without it
                // there is nothing to convert from, and no point going on.
                say(context.getString(R.string.exchange_rate_default_currency_no_rate));
                return null;
            }

            List<ExchangeRate> rates = new ArrayList<>();
            for (Currency c : currencies) {
                if (c.isDefault || !c.updateExchangeRate) {
                    continue;
                }
                try {
                    ExchangeRate rate = new ExchangeRate();
                    rate.fromCurrencyId = homeCurrency.id;
                    rate.toCurrencyId = c.id;
                    rate.rate = homeToDollar * perDollar.getDouble(c.name);
                    rate.date = when;
                    rates.add(rate);
                } catch (Exception unknownCurrency) {
                    // One currency the service does not know is no reason to
                    // give up on the rest: it is left as it was.
                    Log.i("Cifra", "No rate offered for " + c.name);
                }
            }
            return rates;
        } catch (Exception failed) {
            say(whatWentWrong(failed));
            return null;
        }
    }

    // ------------------------------------------------------------- fetching

    private void fetchToday() throws Exception {
        if (today != null) {
            return;
        }
        if (TextUtils.getTrimmedLength(appId) == 0) {
            // The service answers nobody without a key, and a request without
            // one comes back as a refusal that says nothing useful.
            throw new IllegalStateException("No openexchangerates key has been set");
        }
        today = http.getAsJson(LATEST + appId);
    }

    /**
     * One rate from two, both against the dollar.
     * <p>
     * The service says what a dollar buys. Going the other way through it -
     * from what one wants, out to what one has - gives the rate between them.
     */
    private static double throughTheDollar(double dollarToFrom, double dollarToTo) {
        return dollarToTo * (1 / dollarToFrom);
    }

    /** The moment the service says its figures belong to, or now if it does not say. */
    private static long asOf(JSONObject answer) {
        return 1000 * answer.optLong("timestamp", System.currentTimeMillis() / 1000);
    }

    private static boolean refused(JSONObject answer) {
        return answer.optBoolean("error", false);
    }

    /** The service's own words: a wrong key and a spent allowance read differently. */
    private static String whyRefused(JSONObject answer) {
        return answer.optString("status") + " (" + answer.optString("message") + "): "
                + answer.optString("description");
    }

    private String whatWentWrong(Exception failed) {
        return context.getString(R.string.exchange_rate_provider_error, failed.getMessage());
    }

    /** Rates are fetched off the main thread; a message has to go back to it. */
    private void say(String message) {
        onScreen.post(() -> new AlertDialog.Builder(context).setMessage(message).show());
    }
}
