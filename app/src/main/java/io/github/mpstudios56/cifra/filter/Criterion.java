package io.github.mpstudios56.cifra.filter;

import android.content.Intent;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * One condition a movement has to satisfy to be shown.
 * <p>
 * "The account is this one", "the date falls between these two", "the note
 * contains this word". A condition knows the column it asks about, the way it
 * asks - equals, between, one of - and the values it asks with; from those it
 * can produce the fragment of SQL that goes into the query, and the arguments
 * that fill its question marks.
 * <p>
 * Two conditions can be joined into one with {@link #and} or {@link #or}, and
 * then the joined condition carries its parts as children and asks them for
 * their own fragments in turn.
 * <p>
 * A condition can also be written down as text - a small JSON array - so that
 * it survives being put in an intent or in the settings, and read back.
 */
public class Criterion {

    /** Reads and writes the text form; the two adapters decide its shape. */
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Criterion.class, new CriterionAdapter())
            .registerTypeAdapter(DateTimeCriterion.class, new DateTimeCriterionAdapter())
            .create();

    // ---------------------------------------------------------------- asking

    public static Criterion eq(String column, String value) {
        return new Criterion(column, WhereFilter.Operation.EQ, value);
    }

    public static Criterion neq(String column, String value) {
        return new Criterion(column, WhereFilter.Operation.NEQ, value);
    }

    /** Between the first value and the second, both included. */
    public static Criterion btw(String column, String... values) {
        if (values.length < 2) {
            throw new IllegalArgumentException("Un intervallo ha bisogno di due estremi");
        }
        return new Criterion(column, WhereFilter.Operation.BTW, values);
    }

    /** Any one of the values given. */
    public static Criterion in(String column, String... values) {
        if (values.length == 0) {
            throw new IllegalArgumentException("Un elenco vuoto non seleziona nulla");
        }
        return new Criterion(column, WhereFilter.Operation.IN, values);
    }

    public static Criterion gt(String column, String value) {
        return new Criterion(column, WhereFilter.Operation.GT, value);
    }

    public static Criterion gte(String column, String value) {
        return new Criterion(column, WhereFilter.Operation.GTE, value);
    }

    public static Criterion lt(String column, String value) {
        return new Criterion(column, WhereFilter.Operation.LT, value);
    }

    public static Criterion lte(String column, String value) {
        return new Criterion(column, WhereFilter.Operation.LTE, value);
    }

    public static Criterion isNull(String column) {
        return new Criterion(column, WhereFilter.Operation.ISNULL);
    }

    public static Criterion like(String column, String text) {
        return new Criterion(column, WhereFilter.Operation.LIKE, text);
    }

    /**
     * A fragment of SQL written out by hand, for the questions the operations
     * above cannot express. It carries no arguments, so whatever is put in it
     * must already be safe: only figures worked out by the app, never anything
     * somebody typed.
     */
    public static Criterion raw(String sql) {
        return new Criterion("(" + sql + ")", WhereFilter.Operation.RAW);
    }

    /**
     * A marker rather than a question: it selects everything, and exists so
     * that something can be attached to the filter and read back later.
     */
    public static Criterion tag(String column, String text) {
        return new Criterion(column, WhereFilter.Operation.TAG, text);
    }

    /** Satisfied when any one of these is. */
    public static Criterion or(Criterion... parts) {
        return joined(WhereFilter.Operation.OR, parts);
    }

    /** Satisfied only when all of these are. */
    public static Criterion and(Criterion... parts) {
        return joined(WhereFilter.Operation.AND, parts);
    }

    private static Criterion joined(WhereFilter.Operation how, Criterion... parts) {
        List<String> values = new ArrayList<>();
        for (Criterion part : parts) {
            values.addAll(Arrays.asList(part.getValues()));
        }
        // The column of the first part stands for the whole: a filter holds one
        // condition per column, and a joined condition has to answer for one.
        return new Criterion(parts[0].columnName, how,
                values.toArray(new String[0]), parts);
    }

    // ----------------------------------------------------------- what it is

    public final String columnName;
    public final WhereFilter.Operation operation;
    private final String[] values;
    private final Criterion[] children;

    public Criterion(String columnName, WhereFilter.Operation operation, String... values) {
        this(columnName, operation, values, new Criterion[0]);
    }

    public Criterion(String columnName, WhereFilter.Operation operation,
                     String[] values, Criterion... children) {
        this.columnName = columnName;
        this.operation = operation;
        this.values = values == null ? new String[0] : values;
        this.children = children == null ? new Criterion[0] : children;
    }

    public boolean isNull() {
        return operation == WhereFilter.Operation.ISNULL;
    }

    public String[] getValues() {
        return values;
    }

    public Criterion[] getChildren() {
        return children;
    }

    public int size() {
        return values.length;
    }

    public String getStringValue() {
        return values.length > 0 ? values[0] : "";
    }

    public int getIntValue() {
        return values.length > 0 ? Integer.parseInt(values[0]) : -1;
    }

    public long getLongValue1() {
        return values.length > 0 ? Long.parseLong(values[0]) : -1;
    }

    public long getLongValue2() {
        return values.length > 1 ? Long.parseLong(values[1]) : -1;
    }

    // -------------------------------------------------------------- as a query

    /**
     * The fragment of SQL this condition contributes, with question marks where
     * the values go.
     */
    public String getSelection() {
        if (operation == WhereFilter.Operation.TAG) {
            // A marker asks nothing: "1" is true of every row.
            return "1";
        }
        if (operation == WhereFilter.Operation.AND || operation == WhereFilter.Operation.OR) {
            String[] parts = new String[children.length];
            for (int i = 0; i < children.length; i++) {
                parts[i] = children[i].getSelection();
            }
            return "(" + String.join(" " + operation.getOp(0) + " ", parts) + ")";
        }

        String fragment = columnName + " " + operation.getOp(getSelectionArgs().length);
        String joinWith = operation.getGroupOp();
        int perGroup = operation.getValsPerGroup();
        if (joinWith == null || perGroup <= 0 || values.length <= perGroup) {
            return fragment;
        }
        // More values than the operation takes at once - a "between" given four
        // dates, say - so the fragment is repeated once per group and the
        // groups are joined.
        int groups = values.length / perGroup;
        StringBuilder repeated = new StringBuilder("(").append(fragment);
        for (int i = 1; i < groups; i++) {
            repeated.append(' ').append(joinWith).append(' ').append(fragment);
        }
        return repeated.append(')').toString();
    }

    /** The values that fill the question marks, in the order they appear. */
    public String[] getSelectionArgs() {
        if (operation == WhereFilter.Operation.TAG) {
            return new String[0];
        }
        if (children.length == 0) {
            return values;
        }
        List<String> args = new ArrayList<>();
        for (Criterion child : children) {
            args.addAll(Arrays.asList(child.getSelectionArgs()));
        }
        return args.toArray(new String[0]);
    }

    // ------------------------------------------------------------- as writing

    public JsonArray toJsonArray() {
        return GSON.toJsonTree(this).getAsJsonArray();
    }

    public String toStringExtra() {
        return toJsonArray().toString();
    }

    public static Criterion fromJsonArray(JsonArray array) {
        return GSON.fromJson(array, Criterion.class);
    }

    public static Criterion fromStringExtra(String extra) {
        return fromJsonArray(JsonParser.parseString(extra).getAsJsonArray());
    }

    /** Hands this one condition to another screen, under a title of its own. */
    public void toIntent(String title, Intent intent) {
        intent.putExtra(WhereFilter.TITLE_EXTRA, title);
        intent.putExtra(WhereFilter.FILTER_EXTRA, new String[]{toStringExtra()});
    }
}
