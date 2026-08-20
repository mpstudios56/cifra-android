/*
 * This program is made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */
package io.github.mpstudios56.cifra.export.csv;

import java.util.Date;

/**
 * One line of somebody else's file, once it has been understood.
 * <p>
 * Plain fields and no database: this is what the preview shows and what the
 * import writes, so that the two can never disagree about what a line meant.
 */
public class CsvRow {

    public Date date;
    /** In cents, sign included: negative is money out. */
    public long amount;

    public String account;
    /** Where the money went, when the line is a transfer. */
    public String transferAccount;
    public String category;
    public String parentCategory;
    public String payee;
    public String note;
    public String currency;

    public boolean transfer;
}
