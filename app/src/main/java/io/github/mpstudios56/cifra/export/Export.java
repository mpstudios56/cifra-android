/*******************************************************************************
 * Copyright (c) 2010 Denis Solonenko.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * <p/>
 * Contributors:
 * Denis Solonenko - initial API and implementation
 ******************************************************************************/
package io.github.mpstudios56.cifra.export;

import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.zip.GZIPOutputStream;

import io.github.mpstudios56.cifra.R;
import io.github.mpstudios56.cifra.export.drive.GoogleDriveRESTClient;
import io.github.mpstudios56.cifra.export.dropbox.Dropbox;
import io.github.mpstudios56.cifra.utils.MyPreferences;

public abstract class Export {

    public static final String BACKUP_DIRECTORY_NAME = "backups";
    public static final String BACKUP_MIME_TYPE = "application/x.financisto+gzip";

    private final Context context;
    private final boolean useGzip;

    protected Export(Context context, boolean useGzip) {
        this.context = context;
        this.useGzip = useGzip;
    }

    public Uri export() throws Exception {
        String folder = getBackupFolder(context);
        // Until somebody picks a folder, the setting holds a plain path to the
        // app's own directory - and every call built on DocumentsContract
        // throws on it. That used to stop the safety backup, and with it the
        // import it was protecting: the app refused to import anything until a
        // backup folder had been chosen, which is not a thing anybody was told
        // to do first.
        if (folder == null || !folder.startsWith("content://")) {
            return exportToPlainFolder(folder);
        }
        Uri backupFolderUri = Uri.parse(folder);
        String backupFolderId = DocumentsContract.getTreeDocumentId(backupFolderUri);
        Uri dirUri = DocumentsContract.buildDocumentUriUsingTree(backupFolderUri, backupFolderId);
        Uri backupFileUri = DocumentsContract.createDocument(context.getContentResolver(),
                    dirUri, Export.BACKUP_MIME_TYPE, generateFilename());
        OutputStream outputStream = context.getContentResolver().openOutputStream(backupFileUri);
        try {
            if (useGzip) {
                export(new GZIPOutputStream(outputStream));
            } else {
                export(outputStream);
            }
        } finally {
            outputStream.flush();
            outputStream.close();
        }
        return backupFileUri;
    }

    /**
     * Writing into an ordinary directory rather than a picked document tree.
     * <p>
     * The default is the app's own external directory, which needs no
     * permission and is emptied when the app is uninstalled - fine for the copy
     * taken automatically before an import, and the reason to keep offering the
     * cloud folders for the copies meant to outlive the phone.
     */
    private Uri exportToPlainFolder(String path) throws Exception {
        java.io.File dir = (path == null || path.isEmpty())
                ? context.getExternalFilesDir(BACKUP_DIRECTORY_NAME)
                : new java.io.File(path);
        if (dir == null) {
            throw new IOException("no place to write the backup to");
        }
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("could not make the backup folder: " + dir);
        }
        java.io.File file = new java.io.File(dir, generateFilename());
        OutputStream outputStream = new java.io.FileOutputStream(file);
        try {
            if (useGzip) {
                export(new GZIPOutputStream(outputStream));
            } else {
                export(outputStream);
            }
        } finally {
            outputStream.flush();
            outputStream.close();
        }
        Log.i("Financisto", "backup written to " + file);
        return Uri.fromFile(file);
    }

    protected void export(OutputStream outputStream) throws Exception {
        generateBackup(outputStream);
    }

    public String generateFilename() {
        SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd'_'HHmmss'_'SSS");
        return df.format(new Date()) + getExtension();
    }

    public byte[] generateBackupBytes() throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        OutputStream out = new BufferedOutputStream(new GZIPOutputStream(outputStream));
        generateBackup(out);
        return outputStream.toByteArray();
    }

    private void generateBackup(OutputStream outputStream) throws Exception {
        OutputStreamWriter osw = new OutputStreamWriter(outputStream, "UTF-8");
        try (BufferedWriter bw = new BufferedWriter(osw, 65536)) {
            writeHeader(bw);
            writeBody(bw);
            writeFooter(bw);
        }
    }

    protected abstract void writeHeader(BufferedWriter bw) throws IOException, NameNotFoundException;

    protected abstract void writeBody(BufferedWriter bw) throws IOException;

    protected abstract void writeFooter(BufferedWriter bw) throws IOException;

    protected abstract String getExtension();

    public static String getBackupFolder(Context context) {
        String backupFolderUri = MyPreferences.getDatabaseBackupFolder();
        Log.i("Financisto", "getBackupFolder: " + backupFolderUri);
        return backupFolderUri;
    }

    /**
     * Whether a backup folder has been picked and is still writable. Until one is,
     * the preference holds a plain filesystem path rather than a document tree, so
     * everything built on DocumentsContract fails.
     */
    public static boolean isBackupFolderConfigured(Context context) {
        try {
            Uri backupFolderUri = Uri.parse(getBackupFolder(context));
            String backupFolderId = DocumentsContract.getTreeDocumentId(backupFolderUri);
            Uri dirUri = DocumentsContract.buildDocumentUriUsingTree(backupFolderUri, backupFolderId);
            DocumentFile dir = DocumentFile.fromTreeUri(context, dirUri);
            return dir != null && dir.canWrite();
        } catch (Exception e) {
            Log.i("Financisto", "backup folder not usable: " + e);
            return false;
        }
    }

    public static void uploadBackupFileToDropbox(Context context, Uri backupFileUri) throws Exception {
        Dropbox dropbox = new Dropbox(context);
        dropbox.uploadBackupFile(backupFileUri);
    }

    public static void uploadBackupFileToGoogleDrive(Context context, Uri backupFileUri) throws Exception {
        try {
            GoogleDriveRESTClient googleDriveRESTClient = new GoogleDriveRESTClient(context);
            googleDriveRESTClient.uploadBackup(backupFileUri);
        } catch (Exception e) {
            // The reason is carried through instead of being dropped: without
            // it every failure - no permission, no folder, no network, an
            // account the app was never allowed to use - read as the same
            // three words, and there was nothing to go on.
            android.util.Log.e("Cifra", "Google Drive upload failed", e);
            throw new ImportExportException(R.string.google_drive_error, e);
        }
    }

}
