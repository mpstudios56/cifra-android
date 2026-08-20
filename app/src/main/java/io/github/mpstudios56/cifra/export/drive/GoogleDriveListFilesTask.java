package io.github.mpstudios56.cifra.export.drive;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.net.Uri;

import java.util.List;

import io.github.mpstudios56.cifra.R;
import io.github.mpstudios56.cifra.bus.GreenRobotBus_;
import io.github.mpstudios56.cifra.db.DatabaseAdapter;
import io.github.mpstudios56.cifra.export.ImportExportAsyncTask;
import io.github.mpstudios56.cifra.export.ImportExportAsyncTaskListener;
import io.github.mpstudios56.cifra.export.ImportExportException;

public class GoogleDriveListFilesTask extends ImportExportAsyncTask {

    public GoogleDriveListFilesTask(final Activity context, ProgressDialog dialog) {
        super(context, dialog);
        setShowResultMessage(false);
        setListener(new ImportExportAsyncTaskListener() {
            @Override
            public void onCompleted(Object result) {
                GreenRobotBus_.getInstance_(context).post(new GoogleDriveFileList((GoogleDriveFileInfo[]) result));
            }
        });
    }

    @Override
    protected Object work(Context context, DatabaseAdapter db, Uri... params) throws Exception {
        try {
            GoogleDriveRESTClient client = new GoogleDriveRESTClient(context);
            List<GoogleDriveFileInfo> files = client.listFiles();
            return files.toArray(new GoogleDriveFileInfo[files.size()]);
        } catch (Exception e) {
            throw new ImportExportException(R.string.google_drive_list_files_failed);
        }
    }

}
