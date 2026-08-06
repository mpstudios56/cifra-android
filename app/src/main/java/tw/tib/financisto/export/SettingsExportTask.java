package tw.tib.financisto.export;

import android.app.ProgressDialog;
import android.content.Context;
import android.net.Uri;

import tw.tib.financisto.db.DatabaseAdapter;

public class SettingsExportTask extends ImportExportAsyncTask {

    public SettingsExportTask(Context context, ProgressDialog dialog) {
        super(context, dialog);
    }

    @Override
    protected Object work(Context context, DatabaseAdapter db, Uri... params) throws Exception {
        return new SettingsExport(context).export();
    }

}
