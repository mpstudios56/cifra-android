/*
 * This program is made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */
package tw.tib.financisto.utils;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/**
 * The same page in the reader's language, when there is one.
 * <p>
 * Pages kept as assets - what is new, the licence, about - do not go through
 * the usual translation of strings, so they stayed in English while every menu
 * around them had been translated into nineteen languages. Somebody reading the
 * app in Italian and finding its own diary in English concludes, reasonably,
 * that half of it was never finished.
 * <p>
 * A file per language, named "whatsnew-it.htm" beside "whatsnew.htm", and
 * English when there is no such file. Adding a language is adding a file.
 */
public class LocalisedAsset {

    private LocalisedAsset() {
    }

    /**
     * @param name a file in the assets folder, "whatsnew.htm"
     * @return the address to load, in the reader's language where possible
     */
    public static String url(Context context, String name) {
        return "file:///android_asset/" + inLanguage(context, name);
    }

    /**
     * The page itself, in the reader's language, with the version written into
     * it wherever it says {version}.
     * <p>
     * A page kept as a file cannot know what version it is being shown by, and
     * the one place somebody looks for a version number is the about screen.
     */
    public static String read(Context context, String name) {
        StringBuilder text = new StringBuilder();
        try (InputStream in = context.getAssets().open(inLanguage(context, name))) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                text.append(new String(buffer, 0, read, "UTF-8"));
            }
        } catch (Exception e) {
            return "";
        }
        return text.toString().replace("{version}", version(context));
    }

    private static String version(Context context) {
        try {
            return context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }

    private static String inLanguage(Context context, String name) {
        String language = language(context);
        if (language.isEmpty()) {
            return name;
        }
        int dot = name.lastIndexOf('.');
        String candidate = dot < 0
                ? name + "-" + language
                : name.substring(0, dot) + "-" + language + name.substring(dot);
        return exists(context, candidate) ? candidate : name;
    }

    /**
     * The language the app is being read in, which is not always the phone's:
     * Cifra lets it be chosen in the settings, and that choice has to win here
     * as it does everywhere else.
     */
    private static String language(Context context) {
        try {
            return context.getResources().getConfiguration().getLocales().get(0)
                    .getLanguage().toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return "";
        }
    }

    private static boolean exists(Context context, String name) {
        AssetManager assets = context.getAssets();
        try (InputStream in = assets.open(name)) {
            return in != null;
        } catch (IOException e) {
            return false;
        }
    }
}
