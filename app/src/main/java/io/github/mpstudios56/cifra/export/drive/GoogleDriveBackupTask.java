package io.github.mpstudios56.cifra.export.drive;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.net.Uri;

import io.github.mpstudios56.cifra.backup.DatabaseExport;
import io.github.mpstudios56.cifra.export.ImportExportAsyncTask;
import io.github.mpstudios56.cifra.db.DatabaseAdapter;

public class GoogleDriveBackupTask extends ImportExportAsyncTask {

    public GoogleDriveBackupTask(Activity mainActivity, ProgressDialog dialog) {
        super(mainActivity, dialog);
    }

    @Override
    protected Object work(Context context, DatabaseAdapter db, Uri... params) throws Exception {
        DatabaseExport export = new DatabaseExport(context, db.db(), true);
        Uri backupFileUri = export.export();
        doForceUploadToGoogleDrive(context, backupFileUri);
        return backupFileUri;
    }

}
