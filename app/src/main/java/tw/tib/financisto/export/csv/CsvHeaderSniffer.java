/*
 * This program is made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */
package tw.tib.financisto.export.csv;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads the top of somebody else's CSV and works out what it is looking at.
 * <p>
 * Everything here is a proposal, shown on screen for correction before a single
 * row is imported. Nothing is decided in silence: the two files this was built
 * against disagree about the delimiter, the decimal separator, the date format,
 * how income is told from spending and how a transfer is written, and one of
 * them changes its own shape according to four switches buried in its settings.
 * <p>
 * Deliberately free of Android, so it can be tested on the real files.
 */
public class CsvHeaderSniffer {

    /** How many lines are enough to tell: the header and a few transactions. */
    private static final int SAMPLE_LINES = 20;
    private static final char[] DELIMITERS = {',', ';', '\t', '|'};

    /** The date formats worth trying, most specific first. */
    private static final String[] DATE_FORMATS = {
            "yyyy-MM-dd HH:mm:ss.SSS", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd", "yyyy/MM/dd",
            "dd/MM/yyyy HH:mm", "dd/MM/yyyy", "dd/MM/yy",
            "MM/dd/yyyy", "MM/dd/yy",
            "dd.MM.yyyy", "dd.MM.yy", "dd-MM-yyyy",
    };

    public static class Guess {
        public CsvColumnMapping mapping = new CsvColumnMapping();
        /** The headings as they appear in the file, for the screen to list. */
        public List<String> headings = new ArrayList<>();
        /** The first few rows, so somebody can see what they are assigning. */
        public List<String[]> sample = new ArrayList<>();
    }

    private CsvHeaderSniffer() {
    }

    public static Guess sniff(Reader reader) throws IOException {
        List<String> lines = readSample(reader);
        Guess guess = new Guess();
        if (lines.isEmpty()) {
            return guess;
        }
        guess.mapping.delimiter = guessDelimiter(lines);

        List<String[]> rows = new ArrayList<>();
        for (String line : lines) {
            rows.add(split(line, guess.mapping.delimiter));
        }
        String[] first = rows.get(0);

        // A header if nothing in the first row looks like a number: every app tested
        // writes one, but a file cut down by hand might not.
        guess.mapping.hasHeader = looksLikeHeader(first);
        if (guess.mapping.hasHeader) {
            for (String heading : first) {
                guess.headings.add(heading);
            }
            guess.sample = rows.subList(1, rows.size());
            propose(guess);
        } else {
            for (int i = 0; i < first.length; i++) {
                guess.headings.add(String.valueOf(i + 1));
            }
            guess.sample = rows;
        }

        guess.mapping.decimalSeparator = guessDecimalSeparator(guess);
        String dateFormat = guessDateFormat(guess);
        if (dateFormat != null) {
            guess.mapping.dateFormat = dateFormat;
        }
        return guess;
    }

    // ------------------------------------------------------------------ reading

    private static List<String> readSample(Reader reader) throws IOException {
        BufferedReader in = new BufferedReader(reader);
        List<String> lines = new ArrayList<>();
        String line;
        while (lines.size() < SAMPLE_LINES && (line = in.readLine()) != null) {
            if (!line.trim().isEmpty()) {
                lines.add(line);
            }
        }
        return lines;
    }

    /**
     * Whichever candidate splits every sampled line into the same number of fields,
     * and the most of them. Counting on the header alone is not enough: a comma is
     * also the decimal separator in half of Europe, so the winner has to be the one
     * that keeps the rows the same width as the header.
     */
    static char guessDelimiter(List<String> lines) {
        char best = ',';
        int bestWidth = 0;
        for (char candidate : DELIMITERS) {
            int width = split(lines.get(0), candidate).length;
            if (width < 2) {
                continue;
            }
            boolean consistent = true;
            for (String line : lines) {
                if (split(line, candidate).length != width) {
                    consistent = false;
                    break;
                }
            }
            if (consistent && width > bestWidth) {
                best = candidate;
                bestWidth = width;
            }
        }
        return best;
    }

    /** A CSV split that respects quotes and doubled quotes inside them. */
    static String[] split(String line, char delimiter) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == delimiter) {
                fields.add(field.toString().trim());
                field.setLength(0);
            } else {
                field.append(c);
            }
        }
        fields.add(field.toString().trim());
        return fields.toArray(new String[0]);
    }

    static boolean looksLikeHeader(String[] first) {
        for (String cell : first) {
            if (cell != null && cell.matches("-?[\\d.,]+") && cell.matches(".*\\d.*")) {
                return false;
            }
        }
        return true;
    }

    // ----------------------------------------------------------------- proposing

    /**
     * Assigns each field to the heading that matches it best, letting no heading
     * serve two fields. Ties are broken by the order the headings appear, which is
     * as good a rule as any and at least a predictable one.
     */
    static void propose(Guess guess) {
        Map<CsvField, Integer> bestColumn = new EnumMap<>(CsvField.class);
        Map<CsvField, Integer> bestScore = new EnumMap<>(CsvField.class);
        boolean[] taken = new boolean[guess.headings.size()];

        for (CsvField field : CsvField.values()) {
            for (int i = 0; i < guess.headings.size(); i++) {
                int score = field.score(guess.headings.get(i));
                Integer current = bestScore.get(field);
                if (score > 0 && (current == null || score > current)) {
                    bestScore.put(field, score);
                    bestColumn.put(field, i);
                }
            }
        }
        // Exact matches first, so a heading that is simply "Data" is not taken by a
        // field that merely contains the word.
        for (int wanted = 2; wanted >= 1; wanted--) {
            for (CsvField field : CsvField.values()) {
                Integer score = bestScore.get(field);
                Integer column = bestColumn.get(field);
                if (score != null && score == wanted && column != null && !taken[column]
                        && !guess.mapping.has(field)) {
                    guess.mapping.put(field, column);
                    taken[column] = true;
                }
            }
        }
        guess.mapping.sign = guessSign(guess.mapping);
    }

    static CsvColumnMapping.Sign guessSign(CsvColumnMapping mapping) {
        if (mapping.has(CsvField.INCOME) && mapping.has(CsvField.EXPENSE)) {
            return CsvColumnMapping.Sign.TWO_COLUMNS;
        }
        if (mapping.has(CsvField.TYPE)) {
            return CsvColumnMapping.Sign.IN_TYPE_COLUMN;
        }
        return CsvColumnMapping.Sign.IN_AMOUNT;
    }

    // ----------------------------------------------------------------- detecting

    /**
     * Whether the decimals are written after a point or after a comma, read off
     * the amounts themselves. It does not follow the language: the two files this
     * was built against disagree on the same phone.
     */
    static char guessDecimalSeparator(Guess guess) {
        int points = 0, commas = 0;
        for (CsvField field : new CsvField[]{CsvField.AMOUNT, CsvField.INCOME, CsvField.EXPENSE}) {
            int column = guess.mapping.get(field);
            if (column < 0) {
                continue;
            }
            for (String[] row : guess.sample) {
                if (column >= row.length) {
                    continue;
                }
                String value = row[column];
                if (value.matches("-?\\d+\\.\\d{1,2}")) {
                    points++;
                } else if (value.matches("-?\\d+,\\d{1,2}")) {
                    commas++;
                }
            }
        }
        return commas > points ? ',' : '.';
    }

    /**
     * The first pattern that reads every date in the sample and writes it back
     * unchanged.
     * <p>
     * Parsing alone is not enough to tell patterns apart. {@code 08/08/26} parses
     * happily as {@code yyyy/MM/dd} - year 8, month 8, day 26 - and is a perfectly
     * real date, so a check that only asks "did it parse" picks whichever pattern
     * comes first in the list and is wrong two thirds of the time. Writing the
     * date back and comparing settles it: that reading would be written
     * {@code 0008/08/26}, which is not what the file says.
     * <p>
     * Round tripping is strict about padding as well, so a file with {@code 8/8/26}
     * would match nothing. Hence the second pass, which drops the comparison and
     * accepts anything that at least parses.
     */
    static String guessDateFormat(Guess guess) {
        int column = guess.mapping.get(CsvField.DATE);
        if (column < 0) {
            return null;
        }
        List<String> values = new ArrayList<>();
        for (String[] row : guess.sample) {
            if (column < row.length && !row[column].isEmpty()) {
                values.add(row[column]);
            }
        }
        if (values.isEmpty()) {
            return null;
        }
        String exact = matchDateFormat(values, true);
        return exact != null ? exact : matchDateFormat(values, false);
    }

    private static String matchDateFormat(List<String> values, boolean mustWriteBack) {
        for (String pattern : DATE_FORMATS) {
            SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
            format.setLenient(false);
            boolean all = true;
            for (String value : values) {
                try {
                    Date date = format.parse(value);
                    if (mustWriteBack && !format.format(date).equals(value)) {
                        all = false;
                        break;
                    }
                } catch (ParseException e) {
                    all = false;
                    break;
                }
            }
            if (all) {
                return pattern;
            }
        }
        return null;
    }
}
