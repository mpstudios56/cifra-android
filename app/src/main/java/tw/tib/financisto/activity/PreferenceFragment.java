package tw.tib.financisto.activity;

import android.app.DatePickerDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import java.util.Calendar;

import tw.tib.financisto.R;
import tw.tib.financisto.preference.PinDialogFragment;
import tw.tib.financisto.preference.PinPreference;
import tw.tib.financisto.preference.TimeDialogFragment;
import tw.tib.financisto.preference.TimePreference;
import tw.tib.financisto.rates.ExchangeRateProviderFactory;
import tw.tib.financisto.utils.Identity;
import tw.tib.financisto.utils.MyPreferences;
import tw.tib.financisto.utils.PinProtection;

public class PreferenceFragment extends PreferenceFragmentCompat {
    Preference pOpenExchangeRatesAppId;

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        PreferenceScreen preferenceScreen = getPreferenceScreen();
        Preference pLocale = preferenceScreen.findPreference("ui_language");
        Context context = getContext();

        ViewCompat.setOnApplyWindowInsetsListener(getListView(), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, 0, 0, insets.bottom);
            ((ViewGroup) v).setClipToPadding(false);
            return WindowInsetsCompat.CONSUMED;
        });

        pLocale.setOnPreferenceChangeListener((preference, newValue) -> {
            String locale = (String) newValue;
            MyPreferences.switchLocale(context, locale);
            return true;
        });
        Preference pFiscalYearStart = preferenceScreen.findPreference("fiscal_year_start");
        pFiscalYearStart.setOnPreferenceClickListener(arg0 -> {
            selectFiscalYearStart();
            return true;
        });
        Preference pNewTransactionShortcut = preferenceScreen.findPreference("shortcut_new_transaction");
        pNewTransactionShortcut.setOnPreferenceClickListener(arg0 -> {
            addShortcut(".activity.TransactionActivity", R.string.transaction, R.drawable.icon_transaction);
            return true;
        });
        Preference pNewTransferShortcut = preferenceScreen.findPreference("shortcut_new_transfer");
        pNewTransferShortcut.setOnPreferenceClickListener(arg0 -> {
            addShortcut(".activity.TransferActivity", R.string.transfer, R.drawable.icon_transfer);
            return true;
        });
        Preference pExchangeProvider = preferenceScreen.findPreference("exchange_rate_provider");
        pOpenExchangeRatesAppId = preferenceScreen.findPreference("openexchangerates_app_id");
        pExchangeProvider.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                pOpenExchangeRatesAppId.setEnabled(isOpenExchangeRatesProvider((String) newValue));
                return true;
            }

            private boolean isOpenExchangeRatesProvider(String provider) {
                return ExchangeRateProviderFactory.openexchangerates.name().equals(provider);
            }
        });

        setFiscalYearStart();
        enableOpenExchangeApp();
    }

    private void enableOpenExchangeApp() {
        pOpenExchangeRatesAppId.setEnabled(MyPreferences.isOpenExchangeRatesProviderSelected());
    }

    private void selectFiscalYearStart() {
        Calendar cal = Calendar.getInstance();
        int fiscalYearStart = MyPreferences.getFiscalYearStart();
        DatePickerDialog dialog = new DatePickerDialog(getContext(),
                (view, year, monthOfYear, dayOfMonth) -> {
                    MyPreferences.setFiscalYearStart(monthOfYear, dayOfMonth);
                    setFiscalYearStart();
                },
                cal.get(Calendar.YEAR),
                fiscalYearStart / 100,
                fiscalYearStart % 100
        );
        dialog.show();
    }

    private void setFiscalYearStart() {
        Preference pFiscalYearStart = getPreferenceScreen().findPreference("fiscal_year_start");
        int fiscalYearStart = MyPreferences.getFiscalYearStart();
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.MONTH, fiscalYearStart / 100);
        cal.set(Calendar.DATE, fiscalYearStart % 100);
        String summary = getString(R.string.fiscal_year_start_summary, DateUtils.formatDateTime(getContext(), cal.getTimeInMillis(),
                DateUtils.FORMAT_SHOW_DATE|DateUtils.FORMAT_ABBREV_MONTH|DateUtils.FORMAT_NO_YEAR));
        pFiscalYearStart.setSummary(summary);
    }

    private void addShortcut(String activity, int nameId, int iconId) {
        Intent intent = createShortcutIntent(activity, getString(nameId), Intent.ShortcutIconResource.fromContext(getContext(), iconId),
                "com.android.launcher.action.INSTALL_SHORTCUT");
        getContext().sendBroadcast(intent);
    }

    private Intent createShortcutIntent(String activity, String shortcutName, Intent.ShortcutIconResource shortcutIcon, String action) {
        Intent shortcutIntent = new Intent();
        shortcutIntent.setComponent(new ComponentName(getContext().getPackageName(), activity));
        shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        Intent intent = new Intent();
        intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
        intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, shortcutName);
        intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, shortcutIcon);
        intent.setAction(action);
        return intent;
    }

    @Override
    public void onPause() {
        super.onPause();
        PinProtection.lock(getContext());
    }

    @Override
    public void onResume() {
        super.onResume();
        PinProtection.unlock(getContext());
        var actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        actionBar.setTitle(R.string.preferences);
        actionBar.setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public void onDisplayPreferenceDialog(@NonNull Preference preference) {
        if (preference instanceof TimePreference) {
            TimeDialogFragment f = TimeDialogFragment.newInstance(preference.getKey());
            f.setTargetFragment(this, 0);
            f.show(getParentFragmentManager(), null);
            return;
        }
        else if (preference instanceof PinPreference) {
            PinDialogFragment f = PinDialogFragment.newInstance(preference.getKey());
            f.setTargetFragment(this, 0);
            f.show(getParentFragmentManager(), null);
            return;
        }
        super.onDisplayPreferenceDialog(preference);
    }
}
