/*
 * This program is made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */
package io.github.mpstudios56.cifra.export.csv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;

/**
 * Against the top of two files exported from real apps on a real phone, kept in
 * docs/campioni-import. They were copied here rather than read from disk so the
 * test says what it is testing, and so it keeps passing wherever it runs.
 * <p>
 * The point of these is that the two disagree about nearly everything: the
 * decimal separator, the date, how income is told from spending, how a transfer
 * is written. Anything that passes both is not tuned to one of them.
 */
public class CsvHeaderSnifferTest {

    /** My Expenses, Italian, with its own default switches. */
    private static final String MY_EXPENSES =
            "\"Operazione frazionata\",\"Data\",\"Ora\",\"Controparte\",\"Entrate\",\"Spesa\","
                    + "\"Categoria\",\"Note\",\"Metodo di pagamento\",\"Stato\",\"Numero di riferimento\","
                    + "\"Allegati\",\"Tag\",\"Importo originario\",\"Importo originario (Valuta)\"\n"
                    + "\"\",\"08/08/26\",\"18:56\",\"Mi Chiamo Toni\",\"0\",\"50,00\","
                    + "\"Bambini > Baby sitter\",\"\",\"\",\"\",\"\",\"\",\"Bimbi\",,\n"
                    + "\"\",\"08/08/26\",\"18:57\",\"Andrea L\",\"6000,00\",\"0\","
                    + "\"Redditi vari > Regali\",\"Wow\",\"\",\"\",\"\",\"\",\"\",,\n"
                    + "\"\",\"08/08/26\",\"18:58\",\"\",\"0\",\"500,00\","
                    + "\"[Nuovo Conto]\",\"\",\"\",\"\",\"\",\"\",\"Casa\",,\n";

    /** Bluecoins, Italian, its own demo data. */
    private static final String BLUECOINS =
            "\"Tipo\",\"Data\",\"Orario avviso\",\"Nome\",\"Importo\",\"Valuta\",\"Tasso di scambio\","
                    + "\"Gruppo di categorie\",\"Categoria\",\"Conto\",\"Note\",\"Etichette\",\"Stato\"\n"
                    + "\"Spesa\",\"2026-08-09 15:06:21.018\",\"15:06\",\"Tv via cavo\",\"-37.83\",\"EUR\",\"1.0\","
                    + "\"Utenze\",\"Cable\",\"Amex\",\"\",\"\",\"Conciliato\"\n"
                    + "\"Reddito\",\"2026-08-09 15:06:21.018\",\"15:06\",\"Commissione di vendita\",\"2402.15\",\"EUR\",\"1.0\","
                    + "\"Datore di lavoro\",\"Bonus\",\"Risparmi\",\"\",\"\",\"Nessuno\"\n"
                    + "\"Trasferimento\",\"2026-08-06 17:10:21.018\",\"17:10\",\"Pagamento con carta di credito\",\"-95.95\",\"EUR\",\"1.0\","
                    + "\"(Trasferimento)\",\"(Transfer)\",\"Conto corrente\",\"\",\"\",\"Conciliato\"\n";

    private CsvHeaderSniffer.Guess sniff(String text) throws IOException {
        return CsvHeaderSniffer.sniff(new StringReader(text));
    }

    @Test
    public void readsMyExpenses() throws Exception {
        CsvHeaderSniffer.Guess g = sniff(MY_EXPENSES);
        CsvColumnMapping m = g.mapping;

        assertEquals(',', m.delimiter);
        assertEquals(15, g.headings.size());
        assertEquals("Data", g.headings.get(1));

        assertEquals(1, m.get(CsvField.DATE));
        assertEquals(2, m.get(CsvField.TIME));
        assertEquals(3, m.get(CsvField.PAYEE));
        assertEquals(4, m.get(CsvField.INCOME));
        assertEquals(5, m.get(CsvField.EXPENSE));
        assertEquals(6, m.get(CsvField.CATEGORY));
        assertEquals(7, m.get(CsvField.NOTE));

        // Two columns rather than a sign or a type: the switch that is on out of
        // the box, and the reason this file surprised the design.
        assertEquals(CsvColumnMapping.Sign.TWO_COLUMNS, m.sign);
        assertEquals(',', m.decimalSeparator);
        assertEquals("dd/MM/yy", m.dateFormat);
        assertTrue(m.isComplete());
    }

    @Test
    public void readsBluecoins() throws Exception {
        CsvHeaderSniffer.Guess g = sniff(BLUECOINS);
        CsvColumnMapping m = g.mapping;

        assertEquals(',', m.delimiter);
        assertEquals(13, g.headings.size());

        assertEquals(0, m.get(CsvField.TYPE));
        assertEquals(1, m.get(CsvField.DATE));
        assertEquals(3, m.get(CsvField.PAYEE));
        assertEquals(4, m.get(CsvField.AMOUNT));
        assertEquals(5, m.get(CsvField.CURRENCY));
        assertEquals(7, m.get(CsvField.PARENT_CATEGORY));
        assertEquals(8, m.get(CsvField.CATEGORY));
        assertEquals(9, m.get(CsvField.ACCOUNT));

        assertEquals(CsvColumnMapping.Sign.IN_TYPE_COLUMN, m.sign);
        // A point here where the other file used a comma, on the same phone in the
        // same language: it does not follow the language and must be read off the file.
        assertEquals('.', m.decimalSeparator);
        assertEquals("yyyy-MM-dd HH:mm:ss.SSS", m.dateFormat);
        assertTrue(m.isComplete());
    }

    @Test
    public void separatesFieldsWithoutBeingFooledByQuotes() {
        String[] row = CsvHeaderSniffer.split("\"a,b\",\"c\"\"d\",e", ',');
        assertEquals(3, row.length);
        assertEquals("a,b", row[0]);
        assertEquals("c\"d", row[1]);
        assertEquals("e", row[2]);
    }

    @Test
    public void picksSemicolonWhenTheCommaIsTheDecimalPoint() throws Exception {
        CsvHeaderSniffer.Guess g = sniff("Data;Importo;Conto\n08/08/2026;-12,34;Contanti\n");
        assertEquals(';', g.mapping.delimiter);
        assertEquals(3, g.headings.size());
        assertEquals(',', g.mapping.decimalSeparator);
    }

    /**
     * 08/08/26 is a real date read as yyyy/MM/dd too - year 8, month 8, day 26 -
     * so a check that only asks whether it parsed picks the wrong pattern.
     */
    @Test
    public void doesNotReadATwoDigitYearAsAYear() throws Exception {
        CsvHeaderSniffer.Guess g = sniff("Data,Importo\n08/08/26,-12.34\n");
        assertEquals("dd/MM/yy", g.mapping.dateFormat);
    }

    /** Dates written without the leading zero still have to land somewhere. */
    @Test
    public void acceptsDatesThatAreNotPadded() throws Exception {
        CsvHeaderSniffer.Guess g = sniff("Data,Importo\n8/8/2026,-12.34\n");
        assertEquals("dd/MM/yyyy", g.mapping.dateFormat);
    }

    @Test
    public void treatsAFileWithoutHeadingsAsNumberedColumns() throws Exception {
        CsvHeaderSniffer.Guess g = sniff("08/08/2026,-12.34,Contanti\n07/08/2026,-5.00,Contanti\n");
        assertEquals(3, g.headings.size());
        assertEquals("1", g.headings.get(0));
        assertEquals(2, g.sample.size());
    }
}
