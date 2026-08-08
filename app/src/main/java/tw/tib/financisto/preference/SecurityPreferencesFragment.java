package tw.tib.financisto.preference;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;

import tw.tib.financisto.R;

/**
 * The lock: screenshots, PIN, fingerprint.
 * <p>
 * Reached from the menu beside backup and restore rather than from the settings.
 * Keeping the data safe and keeping a copy of it are the same worry, and the PIN
 * was six taps deep in a screen nobody opens twice.
 */
public class SecurityPreferencesFragment extends PreferenceFragmentBase {

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        title = R.string.protection;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.pref_security, rootKey);
    }

    @Override
    public void onDisplayPreferenceDialog(@NonNull Preference preference) {
        // The PIN keypad is a dialog of our own, and without this the framework
        // opens an empty one in its place.
        if (preference instanceof PinPreference) {
            PinDialogFragment f = PinDialogFragment.newInstance(preference.getKey());
            f.setTargetFragment(this, 0);
            f.show(getParentFragmentManager(), null);
            return;
        }
        super.onDisplayPreferenceDialog(preference);
    }
}
