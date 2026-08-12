/*
 * This program is made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */
package tw.tib.financisto.sync;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import tw.tib.financisto.db.DatabaseHelper;

/**
 * Puts two labels that mean the same thing back together.
 * <p>
 * After the first exchange each phone has its own "Spesa" and the other's: two
 * rows with one name. The app does not join them by itself, because "Casa" can
 * be the mortgage to one person and the cleaning to the other, and a wrong guess
 * inside somebody's accounts does not come undone. So it proposes, the way a
 * phone book proposes duplicate contacts, and somebody decides.
 * <p>
 * <b>Merging never touches the money.</b> Not one movement is deleted, moved or
 * added up: the ones that pointed at the label going away point at the one
 * staying. What is unified is the legend, not the spending.
 */
public class Merger {

    private static final String TAG = "Merger";

    /** Two rows that might be the same thing. */
    public static class Pair {
        public final String kind;
        public final long leftId;
        public final long rightId;
        public final String leftName;
        public final String rightName;
        public final int leftUses;
        public final int rightUses;
        /** True when the two are written exactly alike once tidied up. */
        public final boolean identical;

        Pair(String kind, long leftId, String leftName, int leftUses,
             long rightId, String rightName, int rightUses, boolean identical) {
            this.kind = kind;
            this.leftId = leftId;
            this.leftName = leftName;
            this.leftUses = leftUses;
            this.rightId = rightId;
            this.rightName = rightName;
            this.rightUses = rightUses;
            this.identical = identical;
        }
    }

    /** The kinds that carry a label a movement can point at. */
    public static final String[] KINDS = {
            DatabaseHelper.CATEGORY_TABLE,
            DatabaseHelper.PAYEE_TABLE,
            DatabaseHelper.PROJECT_TABLE,
            DatabaseHelper.LOCATIONS_TABLE,
    };

    private Merger() {
    }

    private static String column(String kind) {
        if (DatabaseHelper.CATEGORY_TABLE.equals(kind)) return "category_id";
        if (DatabaseHelper.PAYEE_TABLE.equals(kind)) return "payee_id";
        if (DatabaseHelper.PROJECT_TABLE.equals(kind)) return "project_id";
        return "location_id";
    }

    // ------------------------------------------------------------- proposing

    /**
     * Every pair worth asking about, written alike first.
     * <p>
     * Compared without capitals, without accents and without the spaces at the
     * ends: "Supermercato" and "supermercato " are not two different shops.
     */
    public static List<Pair> candidates(SQLiteDatabase db) {
        List<Pair> pairs = new ArrayList<>();
        for (String kind : KINDS) {
            List<long[]> ids = new ArrayList<>();
            List<String> names = new ArrayList<>();
            String nameColumn = SharedThings.nameColumn(kind);
            String where = DatabaseHelper.CATEGORY_TABLE.equals(kind) ? "_id > 0" : null;
            try (Cursor c = db.query(kind, new String[]{"_id", nameColumn},
                    where, null, null, null, "_id asc")) {
                while (c.moveToNext()) {
                    String name = c.getString(1);
                    if (name == null || name.trim().isEmpty()) {
                        continue;
                    }
                    ids.add(new long[]{c.getLong(0)});
                    names.add(name);
                }
            } catch (Exception e) {
                continue;
            }

            for (int i = 0; i < names.size(); i++) {
                for (int j = i + 1; j < names.size(); j++) {
                    String a = tidy(names.get(i));
                    String b = tidy(names.get(j));
                    boolean same = a.equals(b);
                    if (!same && !alike(a, b)) {
                        continue;
                    }
                    pairs.add(new Pair(kind,
                            ids.get(i)[0], names.get(i), uses(db, kind, ids.get(i)[0]),
                            ids.get(j)[0], names.get(j), uses(db, kind, ids.get(j)[0]),
                            same));
                }
            }
        }
        // The written-alike ones first: those are the ones somebody will say yes
        // to without thinking, and the doubtful ones should not be in the way.
        pairs.sort((x, y) -> Boolean.compare(y.identical, x.identical));
        return pairs;
    }

    /** Lower case, no accents, no spaces at the ends. */
    private static String tidy(String name) {
        String s = Normalizer.normalize(name.trim().toLowerCase(Locale.ROOT),
                Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return s.replaceAll("\\s+", " ");
    }

    /**
     * Close enough to be worth asking about: one letter in, out or different.
     * Deliberately mean - proposing everything that vaguely rhymes turns the
     * screen into a list nobody reads.
     */
    private static boolean alike(String a, String b) {
        if (Math.abs(a.length() - b.length()) > 1 || a.length() < 4) {
            return false;
        }
        return distance(a, b) == 1;
    }

    private static int distance(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }

    private static int uses(SQLiteDatabase db, String kind, long id) {
        try (Cursor c = db.rawQuery("select count(*) from transactions where "
                + column(kind) + "=?", new String[]{String.valueOf(id)})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    // --------------------------------------------------------------- merging

    /**
     * Points everything that used {@code loser} at {@code keeper}, then removes
     * {@code loser}. The movements themselves are not touched: they keep their
     * date, their amount and their account, and only the label they point at
     * changes.
     *
     * @return how many movements changed hands
     */
    public static int merge(tw.tib.financisto.db.DatabaseAdapter adapter,
                            String kind, long keeper, long loser) {
        if (keeper == loser || keeper <= 0 || loser <= 0) {
            return 0;
        }
        SQLiteDatabase db = adapter.db();
        boolean isCategory = DatabaseHelper.CATEGORY_TABLE.equals(kind);
        if (isCategory && hasChildren(db, loser)) {
            // A category with subcategories under it cannot simply go: taking it
            // away would take them with it. Empty it out first, or merge the
            // subcategories one by one.
            return -1;
        }

        int moved;
        db.beginTransaction();
        try {
            ContentValues v = new ContentValues();
            v.put(column(kind), keeper);
            moved = db.update(DatabaseHelper.TRANSACTION_TABLE, v,
                    column(kind) + "=?", new String[]{String.valueOf(loser)});

            if (isCategory) {
                // A budget names a category too, and a budget left pointing at a
                // category that has gone counts nothing at all.
                ContentValues b = new ContentValues();
                b.put("category_id", keeper);
                db.update(DatabaseHelper.BUDGET_TABLE, b, "category_id=?",
                        new String[]{String.valueOf(loser)});
                db.delete("category_attribute", "category_id=?",
                        new String[]{String.valueOf(loser)});
            } else {
                db.delete(kind, "_id=?", new String[]{String.valueOf(loser)});
            }
            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, "could not merge " + kind + " " + loser + " into " + keeper, e);
            return 0;
        } finally {
            db.endTransaction();
        }

        if (isCategory) {
            // Through the app's own deletion, which knows how the tree of
            // categories is numbered. A plain delete leaves the numbering wrong
            // and the next category added lands in the wrong place.
            adapter.deleteCategory(loser);
        }
        return moved;
    }

    /** Whether anything sits underneath this category. */
    private static boolean hasChildren(SQLiteDatabase db, long id) {
        try (Cursor c = db.query(DatabaseHelper.CATEGORY_TABLE,
                new String[]{"right - left"}, "_id=?",
                new String[]{String.valueOf(id)}, null, null, null)) {
            return c.moveToFirst() && c.getInt(0) > 1;
        } catch (Exception e) {
            return true;
        }
    }
}
