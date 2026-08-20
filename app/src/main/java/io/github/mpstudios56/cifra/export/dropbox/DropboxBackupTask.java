/*
 * Copyright (c) 2014 Denis Solonenko.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */

package io.github.mpstudios56.cifra.export.dropbox;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.net.Uri;

import io.github.mpstudios56.cifra.backup.DatabaseExport;
import io.github.mpstudios56.cifra.export.ImportExportAsyncTask;
import io.github.mpstudios56.cifra.db.DatabaseAdapter;

/**
 * Created by IntelliJ IDEA.
 * User: Denis Solonenko
 * Date: 11/9/11 2:23 AM
 */
public class DropboxBackupTask extends ImportExportAsyncTask {

    public DropboxBackupTask(Activity mainActivity, ProgressDialog dialog) {
        super(mainActivity, dialog);
    }

    @Override
    protected Object work(Context context, DatabaseAdapter db, Uri... params) throws Exception {
        DatabaseExport export = new DatabaseExport(context, db.db(), true);
        Uri backupFileUri = export.export();
        doForceUploadToDropbox(context, backupFileUri);
        return backupFileUri;
    }

}
