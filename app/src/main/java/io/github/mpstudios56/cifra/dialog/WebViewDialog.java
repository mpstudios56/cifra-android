package io.github.mpstudios56.cifra.dialog;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.webkit.WebView;

import io.github.mpstudios56.cifra.R;
import io.github.mpstudios56.cifra.utils.LocalisedAsset;
import io.github.mpstudios56.cifra.utils.Utils;

/**
 * What is new in this version, shown once after it has been installed.
 * <p>
 * The version last seen is kept beside the screen that shows it, so the page
 * appears on the first opening after an update and never again - and never at
 * all on a first install, where a list of changes to something one has not used
 * yet says nothing.
 */
public class WebViewDialog {

    private static final String LAST_VERSION_SEEN = "versionCode";

    private WebViewDialog() {
    }

    /**
     * Shows the page if this version has not been seen before.
     *
     * @return the version, ready to be put on screen
     */
    public static String checkVersionAndShowWhatsNewIfNeeded(Activity activity) {
        try {
            PackageInfo installed = Utils.getPackageInfo(activity);
            SharedPreferences seen = activity.getPreferences(0);
            if (installed.versionCode > seen.getInt(LAST_VERSION_SEEN, -1)) {
                // Written down before the page is shown, not after: a page
                // dismissed by turning the phone would otherwise come back at
                // every opening.
                seen.edit().putInt(LAST_VERSION_SEEN, installed.versionCode).apply();
                showWhatsNew(activity);
            }
            return "v. " + installed.versionName;
        } catch (Exception noPackage) {
            return "";
        }
    }

    public static void showWhatsNew(Context context) {
        show(context, "whatsnew.htm", R.string.whats_new);
    }

    private static void show(Context context, String page, int title) {
        WebView view = new WebView(context);
        // Painted dark before anything is loaded: a web view starts white, and
        // the flash of a white sheet was visible for as long as the page took.
        view.setBackgroundColor(Color.parseColor("#141414"));
        view.loadDataWithBaseURL("file:///android_asset/",
                LocalisedAsset.readStyled(context, page), "text/html", "UTF-8", null);
        new AlertDialog.Builder(context)
                .setView(view)
                .setTitle(title)
                .setPositiveButton(R.string.ok, null)
                .show();
    }
}
