package tw.tib.financisto.export;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.preference.PreferenceManager;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import tw.tib.financisto.R;

public class SettingsImport {

    /**
     * Returns only the settings whose value differs from the current one, so the confirmation
     * dialog can tell the user how many settings are actually going to change.
     */
    public static Map<String, Object> readChanges(Context context, Uri uri) throws Exception {
        JSONObject settings = readSettings(context, uri);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        Map<String, ?> current = preferences.getAll();

        Map<String, Object> changes = new LinkedHashMap<>();
        for (Iterator<String> it = settings.keys(); it.hasNext(); ) {
            String key = it.next();
            if (SettingsExport.EXCLUDED_KEYS.contains(key)) continue;
            JSONObject setting = settings.optJSONObject(key);
            if (setting == null) continue;
            Object value = parseValue(setting);
            if (value != null && !value.equals(current.get(key))) {
                changes.put(key, value);
            }
        }
        return changes;
    }

    public static void apply(Context context, Map<String, Object> changes) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
        for (Map.Entry<String, Object> entry : changes.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Boolean) {
                editor.putBoolean(key, (Boolean) value);
            } else if (value instanceof String) {
                editor.putString(key, (String) value);
            } else if (value instanceof Integer) {
                editor.putInt(key, (Integer) value);
            } else if (value instanceof Long) {
                editor.putLong(key, (Long) value);
            } else if (value instanceof Float) {
                editor.putFloat(key, (Float) value);
            }
        }
        editor.apply();
    }

    private static JSONObject readSettings(Context context, Uri uri) throws Exception {
        String content;
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = inputStream.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            content = buffer.toString(StandardCharsets.UTF_8.name());
        }
        JSONObject settings = new JSONObject(content).optJSONObject(SettingsExport.KEY_SETTINGS);
        if (settings == null) {
            throw new ImportExportException(R.string.import_settings_invalid_file);
        }
        return settings;
    }

    private static Object parseValue(JSONObject setting) {
        String type = setting.optString(SettingsExport.KEY_TYPE);
        switch (type) {
            case SettingsExport.TYPE_BOOLEAN:
                return setting.optBoolean(SettingsExport.KEY_VALUE);
            case SettingsExport.TYPE_STRING:
                return setting.isNull(SettingsExport.KEY_VALUE) ? null : setting.optString(SettingsExport.KEY_VALUE);
            case SettingsExport.TYPE_INT:
                return setting.optInt(SettingsExport.KEY_VALUE);
            case SettingsExport.TYPE_LONG:
                return setting.optLong(SettingsExport.KEY_VALUE);
            case SettingsExport.TYPE_FLOAT:
                return (float) setting.optDouble(SettingsExport.KEY_VALUE);
            default:
                return null;
        }
    }

}
