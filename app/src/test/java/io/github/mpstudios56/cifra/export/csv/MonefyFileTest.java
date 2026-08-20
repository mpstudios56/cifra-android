/*
 * This program is made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */
package io.github.mpstudios56.cifra.export.csv;

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
 * A real export from Monefy.
 * <p>
 * Its header is the reason this file is worth keeping: it names two of its
 * columns "currency" - one for the amount as entered, one for the amount
 * converted - so anything matching columns by their name meets the same name
 * twice and has to come out of it with something sensible.
 * <p>
 * It also writes whole numbers with no decimal part at all (-78), names its
 * account on every line, and puts the sign on the amount.
 */
public class MonefyFileTest {

    /** Skipped rather than failed when run outside the repository. */
    private File sample() {
        File file = new File("../docs/campioni-import/monefy.csv");
        assumeTrue("sample file not available: " + file, file.isFile());
        return file;
    }

    private CsvHeaderSniffer.Guess sniff(File file) throws Exception {
        try (InputStreamReader in = new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8)) {
            return CsvHeaderSniffer.sniff(in);
        }
    }

    @Test
    public void readsTheMonefyExport() throws Exception {
        File file = sample();
        CsvHeaderSniffer.Guess guess = sniff(file);
        CsvColumnMapping mapping = guess.mapping;

        assertTrue("not enough was recognised to import anything", mapping.isComplete());
        assertEquals(',', mapping.delimiter);
        assertEquals(CsvColumnMapping.Sign.IN_AMOUNT, mapping.sign);
        assertEquals("dd/MM/yyyy", mapping.dateFormat);

        assertEquals(0, mapping.get(CsvField.DATE));
        assertEquals(1, mapping.get(CsvField.ACCOUNT));
        assertEquals(2, mapping.get(CsvField.CATEGORY));
        assertEquals(3, mapping.get(CsvField.AMOUNT));
        // Two columns are called "currency". Either is right - both hold EUR on
        // every line - but it has to settle on one of them and not on nothing.
        int currency = mapping.get(CsvField.CURRENCY);
        assertTrue("the repeated heading lost the currency column", currency == 4 || currency == 6);

        List<CsvRow> rows = new ArrayList<>();
        CsvRowReader reader = new CsvRowReader(mapping);
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            boolean first = true;
            while ((line = in.readLine()) != null) {
                if (first) {
                    first = false;
                    continue;
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

        assertEquals(10, rows.size());
        int in = 0, out = 0;
        for (CsvRow row : rows) {
            assertTrue("a line with no date", row.date != null);
            assertTrue("a line with no account", row.account != null);
            if (row.amount > 0) in++;
            if (row.amount < 0) out++;
        }
        assertEquals("the one salary line", 1, in);
        assertEquals(9, out);

        // Whole numbers with no decimal part: -78 is seventy-eight euro, not
        // seventy-eight cents.
        assertEquals(-7800, rows.get(0).amount);
        assertEquals(2000, rows.get(9).amount);
    }
}
