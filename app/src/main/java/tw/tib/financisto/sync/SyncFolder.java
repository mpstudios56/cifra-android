/*
 * This program is made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */
package tw.tib.financisto.sync;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The folder the two phones meet in.
 * <p>
 * Each phone writes one file and never touches the other's. That is the whole
 * trick: two writers on one file, with a cloud copying both ways underneath,
 * ends in one of them being overwritten and nobody knowing which. One file each
 * means the cloud has nothing to reconcile - only to carry.
 * <p>
 * The files are plain text, one change per line, readable with any text editor.
 * If this ever goes wrong the person it went wrong for can look.
 */
public class SyncFolder {

    private static final String TAG = "SyncFolder";
    /** Deliberately not the backup's name or place: they are different things. */
    private static final String PREFIX = "cifra-sync-";
    private static final String SUFFIX = ".log";
    private static final String MIME = "text/plain";

    private final Context context;
    private final DocumentFile folder;

    private SyncFolder(Context context, DocumentFile folder) {
        this.context = context;
        this.folder = folder;
    }

    /** null when no folder was chosen, or the one chosen is no longer reachable. */
    public static SyncFolder open(Context context, String uri) {
        if (uri == null || uri.isEmpty()) {
            return null;
        }
        try {
            DocumentFile folder = DocumentFile.fromTreeUri(context, Uri.parse(uri));
            if (folder == null || !folder.exists() || !folder.canWrite()) {
                return null;
            }
            return new SyncFolder(context, folder);
        } catch (Exception e) {
            Log.e(TAG, "cannot open " + uri, e);
            return null;
        }
    }

    private String nameFor(String deviceId) {
        return PREFIX + deviceId + SUFFIX;
    }

    /**
     * Replaces this phone's file with the lines given.
     * <p>
     * Written whole rather than appended to. Appending through a document
     * provider is not reliably supported, and rewriting a file of a few hundred
     * lines costs nothing - while a half-appended line costs an afternoon.
     */
    public boolean write(String deviceId, List<String> lines) {
        try {
            String name = nameFor(deviceId);
            DocumentFile file = folder.findFile(name);
            if (file == null) {
                file = folder.createFile(MIME, name);
            }
            if (file == null) {
                return false;
            }
            try (OutputStream out = context.getContentResolver()
                    .openOutputStream(file.getUri(), "wt")) {
                if (out == null) {
                    return false;
                }
                Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
                for (String line : lines) {
                    writer.write(line);
                    writer.write("\n");
                }
                writer.flush();
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "could not write this phone's file", e);
            return false;
        }
    }

    /** Every line written by any phone but this one. */
    public List<String> readOthers(String deviceId) {
        List<String> lines = new ArrayList<>();
        String mine = nameFor(deviceId);
        try {
            for (DocumentFile file : folder.listFiles()) {
                String name = file.getName();
                if (name == null || !name.startsWith(PREFIX) || !name.endsWith(SUFFIX)
                        || name.equals(mine)) {
                    continue;
                }
                read(file, lines);
            }
        } catch (Exception e) {
            Log.e(TAG, "could not read the other phone's files", e);
        }
        return lines;
    }

    private void read(DocumentFile file, List<String> into) {
        try (InputStream in = context.getContentResolver().openInputStream(file.getUri())) {
            if (in == null) {
                return;
            }
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    into.add(line);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "could not read " + file.getName(), e);
        }
    }

    /** How many other phones have left a file here. */
    public int otherPhones(String deviceId) {
        int count = 0;
        String mine = nameFor(deviceId);
        try {
            for (DocumentFile file : folder.listFiles()) {
                String name = file.getName();
                if (name != null && name.startsWith(PREFIX) && name.endsWith(SUFFIX)
                        && !name.equals(mine)) {
                    count++;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "could not count the files", e);
        }
        return count;
    }
}
