package tw.tib.financisto.sync;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import tw.tib.financisto.utils.MyPreferences;

/**
 * The people this phone shares with.
 * <p>
 * Nobody types them in. Each phone writes a file called after its owner -
 * cifra-Marcello-CASA24-531439ae.txt - so the folder already says who is in the
 * group and what to call them. Reading a round is enough to learn a new person,
 * and they are kept here so the list is there when the folder is not.
 * <p>
 * A person is addressed by the eight characters at the end of their file name.
 * The name is only for reading: two people can call themselves the same thing
 * and change it whenever they like.
 */
public class People {

    private static final String TAG = "People";
    private static final String TABLE = "person";

    public static class Person {
        public final String mark;
        public final String name;
        public final long seenOn;

        Person(String mark, String name, long seenOn) {
            this.mark = mark;
            this.name = name;
            this.seenOn = seenOn;
        }

        /** What to show: the name if they gave one, otherwise their mark. */
        public String label() {
            return name == null || name.isEmpty() ? mark : name;
        }
    }

    private People() {
    }

    /** Everybody seen in the folder, most recently seen first. This phone is not in it. */
    public static List<Person> all(SQLiteDatabase db) {
        List<Person> people = new ArrayList<>();
        try (Cursor c = db.query(TABLE, new String[]{"mark", "name", "seen_on"},
                null, null, null, null, "seen_on desc")) {
            while (c.moveToNext()) {
                people.add(new Person(c.getString(0), c.getString(1), c.getLong(2)));
            }
        } catch (Exception e) {
            Log.e(TAG, "could not read the people", e);
        }
        return people;
    }

    /**
     * Remembers somebody seen in the folder.
     *
     * @param mark the eight characters that address them
     */
    public static void seen(SQLiteDatabase db, String mark, String name) {
        if (mark == null || mark.isEmpty() || mark.equals(myMark())) {
            return;
        }
        try {
            ContentValues v = new ContentValues();
            v.put("mark", mark);
            v.put("name", name == null ? "" : name);
            v.put("seen_on", System.currentTimeMillis());
            db.insertWithOnConflict(TABLE, null, v, SQLiteDatabase.CONFLICT_REPLACE);
        } catch (Exception e) {
            Log.e(TAG, "could not remember " + mark, e);
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
