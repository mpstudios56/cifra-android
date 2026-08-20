package io.github.mpstudios56.cifra.export;

import android.app.ProgressDialog;
import android.content.Context;
import android.net.Uri;

import io.github.mpstudios56.cifra.backup.DatabaseExport;
import io.github.mpstudios56.cifra.db.DatabaseAdapter;

public class BackupExportTask extends ImportExportAsyncTask {

    public final boolean uploadOnline;

    public volatile Uri backupFileUri;
	
	public BackupExportTask(Context context, ProgressDialog dialog, boolean uploadOnline) {
		super(context, dialog);
        this.uploadOnline = uploadOnline;
	}
	
	@Override
	protected Object work(Context context, DatabaseAdapter db, Uri...params) throws Exception {
		DatabaseExport export = new DatabaseExport(context, db.db(), true);
        backupFileUri = export.export();
        if (backupFileUri != null) {
            // The backup was made. Any earlier failure still on the books is
            // history, and a red warning about a backup that has since been
            // made - naming a cloud service that was never asked for - is worse
            // than no warning at all.
            io.github.mpstudios56.cifra.utils.MyPreferences.notifyAutobackupSucceeded();
        }
        if (backupFileUri != null && uploadOnline) {
            doUploadToDropbox(context, backupFileUri);
			doUploadToGoogleDrive(context, backupFileUri);
        }
        return backupFileUri;
	}
}