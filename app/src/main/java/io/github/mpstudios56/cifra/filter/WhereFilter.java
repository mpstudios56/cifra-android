package io.github.mpstudios56.cifra.filter;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import io.github.mpstudios56.cifra.activity.DateFilterActivity;
import io.github.mpstudios56.cifra.blotter.BlotterFilter;
import io.github.mpstudios56.cifra.datetime.PeriodType;

/**
 * The whole question a list is asking of the database.
 * <p>
 * It holds one condition per column - the account, the period, the category,
 * the state - and the order the answer should come back in. Adding a condition
 * for a column that already has one replaces it, which is what a filter screen
 * expects: there is one period, not a growing pile of periods.
 * <p>
 * From it come the two things SQLite needs: the text of the WHERE clause, with
 * question marks where the values go, and the values themselves in the same
 * order. A filter can also be written down - into an intent, into the settings,
 * into a line of JSON - and read back, so it survives leaving the screen.
 */
public class WhereFilter {

    private static final String TAG = "WhereFilter";

    /** Reads and writes the whole filter as one line of JSON. */
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Criterion.class, new CriterionAdapter())
            .registerTypeAdapter(DateTimeCriterion.class, new DateTimeCriterionAdapter())
            .create();

    public static final String TITLE_EXTRA = "title";
    public static final String FILTER_EXTRA = "filter";
    /** The sort order travels under the name of the column that holds it. */
    public static final String SORT_ORDER_EXTRA = io.github.mpstudios56.cifra.orb.EntityManager.DEF_SORT_COL;

    public static final String FILTER_TITLE_PREF = "filterTitle";
    public static final String FILTER_LENGTH_PREF = "filterLength";
    public static final String FILTER_CRITERIA_PREF = "filterCriteria";
    public static final String FILTER_SORT_ORDER_PREF = "filterSortOrder";

    /** Says that the totals screen must not drop the excluded movements. */
    public static final String TAG_AS_IS = "__tag_as_is";

    private final String title;
    private final List<Criterion> criteria = new ArrayList<>();
    private final List<String> sorts = new ArrayList<>();

    public WhereFilter(String title) {
        this.title = title;
    }

    public static WhereFilter empty() {
        return new WhereFilter("");
    }

    /** A filter that can be changed without changing the one it came from. */
    public static WhereFilter copyOf(WhereFilter other) {
        synchronized (other) {
            WhereFilter copy = new WhereFilter(other.title);
            copy.criteria.addAll(other.criteria);
            copy.sorts.addAll(other.sorts);
            return copy;
        }
    }

    // ------------------------------------------------- adding to the question

    public synchronized WhereFilter eq(Criterion c) {
        criteria.add(c);
        return this;
    }

    public synchronized WhereFilter eq(String column, String value) {
        return eq(Criterion.eq(column, value));
    }

    public synchronized WhereFilter neq(String column, String value) {
        return eq(Criterion.neq(column, value));
    }

    public synchronized WhereFilter btw(String column, String from, String to) {
        return eq(Criterion.btw(column, from, to));
    }

    public synchronized WhereFilter gt(String column, String value) {
        return eq(Criterion.gt(column, value));
    }

    public synchronized WhereFilter gte(String column, String value) {
        return eq(Criterion.gte(column, value));
    }

    public synchronized WhereFilter lt(String column, String value) {
        return eq(Criterion.lt(column, value));
    }

    public synchronized WhereFilter lte(String column, String value) {
        return eq(Criterion.lte(column, value));
    }

    public synchronized WhereFilter isNull(String column) {
        return eq(Criterion.isNull(column));
    }

    /** Anywhere inside the column's text, wherever it falls. */
    public synchronized WhereFilter contains(String column, String text) {
        return eq(Criterion.like(column, "%" + text + "%"));
    }

    public synchronized WhereFilter asc(String column) {
        sorts.add(column + " asc");
        return this;
    }

    public synchronized WhereFilter desc(String column) {
        sorts.add(column + " desc");
        return this;
    }

    // ------------------------------------------------ reading and changing it

    /** The condition on that column, or nothing when the column is not asked about. */
    public synchronized Criterion get(String column) {
        for (Criterion c : criteria) {
            if (column.equals(c.columnName)) {
                return c;
            }
        }
        return null;
    }

    /**
     * Sets the condition for its column, replacing whatever was asked about
     * that column before. Gives back what it replaced, or nothing.
     */
    public synchronized Criterion put(Criterion criterion) {
        for (int i = 0; i < criteria.size(); i++) {
            Criterion existing = criteria.get(i);
            if (criterion.columnName.equals(existing.columnName)) {
                criteria.set(i, criterion);
                return existing;
            }
        }
        criteria.add(criterion);
        return null;
    }

    public synchronized Criterion remove(String column) {
        for (Iterator<Criterion> i = criteria.iterator(); i.hasNext(); ) {
            Criterion c = i.next();
            if (column.equals(c.columnName)) {
                i.remove();
                return c;
            }
        }
        return null;
    }

    public synchronized void clear() {
        criteria.clear();
        sorts.clear();
    }

    public synchronized boolean isEmpty() {
        return criteria.isEmpty();
    }

    public String getTitle() {
        return title;
    }

    // ---------------------------------------------------------- as a query

    public synchronized String getSelection() {
        StringBuilder where = new StringBuilder();
        for (Criterion c : criteria) {
            if (where.length() > 0) {
                where.append(" AND ");
            }
            where.append(c.getSelection());
        }
        String selection = where.toString().trim();
        Log.d(TAG, "getSelection=" + selection);
        return selection;
    }

    public synchronized String[] getSelectionArgs() {
        List<String> args = new ArrayList<>();
        for (Criterion c : criteria) {
            args.addAll(Arrays.asList(c.getSelectionArgs()));
        }
        return args.toArray(new String[0]);
    }

    public synchronized String getSortOrder() {
        return String.join(",", sorts);
    }

    public synchronized void resetSort() {
        sorts.clear();
    }

    // ------------------------------------------- the questions asked most often

    public DateTimeCriterion getDateTime() {
        return (DateTimeCriterion) get(BlotterFilter.DATETIME);
    }

    public void clearDateTime() {
        remove(BlotterFilter.DATETIME);
    }

    public long getAccountId() {
        Criterion c = get(BlotterFilter.FROM_ACCOUNT_ID);
        return c != null ? c.getLongValue1() : -1;
    }

    public long getBudgetId() {
        Criterion c = get(BlotterFilter.BUDGET_ID);
        return c != null ? c.getLongValue1() : -1;
    }

    public int getIsTemplate() {
        Criterion c = get(BlotterFilter.IS_TEMPLATE);
        return c != null ? c.getIntValue() : 0;
    }

    public boolean isTemplate() {
        return getIsTemplate() == 1;
    }

    public boolean isSchedule() {
        return getIsTemplate() == 2;
    }

    /**
     * Works out again where a named period falls.
     * <p>
     * "This month" is a different fortnight in March than it was in February,
     * so a filter that has been sitting in the settings since then has to be
     * asked the question afresh. A period picked by hand is left alone.
     */
    public void recalculatePeriod() {
        DateTimeCriterion c = getDateTime();
        if (c == null) {
            return;
        }
        PeriodType type = c.getPeriod().type;
        if (type != PeriodType.CUSTOM) {
            put(new DateTimeCriterion(type));
        }
    }

    /** The dates chosen on the period screen, which come back as three plain extras. */
    public static DateTimeCriterion dateTimeFromIntent(Intent data) {
        PeriodType type = PeriodType.valueOf(
                data.getStringExtra(DateFilterActivity.EXTRA_FILTER_PERIOD_TYPE));
        if (type != PeriodType.CUSTOM) {
            return new DateTimeCriterion(type);
        }
        return new DateTimeCriterion(
                data.getLongExtra(DateFilterActivity.EXTRA_FILTER_PERIOD_FROM, 0),
                data.getLongExtra(DateFilterActivity.EXTRA_FILTER_PERIOD_TO, 0));
    }

    // ------------------------------------------------------------ as writing

    public synchronized void toBundle(Bundle bundle) {
        String[] written = new String[criteria.size()];
        for (int i = 0; i < written.length; i++) {
            written[i] = criteria.get(i).toStringExtra();
        }
        bundle.putString(TITLE_EXTRA, title);
        bundle.putStringArray(FILTER_EXTRA, written);
        bundle.putString(SORT_ORDER_EXTRA, getSortOrder());
    }

    public static WhereFilter fromBundle(Bundle bundle) {
        WhereFilter filter = new WhereFilter(bundle.getString(TITLE_EXTRA));
        try {
            synchronized (filter) {
                String[] written = bundle.getStringArray(FILTER_EXTRA);
                if (written != null) {
                    for (String one : written) {
                        filter.put(Criterion.fromStringExtra(one));
                    }
                }
                filter.readSortOrder(bundle.getString(SORT_ORDER_EXTRA));
            }
        } catch (Exception unreadable) {
            // A filter that cannot be read is no reason to show nothing: the
            // list opens without one instead of refusing to open.
            Log.e(TAG, "fromBundle", unreadable);
            return empty();
        }
        return filter;
    }

    public void toIntent(Intent intent) {
        Bundle bundle = intent.getExtras();
        if (bundle == null) {
            bundle = new Bundle();
        }
        toBundle(bundle);
        intent.replaceExtras(bundle);
    }

    public static WhereFilter fromIntent(Intent intent) {
        Bundle bundle = intent.getExtras();
        return fromBundle(bundle == null ? new Bundle() : bundle);
    }

    public synchronized void toSharedPreferences(SharedPreferences preferences) {
        SharedPreferences.Editor e = preferences.edit();
        e.putString(FILTER_TITLE_PREF, title);
        e.putInt(FILTER_LENGTH_PREF, criteria.size());
        for (int i = 0; i < criteria.size(); i++) {
            e.putString(FILTER_CRITERIA_PREF + i, criteria.get(i).toStringExtra());
        }
        e.putString(FILTER_SORT_ORDER_PREF, getSortOrder());
        e.apply();
    }

    public static WhereFilter fromSharedPreferences(SharedPreferences preferences) {
        WhereFilter filter = new WhereFilter(preferences.getString(FILTER_TITLE_PREF, ""));
        try {
            synchronized (filter) {
                int count = preferences.getInt(FILTER_LENGTH_PREF, 0);
                for (int i = 0; i < count; i++) {
                    String written = preferences.getString(FILTER_CRITERIA_PREF + i, "");
                    if (!written.isEmpty()) {
                        filter.put(Criterion.fromStringExtra(written));
                    }
                }
                filter.readSortOrder(preferences.getString(FILTER_SORT_ORDER_PREF, ""));
            }
        } catch (Exception unreadable) {
            Log.e(TAG, "fromSharedPreferences", unreadable);
            return empty();
        }
        return filter;
    }

    private void readSortOrder(String written) {
        if (written == null || written.isEmpty()) {
            return;
        }
        sorts.addAll(Arrays.asList(written.split(",")));
    }

    public synchronized String toJsonString() {
        return GSON.toJsonTree(this).toString();
    }

    public static WhereFilter fromJsonString(String json) {
        return GSON.fromJson(JsonParser.parseString(json), WhereFilter.class);
    }

    /**
     * The ways a condition can ask its question, each carrying the SQL it turns
     * into. The question marks are filled by the values, in order.
     */
    public enum Operation {
        RAW(""),
        EQ("=?"),
        NEQ("!=?"),
        GT(">?"),
        GTE(">=?"),
        LT("<?"),
        LTE("<=?"),
        /** Two values at a time; several pairs are joined with OR. */
        BTW("BETWEEN ? AND ?", "OR", 2),
        /** As many question marks as there are values. */
        IN("IN (?)") {
            @Override
            public String getOp(int howManyValues) {
                StringBuilder marks = new StringBuilder("?");
                for (int i = 1; i < howManyValues; i++) {
                    marks.append(",?");
                }
                return super.getOp(howManyValues).replace("?", marks.toString());
            }
        },
        ISNULL("is NULL"),
        LIKE("LIKE ?"),
        OR("OR"),
        AND("AND"),
        /** Asks nothing; carries a mark for the app to read back. */
        TAG("");

        private final String sql;
        private final String groupOp;
        private final int valsPerGroup;

        Operation(String sql) {
            this(sql, null, 1);
        }

        Operation(String sql, String groupOp, int valsPerGroup) {
            this.sql = sql;
            this.groupOp = groupOp;
            this.valsPerGroup = valsPerGroup;
        }

        public String getOp(int howManyValues) {
            return sql;
        }

        public String getGroupOp() {
            return groupOp;
        }

        public int getValsPerGroup() {
            return valsPerGroup;
        }
    }

    /** How much of a split movement a list is meant to show. */
    public static class Splits {
        public static final int DEFAULT = 0;
        public static final int ALL = 1;
        public static final int SUMMARY_ONLY = 2;
        public static final int CHILDREN_ONLY = 3;
    }
}
