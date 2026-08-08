/*
 * This program is made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */
package tw.tib.financisto.export.csv;

import java.util.EnumMap;
import java.util.Map;

/**
 * Which column of the file holds which of our fields.
 * <p>
 * Kept apart from the reading and the writing so it can be proposed by the
 * sniffer, corrected by hand on screen, saved under a name, and read back the
 * next time somebody imports from the same app.
 */
public class CsvColumnMapping {

    /** How the file says whether a row is money in or money out. */
    public enum Sign {
        /** The amount carries it: negative is spending. */
        IN_AMOUNT,
        /** A column says it in words: Expense, Income, Transfer. */
        IN_TYPE_COLUMN,
        /** Two columns, one for each, with a zero in the one that does not apply. */
        TWO_COLUMNS,
    }

    private final Map<CsvField, Integer> columns = new EnumMap<>(CsvField.class);

    public char delimiter = ',';
    public char decimalSeparator = '.';
    public String dateFormat = "dd/MM/yyyy";
    public Sign sign = Sign.IN_AMOUNT;
    /** True when the first line names the columns rather than being a transaction. */
    public boolean hasHeader = true;

    public void put(CsvField field, int column) {
        if (column < 0) {
            columns.remove(field);
        } else {
            columns.put(field, column);
        }
    }

    /** The column holding this field, or -1. */
    public int get(CsvField field) {
        Integer column = columns.get(field);
        return column == null ? -1 : column;
    }

    public boolean has(CsvField field) {
        return columns.containsKey(field);
    }

    /** The value of a field in a row, or null when the file has not got it. */
    public String value(String[] row, CsvField field) {
        int column = get(field);
        return column >= 0 && column < row.length ? row[column] : null;
    }

    /**
     * Whether enough has been assigned to import anything at all: a date, and some
     * way of arriving at an amount.
     */
    public boolean isComplete() {
        if (!has(CsvField.DATE)) {
            return false;
        }
        return sign == Sign.TWO_COLUMNS
                ? has(CsvField.INCOME) && has(CsvField.EXPENSE)
                : has(CsvField.AMOUNT);
    }
}
