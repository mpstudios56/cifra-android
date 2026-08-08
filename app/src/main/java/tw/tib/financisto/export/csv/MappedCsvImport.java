/*
 * This program is made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */
package tw.tib.financisto.export.csv;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import tw.tib.financisto.db.DatabaseAdapter;
import tw.tib.financisto.export.CategoryCache;
import tw.tib.financisto.export.CategoryInfo;
import tw.tib.financisto.export.ProgressListener;
import tw.tib.financisto.model.Account;
import tw.tib.financisto.model.AccountType;
import tw.tib.financisto.model.Category;
import tw.tib.financisto.model.Currency;
import tw.tib.financisto.model.Payee;
import tw.tib.financisto.model.Transaction;
import tw.tib.financisto.model.TransactionAttribute;

/**
 * Writes into Cifra what somebody else's file turned out to say.
 * <p>
 * The reading was settled on the screen before this: which column is which, how
 * the amounts and dates are written, which way the money goes. All that is left
 * here is the part that cannot be undone, so it is done in one database
 * transaction - either the file lands whole, or nothing of it does.
 * <p>
 * Accounts, categories and payees the file mentions and Cifra does not have are
 * created rather than quietly dropped, and counted so it can be said at the end
 * how many appeared.
 */
public class MappedCsvImport {

    private static final String TAG = "MappedCsvImport";

    /** What the import did, in the words the summary needs. */
    public static class Result {
        public int imported;
        /** Lines with no readable date or amount. Reported, never guessed at. */
        public int skipped;
        public int accountsCreated;
        public int categoriesCreated;
        public int payeesCreated;
    }

    private final Context context;
    private final DatabaseAdapter db;
    private final CsvColumnMapping mapping;
    private final Uri uri;
    /**
     * Where the lines go when the file does not say which account they belong to.
     * <p>
     * Not a luxury: apps that export one account at a time leave its name in the
     * name of the file and nowhere else, so without this their exports have no
     * account at all.
     */
    private final String fallbackAccount;

    private ProgressListener progressListener;

    public MappedCsvImport(Context context, DatabaseAdapter db, CsvColumnMapping mapping,
                           Uri uri, String fallbackAccount) {
        this.context = context;
        this.db = db;
        this.mapping = mapping;
        this.uri = uri;
        this.fallbackAccount = fallbackAccount == null ? null : fallbackAccount.trim();
    }

    public void setProgressListener(ProgressListener progressListener) {
        this.progressListener = progressListener;
    }

    public Result doImport() throws Exception {
        Result result = new Result();
        List<CsvRow> rows = read(result);
        if (rows.isEmpty()) {
            return result;
        }

        Map<String, Account> accounts = createMissingAccounts(rows, result);
        Map<String, Category> categories = createMissingCategories(rows, result);
        Map<String, Payee> payees = createMissingPayees(rows, result);
        write(rows, accounts, categories, payees, result);
        return result;
    }

    // ------------------------------------------------------------------ reading

    private List<CsvRow> read(Result result) throws Exception {
        List<CsvRow> rows = new ArrayList<>();
        CsvRowReader reader = new CsvRowReader(mapping);
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) {
                throw new IllegalStateException("Import file not found");
            }
            BufferedReader lines = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            boolean first = true;
            while ((line = lines.readLine()) != null) {
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
                if (row == null) {
                    result.skipped++;
                } else {
                    rows.add(row);
                }
            }
        }
        Log.i(TAG, "read " + rows.size() + " rows, skipped " + result.skipped);
        return rows;
    }

    // ----------------------------------------------------------------- accounts

    /**
     * The accounts named in the file, made to exist.
     * <p>
     * They are created in the home currency, which is the only sensible guess: a
     * file that names its accounts hardly ever says what currency each one is in.
     * Renaming an account or changing its currency afterwards is a moment's work;
     * having transactions land nowhere is not.
     */
    private Map<String, Account> createMissingAccounts(List<CsvRow> rows, Result result) {
        Map<String, Account> accounts = db.getAllAccountsByTitleMap();
        Currency home = db.getHomeCurrency();

        Set<String> wanted = new LinkedHashSet<>();
        for (CsvRow row : rows) {
            add(wanted, row.account);
            add(wanted, row.transferAccount);
            if (row.account == null || row.account.trim().isEmpty()) {
                // Only then: an account made for lines that turn out never to need it
                // would be an empty account nobody asked for.
                add(wanted, fallbackAccount);
            }
        }
        for (String title : wanted) {
            if (accounts.containsKey(title)) {
                continue;
            }
            Account account = new Account();
            account.id = -1;
            account.title = title;
            account.currency = home;
            account.type = AccountType.CASH.name();
            account.isActive = true;
            account.creationDate = System.currentTimeMillis();
            account.icon = "";
            account.accentColor = "";
            db.saveAccount(account);
            accounts.put(title, account);
            result.accountsCreated++;
        }
        return accounts;
    }

    private void add(Set<String> set, String value) {
        if (value != null && !value.trim().isEmpty()) {
            set.add(value.trim());
        }
    }

    // --------------------------------------------------------------- categories

    /**
     * The categories named in the file, made to exist, keeping the group above the
     * category when the file has both.
     * <p>
     * Income or expense is decided by the sign of the first line filed under each,
     * which is what Cifra needs to know and what no file ever states.
     */
    private Map<String, Category> createMissingCategories(List<CsvRow> rows, Result result) {
        CategoryCache cache = new CategoryCache();
        cache.loadExistingCategories(db);
        int before = cache.categoryNameToCategory.size();

        Set<CategoryInfo> wanted = new LinkedHashSet<>();
        Set<String> seen = new HashSet<>();
        for (CsvRow row : rows) {
            String name = categoryName(row);
            if (name != null && seen.add(name)) {
                wanted.add(new CategoryInfo(name, row.amount > 0));
            }
        }
        cache.insertCategories(db, wanted);
        result.categoriesCreated = Math.max(0, cache.categoryNameToCategory.size() - before);
        return cache.categoryNameToCategory;
    }

    private String categoryName(CsvRow row) {
        if (row.transfer || row.category == null || row.category.trim().isEmpty()) {
            return null;
        }
        String category = row.category.trim();
        if (row.parentCategory != null && !row.parentCategory.trim().isEmpty()) {
            return row.parentCategory.trim() + CategoryInfo.SEPARATOR + category;
        }
        return category;
    }

    // ------------------------------------------------------------------- payees

    private Map<String, Payee> createMissingPayees(List<CsvRow> rows, Result result) {
        Map<String, Payee> payees = db.getAllPayeeByTitleMap();
        for (CsvRow row : rows) {
            String title = row.payee == null ? null : row.payee.trim();
            if (title == null || title.isEmpty() || payees.containsKey(title)) {
                continue;
            }
            Payee payee = new Payee();
            payee.title = title;
            db.saveOrUpdate(payee);
            payees.put(title, payee);
            result.payeesCreated++;
        }
        return payees;
    }

    // ------------------------------------------------------------------ writing

    private void write(List<CsvRow> rows, Map<String, Account> accounts,
                       Map<String, Category> categories, Map<String, Payee> payees,
                       Result result) {
        List<TransactionAttribute> noAttributes = Collections.emptyList();
        SQLiteDatabase database = db.db();
        database.beginTransaction();
        try {
            int count = 0;
            for (CsvRow row : rows) {
                Transaction t = build(row, accounts, categories, payees);
                if (t == null) {
                    result.skipped++;
                    continue;
                }
                db.insertOrUpdateInTransaction(t, noAttributes);
                result.imported++;
                if (++count % 100 == 0 && progressListener != null) {
                    progressListener.onProgress((int) (100f * count / rows.size()));
                }
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    private Transaction build(CsvRow row, Map<String, Account> accounts,
                              Map<String, Category> categories, Map<String, Payee> payees) {
        Account from = row.account == null ? null : accounts.get(row.account.trim());
        if (from == null && fallbackAccount != null) {
            from = accounts.get(fallbackAccount);
        }
        if (from == null) {
            return null;
        }

        Transaction t = new Transaction();
        t.dateTime = row.date.getTime();
        t.fromAccountId = from.id;
        t.fromAmount = scale(row.amount, from);
        t.note = row.note;

        Account to = row.transferAccount == null ? null : accounts.get(row.transferAccount.trim());
        if (row.transfer && to != null && to.id != from.id) {
            // The money leaves one account and arrives in the other, so the two sides
            // are written with opposite signs whichever way round the file put it.
            long amount = Math.abs(row.amount);
            t.fromAmount = -scale(amount, from);
            t.toAccountId = to.id;
            t.toAmount = scale(amount, to);
            return t;
        }
        // A transfer whose other side is not named - the two-cancelling-rows habit -
        // is left as an ordinary line on its own account. Both accounts still end up
        // right; pairing the two halves by guesswork would risk being wrong.
        Category category = categories.get(categoryName(row));
        if (category != null) {
            t.categoryId = category.id;
        }
        Payee payee = row.payee == null ? null : payees.get(row.payee.trim());
        if (payee != null) {
            t.payeeId = payee.id;
        }
        return t;
    }

    /**
     * Cents into whatever the account's currency counts in. Nearly always the same
     * thing; not for the few currencies Cifra stores to more than two places.
     */
    private long scale(long cents, Account account) {
        int scale = account.currency == null ? 2 : account.currency.getScale();
        if (scale == 2) {
            return cents;
        }
        return BigDecimal.valueOf(cents).movePointRight(scale - 2).longValue();
    }
}
