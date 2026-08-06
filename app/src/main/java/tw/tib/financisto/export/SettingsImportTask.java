package tw.tib.financisto.export;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import java.util.Map;

import tw.tib.financisto.R;
import tw.tib.financisto.bus.GreenRobotBus_;
import tw.tib.financisto.bus.RefreshCurrentTab;
import tw.tib.financisto.service.DailyAutoBackupScheduler;

public class SettingsImportTask extends AsyncTask<Uri, Void, Object> {

    private static final String TAG = "SettingsImportTask";

    private final Context context;
    private final ProgressDialog dialog;

    public SettingsImportTask(Context context, ProgressDialog dialog) {
        this.context = context;
        this.dialog = dialog;
    }

    @Override
    protected Object doInBackground(Uri... params) {
        try {
            return SettingsImport.readChanges(context, params[0]);
        } catch (Exception e) {
            Log.e(TAG, "Unable to read settings file", e);
            return e;
        }
    }

    @Override
    protected void onPostExecute(Object result) {
        dialog.dismiss();

        if (result instanceof Exception) {
            new AlertDialog.Builder(context)
                    .setTitle(R.string.fail)
                    .setMessage(R.string.import_settings_invalid_file)
                    .setPositiveButton(R.string.ok, null)
                    .show();
            return;
        }

        @SuppressWarnings("unchecked")
        final Map<String, Object> changes = (Map<String, Object>) result;
        if (changes.isEmpty()) {
            Toast.makeText(context, R.string.import_settings_nothing_to_apply, Toast.LENGTH_LONG).show();
            return;
        }

        new AlertDialog.Builder(context)
                .setTitle(R.string.import_settings)
                .setMessage(context.getString(R.string.import_settings_confirm, changes.size()))
                .setPositiveButton(R.string.ok, (d, which) -> applyChanges(changes))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void applyChanges(Map<String, Object> changes) {
        SettingsImport.apply(context, changes);
        DailyAutoBackupScheduler.scheduleNextAutoBackup(context);
        GreenRobotBus_.getInstance_(context).post(new RefreshCurrentTab());
        Toast.makeText(context, context.getString(R.string.import_settings_success, changes.size()),
                Toast.LENGTH_LONG).show();
    }

}
