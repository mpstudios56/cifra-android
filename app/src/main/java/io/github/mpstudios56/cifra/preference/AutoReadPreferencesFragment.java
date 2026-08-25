package io.github.mpstudios56.cifra.preference;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.preference.Preference;

import io.github.mpstudios56.cifra.R;
import io.github.mpstudios56.cifra.activity.SmsDragListActivity;

/**
 * What Cifra reads by itself from the notifications of other apps.
 * <p>
 * The switches and the rules they work by, on one screen: they used to be a
 * section of the general settings and a line in a different list, and neither
 * said what the other was for.
 */
public class AutoReadPreferencesFragment extends PreferenceFragmentBase {

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        title = R.string.auto_read_notifications;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.pref_auto_read, rootKey);

        Preference rules = findPreference("open_sms_templates");
        if (rules != null) {
            rules.setOnPreferenceClickListener(p -> {
                startActivity(new Intent(getContext(), SmsDragListActivity.class));
                return true;
            });
        }
    }
}
