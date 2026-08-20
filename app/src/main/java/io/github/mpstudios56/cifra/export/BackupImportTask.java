/*
 * Copyright (c) 2011 Denis Solonenko.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */

package io.github.mpstudios56.cifra.export;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.net.Uri;

import io.github.mpstudios56.cifra.R;
import io.github.mpstudios56.cifra.backup.DatabaseImport;
import io.github.mpstudios56.cifra.db.DatabaseAdapter;

/**
 * Created by IntelliJ IDEA.
 * User: Denis Solonenko
 * Date: 11/9/11 2:21 AM
 */
public class BackupImportTask extends ImportExportAsyncTask {

    public BackupImportTask(final Activity activity, ProgressDialog dialog) {
        super(activity, dialog);
    }

    @Override
    protected Object work(Context context, DatabaseAdapter db, Uri... params) throws Exception {
        DatabaseImport.createFromFileBackup(context, db, params[0]).importDatabase();
        return true;
    }

    @Override
    protected String getSuccessMessage(Object result) {
        return context.getString(R.string.restore_database_success);
    }

}
