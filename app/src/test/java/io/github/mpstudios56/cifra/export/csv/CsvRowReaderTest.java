/*
 * This program is made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */
package io.github.mpstudios56.cifra.export.csv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * The three ways a file can say that money went out, and the three ways it can
 * say that money moved between two accounts. Every one of them is taken from a
 * file an app really wrote, not from a specification.
 */
public class CsvRowReaderTest {

    private String day(CsvRow row) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(row.date);
    }

    // --------------------------------------------------------------- Le mie spese

    /** Two columns, a zero in the one that does not apply, commas for decimals. */
    private CsvColumnMapping myExpenses() {
        CsvColumnMapping m = new CsvColumnMapping();
        m.sign = CsvColumnMapping.Sign.TWO_COLUMNS;
        m.decimalSeparator = ',';
        m.dateFormat = "dd/MM/yy";
        m.put(CsvField.DATE, 0);
        m.put(CsvField.TIME, 1);
        m.put(CsvField.PAYEE, 2);
        m.put(CsvField.INCOME, 3);
        m.put(CsvField.EXPENSE, 4);
        m.put(CsvField.CATEGORY, 5);
        return m;
    }

    @Test
    public void readsAnExpenseWrittenInItsOwnColumn() {
        CsvRow row = new CsvRowReader(myExpenses()).read(
                new String[]{"08/08/26", "18:56", "Mi Chiamo Toni", "0", "50,00", "Baby sitter"});
        assertNotNull(row);
        assertEquals("2026-08-08", day(row));
        assertEquals(-5000, row.amount);
        assertEquals("Mi Chiamo Toni", row.payee);
        assertFalse(row.transfer);
    }

    @Test
    public void readsAnIncomeWrittenInTheOtherColumn() {
        CsvRow row = new CsvRowReader(myExpenses()).read(
                new String[]{"08/08/26", "18:57", "Andrea L", "6000,00", "0", "Regali"});
        assertNotNull(row);
        assertEquals(600000, row.amount);
    }

    /**
     * The QIF convention: the category is not a category, it is the other account
     * in square brackets. Missing it would put the money in the wrong place twice.
     */
    @Test
    public void readsATransferHiddenInTheCategory() {
        CsvRow row = new CsvRowReader(myExpenses()).read(
                new String[]{"08/08/26", "18:58", "", "0", "500,00", "[Nuovo Conto]"});
        assertNotNull(row);
        assertTrue(row.transfer);
        assertEquals("Nuovo Conto", row.transferAccount);
        assertNull(row.category);
        assertEquals(-50000, row.amount);
    }

    /** The time is in a column of its own and has to be put back on the date. */
    @Test
    public void putsTheTimeBackOnTheDate() {
        CsvRow row = new CsvRowReader(myExpenses()).read(
                new String[]{"08/08/26", "18:56", "", "0", "1,00", ""});
        assertNotNull(row);
        assertEquals("2026-08-08 18:56",
                new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(row.date));
    }

    // ------------------------------------------------------------------ Bluecoins

    /** A Type column in words, a sign as well, and points for decimals. */
    private CsvColumnMapping bluecoins() {
        CsvColumnMapping m = new CsvColumnMapping();
        m.sign = CsvColumnMapping.Sign.IN_TYPE_COLUMN;
        m.decimalSeparator = '.';
        m.dateFormat = "yyyy-MM-dd HH:mm:ss.SSS";
        m.put(CsvField.TYPE, 0);
        m.put(CsvField.DATE, 1);
        m.put(CsvField.PAYEE, 2);
        m.put(CsvField.AMOUNT, 3);
        m.put(CsvField.PARENT_CATEGORY, 4);
        m.put(CsvField.CATEGORY, 5);
        m.put(CsvField.ACCOUNT, 6);
        return m;
    }

    @Test
    public void readsAnExpenseAnnouncedByItsType() {
        CsvRow row = new CsvRowReader(bluecoins()).read(new String[]{
                "Spesa", "2026-08-09 15:06:21.018", "Tv via cavo", "-37.83",
                "Utenze", "Cable", "Amex"});
        assertNotNull(row);
        assertEquals("2026-08-09", day(row));
        assertEquals(-3783, row.amount);
        assertEquals("Amex", row.account);
        assertEquals("Utenze", row.parentCategory);
        assertFalse(row.transfer);
    }

    @Test
    public void readsAnIncomeAnnouncedByItsType() {
        CsvRow row = new CsvRowReader(bluecoins()).read(new String[]{
                "Reddito", "2026-08-09 15:06:21.018", "Commissione di vendita", "2402.15",
                "Datore di lavoro", "Bonus", "Risparmi"});
        assertNotNull(row);
        assertEquals(240215, row.amount);
    }

    /**
     * The word and the sign both say expense here. Where they disagree - and files
     * where the sign was dropped are common - the word decides.
     */
    @Test
    public void letsTheWordDecideWhenTheSignIsMissing() {
        CsvRow row = new CsvRowReader(bluecoins()).read(new String[]{
                "Spesa", "2026-08-09 15:06:21.018", "Tv via cavo", "37.83",
                "Utenze", "Cable", "Amex"});
        assertNotNull(row);
        assertEquals(-3783, row.amount);
    }

    /** Its transfers are two rows that cancel out, filed under a made up category. */
    @Test
    public void readsATransferAnnouncedByItsType() {
        CsvRow row = new CsvRowReader(bluecoins()).read(new String[]{
                "Trasferimento", "2026-08-06 17:10:21.018", "Pagamento con carta di credito",
                "-95.95", "(Trasferimento)", "(Transfer)", "Conto corrente"});
        assertNotNull(row);
        assertTrue(row.transfer);
        assertEquals(-9595, row.amount);
        assertNull(row.category);
        assertNull(row.parentCategory);
    }

    // ------------------------------------------------------------------- amounts

    @Test
    public void readsAmountsHoweverTheyAreWritten() {
        assertEquals(Long.valueOf(123456), CsvRowReader.cents("1,234.56", '.'));
        assertEquals(Long.valueOf(123456), CsvRowReader.cents("1.234,56", ','));
        assertEquals(Long.valueOf(-1234), CsvRowReader.cents("-12,34", ','));
        assertEquals(Long.valueOf(-1234), CsvRowReader.cents("(12.34)", '.'));
        assertEquals(Long.valueOf(-1234), CsvRowReader.cents("12.34-", '.'));
        assertEquals(Long.valueOf(-1234), CsvRowReader.cents("-€ 12.34", '.'));
        assertEquals(Long.valueOf(500), CsvRowReader.cents("5", '.'));
        assertEquals(Long.valueOf(0), CsvRowReader.cents("0", ','));
        // A third decimal has to be rounded rather than shifting everything by ten.
        assertEquals(Long.valueOf(1235), CsvRowReader.cents("12.345", '.'));
        assertNull(CsvRowReader.cents("", '.'));
        assertNull(CsvRowReader.cents(null, '.'));
        assertNull(CsvRowReader.cents("n/d", '.'));
    }

    // -------------------------------------------------------------- refusing rows

    /** A line with no date is reported, never guessed at: it is somebody's money. */
    @Test
    public void refusesALineWithoutADate() {
        assertNull(new CsvRowReader(myExpenses()).read(
                new String[]{"", "18:56", "", "0", "50,00", ""}));
        assertNull(new CsvRowReader(myExpenses()).read(
                new String[]{"nessuna data", "18:56", "", "0", "50,00", ""}));
    }

    @Test
    public void refusesALineWithoutAnAmount() {
        assertNull(new CsvRowReader(myExpenses()).read(
                new String[]{"08/08/26", "18:56", "", "", "", ""}));
    }
}
