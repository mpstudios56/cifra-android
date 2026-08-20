package io.github.mpstudios56.cifra.export.drive;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.net.Uri;

import io.github.mpstudios56.cifra.R;
import io.github.mpstudios56.cifra.backup.DatabaseImport;
import io.github.mpstudios56.cifra.db.DatabaseAdapter;
import io.github.mpstudios56.cifra.export.ImportExportAsyncTask;

public class GoogleDriveRestoreTask extends ImportExportAsyncTask {
    private final GoogleDriveFileInfo backupFile;

    public GoogleDriveRestoreTask(final Activity activity, ProgressDialog dialog, GoogleDriveFileInfo backupFile) {
        super(activity, dialog);
        this.backupFile = backupFile;
    }

    @Override
    protected Object work(Context context, DatabaseAdapter db, Uri... params) throws Exception {
        GoogleDriveRESTClient googleDriveRESTClient = new GoogleDriveRESTClient(context);
        DatabaseImport.createFromGoogleDriveBackup(context, db, googleDriveRESTClient, backupFile).importDatabase();
        return true;
    }

    @Override
    protected String getSuccessMessage(Object result) {
        return context.getString(R.string.restore_database_success);
    }

}
