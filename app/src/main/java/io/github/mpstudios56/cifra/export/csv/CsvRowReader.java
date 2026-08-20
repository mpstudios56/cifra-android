/*
 * This program is made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */
package io.github.mpstudios56.cifra.export.csv;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Turns a line of somebody else's file into something Cifra can talk about,
 * following an assignment that a person has seen and approved.
 * <p>
 * The preview and the import both go through here, so that what was shown on
 * screen and what ends up in the database cannot drift apart. It reads and
 * decides; it writes nothing.
 */
public class CsvRowReader {

    /** What a Type column says when the line moves money between two accounts. */
    private static final List<String> TRANSFER_WORDS = Arrays.asList(
            "transfer", "trasferimento", "transferencia", "transferencias",
            "uberweisung", "umbuchung", "virement", "overboeking");

    private final CsvColumnMapping mapping;
    private final SimpleDateFormat dateFormat;

    public CsvRowReader(CsvColumnMapping mapping) {
        this.mapping = mapping;
        this.dateFormat = new SimpleDateFormat(mapping.dateFormat, Locale.US);
        this.dateFormat.setLenient(true);
    }

    /**
     * The line understood, or null when it cannot be: no date, or no amount. A
     * line that cannot be read is counted and reported at the end rather than
     * guessed at, because a guess here is somebody's money.
     */
    public CsvRow read(String[] values) {
        CsvRow row = new CsvRow();
        row.date = date(text(values, CsvField.DATE), text(values, CsvField.TIME));
        if (row.date == null) {
            return null;
        }

        Long amount = amount(values);
        if (amount == null) {
            return null;
        }
        row.amount = amount;

        row.account = text(values, CsvField.ACCOUNT);
        row.transferAccount = text(values, CsvField.TRANSFER_ACCOUNT);
        row.category = text(values, CsvField.CATEGORY);
        row.parentCategory = text(values, CsvField.PARENT_CATEGORY);
        row.payee = text(values, CsvField.PAYEE);
        row.note = text(values, CsvField.NOTE);
        row.currency = text(values, CsvField.CURRENCY);

        readTransfer(values, row);
        return row;
    }

    // ------------------------------------------------------------------ transfer

    /**
     * Three conventions, all three found in the wild and all three handled: a Type
     * column that says so; a second account column; and - inherited from the QIF
     * format, and what "Le mie spese" writes - a category that is not a category at
     * all but the other account, in square brackets.
     */
    private void readTransfer(String[] values, CsvRow row) {
        String category = row.category;
        if (category != null && category.length() > 2
                && category.startsWith("[") && category.endsWith("]")) {
            row.transfer = true;
            row.transferAccount = category.substring(1, category.length() - 1).trim();
            row.category = null;
            return;
        }
        if (row.transferAccount != null && !row.transferAccount.isEmpty()) {
            row.transfer = true;
            return;
        }
        String type = text(values, CsvField.TYPE);
        if (type != null && TRANSFER_WORDS.contains(CsvField.normalise(type))) {
            row.transfer = true;
            // Bluecoins files a transfer under a category called "(Transfer)", brackets
            // and all. It is a marker, not a category, and importing it would leave a
            // category by that name in somebody's list forever.
            if (category != null && category.startsWith("(") && category.endsWith(")")) {
                row.category = null;
            }
            if (row.parentCategory != null && row.parentCategory.startsWith("(")
                    && row.parentCategory.endsWith(")")) {
                row.parentCategory = null;
            }
        }
    }

    // -------------------------------------------------------------------- amount

    /**
     * The amount with its sign, whichever of the three ways the file says it.
     * Null when the line carries no readable number at all.
     */
    private Long amount(String[] values) {
        if (mapping.sign == CsvColumnMapping.Sign.TWO_COLUMNS) {
            Long in = cents(text(values, CsvField.INCOME));
            Long out = cents(text(values, CsvField.EXPENSE));
            if (in == null && out == null) {
                return null;
            }
            // One of the two is zero, or empty. Subtracting works either way, and
            // does not care which of the two the file chose to leave blank.
            return (in == null ? 0 : Math.abs(in)) - (out == null ? 0 : Math.abs(out));
        }

        Long amount = cents(text(values, CsvField.AMOUNT));
        if (amount == null) {
            return null;
        }
        if (mapping.sign == CsvColumnMapping.Sign.IN_TYPE_COLUMN) {
            String type = text(values, CsvField.TYPE);
            // Bluecoins writes both the word and the sign, and they agree. Taking the
            // absolute value first means that a file where they disagree, or where
            // only one of the two is present, still comes out right.
            if (type != null && CsvField.EXPENSE.score(type) > 0) {
                return -Math.abs(amount);
            }
            if (type != null && CsvField.INCOME.score(type) > 0) {
                return Math.abs(amount);
            }
        }
        return amount;
    }

    /**
     * A written amount as cents. Everything that is not a digit, a minus or the
     * decimal separator is thrown away, which takes care of currency symbols,
     * spaces, non breaking spaces and the thousands separator in one go.
     */
    static Long cents(String value, char decimalSeparator) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        if (text.isEmpty()) {
            return null;
        }
        // Two ways of writing a negative that are not a leading minus: accountants
        // put it in brackets, some banks put the minus at the end.
        boolean negative = (text.startsWith("(") && text.endsWith(")")) || text.endsWith("-");

        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '0' && c <= '9') {
                digits.append(c);
            } else if (c == decimalSeparator) {
                digits.append('.');
            } else if (c == '-' && digits.length() == 0) {
                negative = true;
            }
        }
        if (digits.length() == 0 || digits.toString().equals(".")) {
            return null;
        }
        try {
            BigDecimal amount = new BigDecimal(digits.toString())
                    .movePointRight(2).setScale(0, RoundingMode.HALF_UP);
            return negative ? -amount.longValueExact() : amount.longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            return null;
        }
    }

    private Long cents(String value) {
        return cents(value, mapping.decimalSeparator);
    }

    // ---------------------------------------------------------------------- date

    /**
     * The date, and the time when the file keeps it in a column of its own. Parsed
     * from the start of the value, so a date column that also carries a time the
     * chosen pattern does not mention is read rather than refused.
     */
    private Date date(String value, String time) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String text = value.trim();
        if (time != null && !time.trim().isEmpty() && !mapping.dateFormat.contains("H")) {
            Date withTime = parse(text + " " + time.trim(), mapping.dateFormat + " HH:mm");
            if (withTime != null) {
                return withTime;
            }
        }
        return parse(text, mapping.dateFormat);
    }

    private Date parse(String text, String pattern) {
        SimpleDateFormat format = pattern.equals(mapping.dateFormat)
                ? dateFormat : new SimpleDateFormat(pattern, Locale.US);
        ParsePosition position = new ParsePosition(0);
        Date date = format.parse(text, position);
        return position.getIndex() == 0 ? null : date;
    }

    // --------------------------------------------------------------------- utils

    private String text(String[] values, CsvField field) {
        String value = mapping.value(values, field);
        if (value == null) {
            return null;
        }
        value = value.trim();
        return value.isEmpty() ? null : value;
    }
}
