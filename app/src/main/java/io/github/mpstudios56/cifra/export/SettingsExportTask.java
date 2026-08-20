package io.github.mpstudios56.cifra.export;

import android.app.ProgressDialog;
import android.content.Context;
import android.net.Uri;

import io.github.mpstudios56.cifra.db.DatabaseAdapter;

public class SettingsExportTask extends ImportExportAsyncTask {

    public SettingsExportTask(Context context, ProgressDialog dialog) {
        super(context, dialog);
    }

    @Override
    protected Object work(Context context, DatabaseAdapter db, Uri... params) throws Exception {
        return new SettingsExport(context).export();
    }

}
