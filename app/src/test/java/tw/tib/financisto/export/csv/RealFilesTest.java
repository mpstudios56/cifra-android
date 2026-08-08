/*
 * This program is made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */
package tw.tib.financisto.export.csv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The whole of the two files, not just the twenty lines the sniffer looks at.
 * <p>
 * Sniffing the top of a file and reading the rest of it are different jobs, and
 * the second one is where a file surprises you: a line with an empty date three
 * hundred rows down, a category with a comma in it, a transfer written in a way
 * the header gave no hint of. These are the only two real exports we have, so
 * they are read from end to end here rather than trusted.
 */
public class RealFilesTest {

    /** Skipped rather than failed when run outside the repository. */
    private File sample(String name) {
        File file = new File("../docs/campioni-import/" + name);
        assumeTrue("sample file not available: " + file, file.isFile());
        return file;
    }

    private List<CsvRow> readAll(File file, CsvColumnMapping mapping) throws Exception {
        List<CsvRow> rows = new ArrayList<>();
        CsvRowReader reader = new CsvRowReader(mapping);
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            boolean first = true;
            while ((line = in.readLine()) != null) {
                if (first) {
                    first = false;
                    if (mapping.hasHeader) {
                        continue;
                    }
                }
                if (line.trim().isEmpty()) {
                    continue;
                }
                CsvRow row = reader.read(CsvHeaderSniffer.split(line, mapping.delimiter));
                if (row != null) {
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    private CsvHeaderSniffer.Guess sniff(File file) throws Exception {
        try (InputStreamReader in = new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8)) {
            return CsvHeaderSniffer.sniff(in);
        }
    }

    @Test
    public void readsTheWholeBluecoinsExport() throws Exception {
        File file = sample("bluecoins.csv");
        CsvHeaderSniffer.Guess guess = sniff(file);
        assertTrue(guess.mapping.isComplete());
        assertEquals(CsvColumnMapping.Sign.IN_TYPE_COLUMN, guess.mapping.sign);

        List<CsvRow> rows = readAll(file, guess.mapping);
        assertTrue("expected the demo data, got " + rows.size() + " rows", rows.size() > 500);

        int transfers = 0, in = 0, out = 0;
        for (CsvRow row : rows) {
            if (row.transfer) {
                transfers++;
            } else if (row.amount > 0) {
                in++;
            } else if (row.amount < 0) {
                out++;
            }
            // Every line of this file names its account, which is what makes it the
            // easy one. If that ever stops being true the import needs to know.
            assertTrue("a line with no account: " + row.payee, row.account != null);
        }
        assertTrue("no transfers found", transfers > 0);
        assertTrue("no income found", in > 0);
        assertTrue("no spending found", out > 0);
    }

    @Test
    public void readsTheWholeMyExpensesExport() throws Exception {
        File file = sample("myexpenses.csv");
        CsvHeaderSniffer.Guess guess = sniff(file);
        assertTrue(guess.mapping.isComplete());
        assertEquals(CsvColumnMapping.Sign.TWO_COLUMNS, guess.mapping.sign);
        // This one exports a single account and leaves its name in the name of the
        // file, which is why the import has to be able to be told where to put them.
        assertTrue(guess.mapping.get(CsvField.ACCOUNT) < 0);

        List<CsvRow> rows = readAll(file, guess.mapping);
        assertTrue("nothing read", rows.size() > 0);
        for (CsvRow row : rows) {
            assertTrue("a line with no date", row.date != null);
        }
    }
}
