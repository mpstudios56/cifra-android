package tw.tib.financisto.sync;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import tw.tib.financisto.utils.MyPreferences;

/**
 * The people this phone shares with, and the code it shares with each.
 * <p>
 * One code per pair, written by hand on both phones: I put in Antonio with
 * xfg456, he puts in me with the same, and that pair exists. I put in Debora
 * with ddf567 and she does the same, and that is a different pair - the
 * accounts I hold with her are not the ones I hold with him, and neither knows
 * about the other.
 * <p>
 * Typed in rather than discovered, because a code that has to match on both
 * sides is only brought into being by somebody writing it. And it is the code,
 * not a name, that pairs two phones: names get changed and two people can
 * choose the same one.
 */
public class People {

    private static final String TAG = "People";
    private static final String TABLE = "person";

    public static class Person {
        /** The code shared with this person, and what their file is found by. */
        public final String mark;
        public final String name;
        public final long seenOn;
        /** Their colour, so their entries are told apart at a glance. */
        public final int colour;

        public Person(String mark, String name, long seenOn, int colour) {
            this.mark = mark;
            this.name = name;
            this.seenOn = seenOn;
            this.colour = colour;
        }

        /** What to show: the name if they gave one, otherwise their mark. */
        public String label() {
            return name == null || name.isEmpty() ? mark : name;
        }
    }

    private People() {
    }

    /** Everybody written down here, in the order they were added. */
    public static List<Person> all(SQLiteDatabase db) {
        List<Person> people = new ArrayList<>();
        try (Cursor c = db.query(TABLE, new String[]{"mark", "name", "seen_on", "colour"},
                null, null, null, null, "seen_on asc")) {
            while (c.moveToNext()) {
                people.add(new Person(c.getString(0), c.getString(1), c.getLong(2), c.getInt(3)));
            }
        } catch (Exception e) {
            Log.e(TAG, "could not read the people", e);
        }
        return people;
    }

    /**
     * Writes down a person and the code shared with them.
     *
     * @param mark the code, which must be the same on their phone
     */
    public static void seen(SQLiteDatabase db, String mark, String name) {
        seen(db, mark, name, 0);
    }

    public static void seen(SQLiteDatabase db, String mark, String name, int colour) {
        if (mark == null || mark.trim().isEmpty()) {
            return;
        }
        mark = mark.trim();
        try {
            ContentValues v = new ContentValues();
            v.put("mark", mark);
            v.put("name", name == null ? "" : name);
            v.put("seen_on", System.currentTimeMillis());
            v.put("colour", colour);
            db.insertWithOnConflict(TABLE, null, v, SQLiteDatabase.CONFLICT_REPLACE);
        } catch (Exception e) {
            Log.e(TAG, "could not remember " + mark, e);
        }
    }

    /** Forgets a person: their file is left alone from then on. */
    public static void forget(SQLiteDatabase db, String mark) {
        try {
            db.delete(TABLE, "mark=?", new String[]{mark});
            db.delete("shared_with", "mark=?", new String[]{mark});
        } catch (Exception e) {
            Log.e(TAG, "could not forget " + mark, e);
        }
    }

    /** How many accounts are held with this person alone. */
    public static int accountsWith(SQLiteDatabase db, String mark) {
        try (Cursor c = db.rawQuery("select count(*) from shared_with where mark=?",
                new String[]{mark})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /** How this phone is addressed by the others. */
    public static String myMark() {
        return markOf(MyPreferences.getSyncDeviceId());
    }

    /** The eight characters that stand for a phone. */
    public static String markOf(String deviceId) {
        String mark = deviceId == null ? "" : deviceId.replace("-", "");
        return mark.length() > 8 ? mark.substring(0, 8) : mark;
    }

    /**
     * Pulls the name and the mark out of a file name.
     *
     * @return {name, mark}, or null when the name is not one of ours
     */
    public static String[] fromFileName(String fileName) {
        if (fileName == null || !fileName.startsWith("cifra-") || !fileName.endsWith(".txt")) {
            return null;
        }
        String middle = fileName.substring("cifra-".length(), fileName.length() - ".txt".length());
        int cut = middle.lastIndexOf('-');
        if (cut < 0) {
            // Only a mark: somebody who has not given themselves a name yet.
            return new String[]{"", middle};
        }
        String mark = middle.substring(cut + 1);
        String name = middle.substring(0, cut);
        // The group code sits between the name and the mark, and is not part of
        // what anybody is called.
        String group = MyPreferences.getSyncGroupCode().trim();
        if (!group.isEmpty() && name.length() > group.length()
                && name.regionMatches(true, name.length() - group.length(), group, 0, group.length())
                && name.charAt(name.length() - group.length() - 1) == '-') {
            name = name.substring(0, name.length() - group.length() - 1);
        }
        return new String[]{name, mark};
    }
}
