package io.github.mpstudios56.cifra.export;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.provider.DocumentsContract;

import androidx.preference.PreferenceManager;

import org.json.JSONObject;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import io.github.mpstudios56.cifra.utils.Utils;

public class SettingsExport {

    public static final int FORMAT_VERSION = 1;
    public static final String FILE_PREFIX = "cifra-settings-";
    public static final String FILE_EXTENSION = ".json";
    public static final String MIME_TYPE = "application/json";

    public static final String KEY_FORMAT = "format";
    public static final String KEY_SETTINGS = "settings";
    public static final String KEY_TYPE = "type";
    public static final String KEY_VALUE = "value";

    public static final String TYPE_BOOLEAN = "boolean";
    public static final String TYPE_STRING = "string";
    public static final String TYPE_INT = "int";
    public static final String TYPE_LONG = "long";
    public static final String TYPE_FLOAT = "float";

    /**
     * Secrets, device-bound grants and internal counters. The PIN switch is excluded together with
     * the PIN itself: importing it alone would turn on protection with no code to unlock.
     */
    public static final Set<String> EXCLUDED_KEYS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "pin",
            "pin_protection",
            "pin_protection_lock",
            "pin_protection_use_fingerprint",
            "pin_protection_use_fingerprint_fallback_to_pin",
            "dropbox_auth_token",
            "dropbox_authorize",
            "google_drive_backup_account",
            "database_backup_folder",
            "last_account_id",
            "last_autobackup_check",
            "auto_backup_failed_notify",
            "auto_backup_failed_error",
            "auto_backup_failed_timestamp",
            "should_rebuild_running_balance",
            "should_update_home_currency",
            "should_update_accounts_last_transaction_date",
            "should_update_split_parent_account_id"
    )));

    private final Context context;

    public SettingsExport(Context context) {
        this.context = context;
    }

    public String generateFilename() {
        SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd'_'HHmmss", Locale.US);
        return FILE_PREFIX + df.format(new Date()) + FILE_EXTENSION;
    }

    public Uri export() throws Exception {
        Uri backupFolderUri = Uri.parse(Export.getBackupFolder(context));
        String backupFolderId = DocumentsContract.getTreeDocumentId(backupFolderUri);
        Uri dirUri = DocumentsContract.buildDocumentUriUsingTree(backupFolderUri, backupFolderId);
        Uri fileUri = DocumentsContract.createDocument(context.getContentResolver(),
                dirUri, MIME_TYPE, generateFilename());
        String json = buildJson().toString(2);
        try (OutputStream outputStream = context.getContentResolver().openOutputStream(fileUri);
             Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
            writer.write(json);
        }
        return fileUri;
    }

    private JSONObject buildJson() throws Exception {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        JSONObject settings = new JSONObject();
        for (Map.Entry<String, ?> entry : new TreeMap<String, Object>(preferences.getAll()).entrySet()) {
            if (EXCLUDED_KEYS.contains(entry.getKey())) continue;
            JSONObject setting = toJson(entry.getValue());
            if (setting != null) {
                settings.put(entry.getKey(), setting);
            }
        }

        PackageInfo pi = Utils.getPackageInfo(context);
        JSONObject root = new JSONObject();
        root.put(KEY_FORMAT, FORMAT_VERSION);
        root.put("application", context.getPackageName());
        root.put("versionName", pi.versionName);
        root.put("versionCode", pi.versionCode);
        root.put("exported", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
        root.put(KEY_SETTINGS, settings);
        return root;
    }

    private static JSONObject toJson(Object value) throws Exception {
        JSONObject setting = new JSONObject();
        if (value instanceof Boolean) {
            setting.put(KEY_TYPE, TYPE_BOOLEAN);
            setting.put(KEY_VALUE, (boolean) (Boolean) value);
        } else if (value instanceof String) {
            setting.put(KEY_TYPE, TYPE_STRING);
            setting.put(KEY_VALUE, value);
        } else if (value instanceof Integer) {
            setting.put(KEY_TYPE, TYPE_INT);
            setting.put(KEY_VALUE, (int) (Integer) value);
        } else if (value instanceof Long) {
            setting.put(KEY_TYPE, TYPE_LONG);
            setting.put(KEY_VALUE, (long) (Long) value);
        } else if (value instanceof Float) {
            setting.put(KEY_TYPE, TYPE_FLOAT);
            setting.put(KEY_VALUE, ((Float) value).doubleValue());
        } else {
            return null;
        }
        return setting;
    }

}
