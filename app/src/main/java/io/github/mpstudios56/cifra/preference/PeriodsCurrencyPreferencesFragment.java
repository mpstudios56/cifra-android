package io.github.mpstudios56.cifra.preference;

import android.app.DatePickerDialog;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.preference.Preference;

import java.util.Calendar;

import io.github.mpstudios56.cifra.R;

import io.github.mpstudios56.cifra.rates.ExchangeRateProviderFactory;
import io.github.mpstudios56.cifra.utils.MyPreferences;

/**
 * The week, the fiscal year, and where the exchange rates come from.
 * <p>
 * Reached from the currencies screen rather than from the settings: all three
 * are answers about money and dates, and the screen that shows the currencies
 * is where somebody is standing when the question comes up.
 */
public class PeriodsCurrencyPreferencesFragment extends PreferenceFragmentBase {

    private Preference appId;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        title = R.string.periods_currencies_rates;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.pref_periods_currency, rootKey);

        showWhatIsSet();

        Preference start = findPreference("fiscal_year_start");
        if (start != null) {
            start.setOnPreferenceClickListener(p -> {
                askFiscalYearStart();
                return true;
            });
            showFiscalYearStart();
        }

        appId = findPreference("openexchangerates_app_id");
        Preference provider = findPreference("exchange_rate_provider");
        if (provider != null) {
            provider.setOnPreferenceChangeListener((preference, value) -> {
                if (appId != null) {
                    appId.setEnabled(ExchangeRateProviderFactory.openexchangerates
                            .name().equals(value));
                }
                return true;
            });
        }
        if (appId != null) {
            appId.setEnabled(MyPreferences.isOpenExchangeRatesProviderSelected());
        }
    }

    private void askFiscalYearStart() {
        Calendar cal = Calendar.getInstance();
        int start = MyPreferences.getFiscalYearStart();
        new DatePickerDialog(requireContext(), (view, year, month, day) -> {
            MyPreferences.setFiscalYearStart(month, day);
            showFiscalYearStart();
        }, cal.get(Calendar.YEAR), start / 100, start % 100).show();
    }

    private void showFiscalYearStart() {
        Preference start = findPreference("fiscal_year_start");
        if (start == null) {
            return;
        }
        int value = MyPreferences.getFiscalYearStart();
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.MONTH, value / 100);
        cal.set(Calendar.DATE, value % 100);
        start.setSummary(getString(R.string.fiscal_year_start_summary,
                android.text.format.DateUtils.formatDateTime(getContext(), cal.getTimeInMillis(),
                        android.text.format.DateUtils.FORMAT_SHOW_DATE
                                | android.text.format.DateUtils.FORMAT_ABBREV_MONTH
                                | android.text.format.DateUtils.FORMAT_NO_YEAR)));
    }

    /**
     * The two lists, and a line under each saying what is in it.
     * <p>
     * Which currencies are set, and how many rates have been written down: the
     * answer to both was one screen away, and both are one line long.
     */
    private void showWhatIsSet() {
        io.github.mpstudios56.cifra.db.DatabaseAdapter db =
                new io.github.mpstudios56.cifra.db.DatabaseAdapter(getContext());
        androidx.preference.Preference currencies = findPreference("open_currencies");
        if (currencies != null) {
            StringBuilder said = new StringBuilder();
            for (io.github.mpstudios56.cifra.model.Currency c : db.getAllCurrenciesList()) {
                if (said.length() > 0) {
                    said.append(", ");
                }
                said.append(c.name);
            }
            currencies.setSummary(said.length() > 0 ? said.toString()
                    : getString(R.string.no_currencies));
            currencies.setOnPreferenceClickListener(p -> {
                startActivity(new android.content.Intent(getContext(),
                        io.github.mpstudios56.cifra.activity.CurrencyListActivity.class));
                return true;
            });
        }
        androidx.preference.Preference rates = findPreference("open_rates");
        if (rates != null) {
            rates.setOnPreferenceClickListener(p -> {
                startActivity(new android.content.Intent(getContext(),
                        io.github.mpstudios56.cifra.activity.ExchangeRatesListActivity.class));
                return true;
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Coming back from the list of currencies, the line under it should say
        // what is there now, not what was there when the screen opened.
        showWhatIsSet();
    }
}
