/*
 * This program is made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */
package io.github.mpstudios56.cifra.utils;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.widget.Toast;

import io.github.mpstudios56.cifra.R;

/**
 * Opens a message to the maintainer, already addressed and with the version and
 * phone filled in.
 * <p>
 * Nothing is sent from the app itself: the mail app opens with the text ready
 * and the person decides whether to send it, and what to leave in. An app that
 * posted a report on its own would be sending someone else's words without
 * their say-so.
 */
public class Feedback {

    private Feedback() {
    }

    private static String versionOf(Activity activity) {
        try {
            return Utils.getPackageInfo(activity).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    public static void send(Activity activity) {
        String version = versionOf(activity);
        String subject = activity.getString(R.string.feedback_subject, version);
        // The build details save a round of "which phone, which version?" and are
        // visible in the draft, so nothing goes out that was not read first.
        String body = activity.getString(R.string.feedback_body, version,
                Build.MANUFACTURER + " " + Build.MODEL,
                "Android " + Build.VERSION.RELEASE);

        Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"));
        intent.putExtra(Intent.EXTRA_EMAIL,
                new String[]{activity.getString(R.string.feedback_address)});
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        intent.putExtra(Intent.EXTRA_TEXT, body);
        try {
            activity.startActivity(Intent.createChooser(intent,
                    activity.getString(R.string.feedback)));
        } catch (Exception e) {
            Toast.makeText(activity, R.string.feedback_no_mail, Toast.LENGTH_LONG).show();
        }
    }
}
