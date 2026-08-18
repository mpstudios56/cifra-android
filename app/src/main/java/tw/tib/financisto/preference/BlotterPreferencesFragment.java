package tw.tib.financisto.preference;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

import tw.tib.financisto.R;

public class BlotterPreferencesFragment extends PreferenceFragmentBase {

    /**
     * The colours the categories are painted from.
     * <p>
     * The same eighteen, and the same picker: a weekend written in one of them
     * sits beside a category wearing another, and two palettes chosen apart
     * would show it.
     */
    private static final String[] COLOURS = {
            "#e53935", "#d81b60", "#8e24aa", "#5e35b1", "#3949ab", "#1e88e5",
            "#039be5", "#00acc1", "#00897b", "#43a047", "#7cb342", "#c0ca33",
            "#fdd835", "#ffb300", "#fb8c00", "#f4511e", "#6d4c41", "#757575"
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        title = R.string.blotter_screen;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.pref_blotter, rootKey);
        Preference colour = findPreference("weekend_date_color");
        if (colour != null) {
            showChosen(colour);
            colour.setOnPreferenceClickListener(p -> {
                pickAColour(p);
                return true;
            });
        }
    }

    private void showChosen(Preference preference) {
        String chosen = PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getString("weekend_date_color", "CC6666");
        preference.setSummary("#" + chosen.replace("#", ""));
    }

    private void pickAColour(final Preference preference) {
        ArrayAdapter<String> patches =
                new ArrayAdapter<String>(requireContext(), R.layout.select_entry_color_row, COLOURS) {
                    @Override
                    public View getView(int position, View convertView, ViewGroup parent) {
                        View patch;
                        if (convertView == null) {
                            convertView = LayoutInflater.from(getContext())
                                    .inflate(R.layout.select_entry_color_row, parent, false);
                            patch = convertView.findViewById(R.id.color_patch);
                            convertView.setTag(patch);
                        } else {
                            patch = (View) convertView.getTag();
                        }
                        patch.setBackground(new ColorDrawable(Color.parseColor(COLOURS[position])));
                        return convertView;
                    }
                };
        new android.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.weekend_date_color)
                .setAdapter(patches, (dialog, which) -> {
                    PreferenceManager.getDefaultSharedPreferences(requireContext()).edit()
                            .putString("weekend_date_color", COLOURS[which].replace("#", ""))
                            .apply();
                    showChosen(preference);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
