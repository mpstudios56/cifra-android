/*
 * This program is made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */
package tw.tib.financisto.export.csv;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import tw.tib.financisto.R;
import tw.tib.financisto.backup.DatabaseExport;
import tw.tib.financisto.db.DatabaseAdapter;
import tw.tib.financisto.export.ImportExportAsyncTask;
import tw.tib.financisto.export.ImportExportException;

/**
 * Backs everything up, then imports.
 * <p>
 * In that order and with no way round it. The import is the operation in the app
 * that can do the most damage, and the people who run it are running it on their
 * first day, before they have found out where the backup lives. If the backup
 * cannot be made, nothing is imported at all: an undo that does not exist is
 * worse than an import that did not happen.
 */
public class MappedCsvImportTask extends ImportExportAsyncTask {

    private static final String TAG = "MappedCsvImportTask";

    private final CsvColumnMapping mapping;
    private final Uri uri;
    private final String fallbackAccount;
    private boolean skipDuplicates = true;

    public MappedCsvImportTask(Activity activity, ProgressDialog dialog,
                               CsvColumnMapping mapping, Uri uri, String fallbackAccount) {
        super(activity, dialog);
        this.mapping = mapping;
        this.uri = uri;
        this.fallbackAccount = fallbackAccount;
    }

    /** Whether lines already in the account are left out. */
    public void setSkipDuplicates(boolean skipDuplicates) {
        this.skipDuplicates = skipDuplicates;
    }

    @Override
    protected Object work(Context context, DatabaseAdapter db, Uri... params) throws Exception {
        publishProgress(context.getString(R.string.csv_map_backing_up));
        try {
            new DatabaseExport(context, db.db(), true).export();
        } catch (Exception e) {
            Log.e(TAG, "no backup could be made, so nothing was imported", e);
            throw new ImportExportException(R.string.csv_map_backup_failed, e);
        }

        publishProgress(context.getString(R.string.csv_map_importing));
        MappedCsvImport importer = new MappedCsvImport(context, db, mapping, uri, fallbackAccount);
        importer.setSkipDuplicates(skipDuplicates);
        importer.setProgressListener(percentage ->
                publishProgress(context.getString(R.string.csv_map_importing_progress, percentage)));
        return importer.doImport();
    }
}
