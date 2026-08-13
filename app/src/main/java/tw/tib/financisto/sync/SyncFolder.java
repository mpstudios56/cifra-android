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
    private static final String PREFIX = "cifra-";
    /**
     * Plain text, and named .txt rather than .log, because the first thing
     * anybody does when the exchange looks stuck is open the cloud folder on a
     * computer to see whether the file is even there. A .log opens in nothing.
     */
    private static final String SUFFIX = ".txt";
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

    /**
     * The name each phone writes under: the name its owner gave themselves,
     * so the folder reads "cifra-Marcello.txt" and "cifra-Debora.txt".
     * <p>
     * It used to be the device identifier - thirty-six characters of hex - which
     * is unmistakable to a program and useless to the person looking at the
     * folder wondering whether anything is arriving at all. The identifier is
     * still inside every line, which is what the reading actually goes by.
     */
    public static String nameFor(String author, String deviceId) {
        return nameFor(author, tw.tib.financisto.utils.MyPreferences.getSyncGroupCode(), deviceId);
    }

    /** Reads every file carrying this pair's code that is not this phone's own. */
    public List<String> readPair(String author, String code, String deviceId) {
        List<String> lines = new ArrayList<>();
        String mine = nameFor(author, code, deviceId);
        String tag = "-" + tidy(code) + "-";
        try {
            for (DocumentFile file : folder.listFiles()) {
                String name = file.getName();
                if (name == null || !name.startsWith(PREFIX) || name.equals(mine)
                        || !name.contains(tag)) {
                    continue;
                }
                Log.i(TAG, "reading " + name);
                read(file, lines);
            }
        } catch (Exception e) {
            Log.e(TAG, "could not read the files of " + code, e);
        }
        return lines;
    }

    /**
     * cifra - name - group code - phone.
     * <p>
     * The name is for the person looking in the folder. The group code says who
     * belongs with whom, so one folder can hold more than one group and a group
     * can be more than two people. The last part keeps two people called the
     * same thing apart: without it they would share one file and each would
     * wipe the other's changes.
     */
    public static String nameFor(String author, String groupCode, String deviceId) {
        String mark = deviceId == null ? "" : deviceId.replace("-", "");
        if (mark.length() > 8) {
            mark = mark.substring(0, 8);
        }
        StringBuilder sb = new StringBuilder(PREFIX);
        String name = tidy(author);
        if (!name.isEmpty()) {
            sb.append(name).append('-');
        }
        String group = tidy(groupCode);
        if (!group.isEmpty()) {
            sb.append(group).append('-');
        }
        sb.append(mark).append(SUFFIX);
        return sb.toString();
    }

    /** The part of a file name that says which group it belongs to, or empty. */
    private static String groupPart() {
        String group = tidy(tw.tib.financisto.utils.MyPreferences.getSyncGroupCode());
        return group.isEmpty() ? "" : "-" + group + "-";
    }

    /** A name a file system will accept, with the spaces and slashes taken out. */
    private static String tidy(String author) {
        if (author == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (char c : author.trim().toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                sb.append(c);
            } else if ((c == ' ' || c == '-' || c == '_') && sb.length() > 0
                    && sb.charAt(sb.length() - 1) != '-') {
                sb.append('-');
            }
            if (sb.length() >= 40) {
                break;
            }
        }
        while (sb.length() > 0 && sb.charAt(sb.length() - 1) == '-') {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString();
    }

    /**
     * Replaces this phone's file with the lines given.
     * <p>
     * Written whole rather than appended to. Appending through a document
     * provider is not reliably supported, and rewriting a file of a few hundred
     * lines costs nothing - while a half-appended line costs an afternoon.
     */
    public boolean write(String author, String code, String deviceId, List<String> lines) {
        try {
            String name = nameFor(author, code, deviceId);
            Log.i(TAG, "writing " + lines.size() + " lines to " + name);
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

    /**
     * Every line written by any phone but this one.
     * <p>
     * Anything in the folder that is not this phone's own file is read,
     * whatever it is called - a name changed on the other phone must not stop
     * the exchange, and old files from before the naming changed are still
     * perfectly good text.
     */
    /** Whoever was seen in the folder on the last read: {name, mark} each. */
    private final List<String[]> seen = new ArrayList<>();

    public List<String[]> whoWasThere() {
        return seen;
    }

    public List<String> readOthers(String author, String deviceId) {
        List<String> lines = new ArrayList<>();
        seen.clear();
        String mine = nameFor(author, deviceId);
        try {
            String group = groupPart();
            for (DocumentFile file : folder.listFiles()) {
                String name = file.getName();
                if (name == null || !name.startsWith(PREFIX) || name.equals(mine)) {
                    continue;
                }
                // With a group code set, only the files carrying it are read.
                // Without one, everything is read, so anybody who set this up
                // before the code existed carries on working.
                if (!group.isEmpty() && !name.contains(group)) {
                    Log.i(TAG, "not our group, left alone: " + name);
                    continue;
                }
                Log.i(TAG, "reading " + name);
                String[] who = tw.tib.financisto.sync.People.fromFileName(name);
                if (who != null) {
                    seen.add(who);
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
    public int otherPhones(String author, String deviceId) {
        int count = 0;
        String mine = nameFor(author, deviceId);
        try {
            String group = groupPart();
            for (DocumentFile file : folder.listFiles()) {
                String name = file.getName();
                if (name != null && name.startsWith(PREFIX) && !name.equals(mine)
                        && (group.isEmpty() || name.contains(group))) {
                    count++;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "could not count the files", e);
        }
        return count;
    }
}
