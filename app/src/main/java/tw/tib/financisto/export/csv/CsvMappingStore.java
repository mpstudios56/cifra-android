/*
 * This program is made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */
package tw.tib.financisto.export.csv;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.List;
import java.util.Locale;

/**
 * Remembers how a particular file's columns were assigned, so the next export
 * out of the same app arrives already sorted out.
 * <p>
 * Keyed by the header line rather than by the file name: the name changes with
 * every export ("bluecoins-2026-08-12.csv"), the headings do not. Two apps that
 * happened to write the same headings would share an entry, which is right -
 * the same headings mean the same columns.
 * <p>
 * Written by hand rather than as JSON. It is six values and a list of pairs,
 * and a hand-written line cannot be broken by a library renaming a field in a
 * release build.
 */
public class CsvMappingStore {

    private static final String PREFS = "csv_mappings";
    /** Bumped if the written form ever changes, so old lines are ignored rather than misread. */
    private static final String VERSION = "1";

    private CsvMappingStore() {
    }

    /**
     * What identifies a file's shape: its headings, in order, lowercased and
     * stripped of spaces. Capitalisation and padding vary between exports of
     * the same app and mean nothing.
     */
    public static String signature(List<String> headings) {
        StringBuilder b = new StringBuilder();
        for (String heading : headings) {
            if (b.length() > 0) {
                // A separator no heading can contain, so two different files
                // cannot come out with the same signature.
                b.append('');
            }
            b.append(heading == null ? "" : heading.trim().toLowerCase(Locale.ROOT));
        }
        return b.toString();
    }

    public static void save(Context context, String signature, CsvColumnMapping mapping) {
        if (signature == null || signature.isEmpty()) {
            return;
        }
        StringBuilder b = new StringBuilder(VERSION);
                // A separator no heading can contain, so two different files
                // cannot come out with the same signature.
                b.append('');
                // A separator no heading can contain, so two different files
                // cannot come out with the same signature.
                b.append('');
                // A separator no heading can contain, so two different files
                // cannot come out with the same signature.
                b.append('');
                // A separator no heading can contain, so two different files
                // cannot come out with the same signature.
                b.append('');
                // A separator no heading can contain, so two different files
                // cannot come out with the same signature.
                b.append('');
        for (CsvField field : CsvField.values()) {
            if (mapping.has(field)) {
                // A separator no heading can contain, so two different files
                // cannot come out with the same signature.
                b.append('');
            }
        }
        prefs(context).edit().putString(signature, b.toString()).apply();
    }

    /** The assignment used last time for this shape of file, or null. */
    public static CsvColumnMapping load(Context context, String signature) {
        if (signature == null || signature.isEmpty()) {
            return null;
        }
        String line = prefs(context).getString(signature, null);
        if (line == null) {
            return null;
        }
        String[] parts = line.split("\\|");
        if (parts.length < 6 || !VERSION.equals(parts[0])) {
            return null;
        }
        try {
            CsvColumnMapping mapping = new CsvColumnMapping();
            mapping.delimiter = (char) Integer.parseInt(parts[1]);
            mapping.decimalSeparator = (char) Integer.parseInt(parts[2]);
            mapping.dateFormat = parts[3];
            mapping.sign = CsvColumnMapping.Sign.valueOf(parts[4]);
            mapping.hasHeader = "1".equals(parts[5]);
            for (int i = 6; i < parts.length; i++) {
                int colon = parts[i].indexOf(':');
                if (colon <= 0) {
                    continue;
                }
                mapping.put(CsvField.valueOf(parts[i].substring(0, colon)),
                        Integer.parseInt(parts[i].substring(colon + 1)));
            }
            return mapping;
        } catch (Exception e) {
            // A line we cannot read is a line from a version that wrote it
            // differently. Forgetting it is better than importing by guesswork.
            return null;
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
