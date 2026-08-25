package io.github.mpstudios56.cifra.preference;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.preference.Preference;

import java.util.List;

import io.github.mpstudios56.cifra.R;
import io.github.mpstudios56.cifra.activity.LocationsListActivity;
import io.github.mpstudios56.cifra.activity.PayeeListActivity;
import io.github.mpstudios56.cifra.activity.ProjectListActivity;
import io.github.mpstudios56.cifra.db.DatabaseAdapter;
import io.github.mpstudios56.cifra.model.MyEntity;

/**
 * The three lists that go beside the category on a movement: the payee, the
 * project, the place.
 * <p>
 * They were three rows that led to three lists and told one nothing. Now each
 * row carries the first few names it holds, the way the currencies do: the
 * screen answers what is in them without being opened.
 */
public class EntitiesPreferencesFragment extends PreferenceFragmentBase {

    /** How many names fit on a line before the rest become an ellipsis. */
    private static final int NAMES_SHOWN = 4;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        title = R.string.menu_records;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.pref_entities, rootKey);
        showWhatIsIn();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Coming back from one of the lists, the line under it should say what
        // is there now, not what was there when this screen opened.
        showWhatIsIn();
    }

    private void showWhatIsIn() {
        DatabaseAdapter db = new DatabaseAdapter(getContext());
        wire("open_payees", PayeeListActivity.class, db.getAllPayeeList());
        wire("open_projects", ProjectListActivity.class, db.getAllProjectsList(false));
        wire("open_locations", LocationsListActivity.class, db.getAllLocationsList(false));
    }

    private void wire(String key, Class<?> screen, List<? extends MyEntity> held) {
        Preference row = findPreference(key);
        if (row == null) {
            return;
        }
        row.setSummary(namesOf(held));
        row.setOnPreferenceClickListener(p -> {
            startActivity(new Intent(getContext(), screen));
            return true;
        });
    }

    private String namesOf(List<? extends MyEntity> held) {
        if (held == null || held.isEmpty()) {
            return getString(R.string.no_entities_yet);
        }
        StringBuilder said = new StringBuilder();
        for (int i = 0; i < held.size() && i < NAMES_SHOWN; i++) {
            if (said.length() > 0) {
                said.append(", ");
            }
            said.append(held.get(i).title);
        }
        if (held.size() > NAMES_SHOWN) {
            said.append("\u2026");
        }
        return said.toString();
    }
}
