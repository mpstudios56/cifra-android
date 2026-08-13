package tw.tib.financisto.utils;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import tw.tib.financisto.BuildConfig;

/**
 * Keeps the reason the app closed, so somebody can be asked to send it.
 * <p>
 * A closed test lives on people saying what went wrong, and what they can say
 * is "si è chiuso" - which is not enough to fix anything. The store collects
 * crashes only from people who agreed to send them, and a tester who did not
 * is invisible.
 * <p>
 * So the reason is written to a file here, and offered on the next start. It
 * holds the version, the phone and the trace: no accounts, no amounts, no
 * names. And it is only ever sent by somebody pressing the button.
 */
public class CrashCatcher {

    private static final String TAG = "CrashCatcher";
    private static final String FILE = "last-crash.txt";

    private CrashCatcher() {
    }

    public static void install(Context context) {
        Context app = context.getApplicationContext();
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                write(app, thread, error);
            } catch (Throwable ignored) {
                // Whatever happens here, the app is going down anyway; the one
                // thing that must still happen is the handler below.
            }
            if (previous != null) {
                previous.uncaughtException(thread, error);
            }
        });
    }

    private static void write(Context context, Thread thread, Throwable error) throws Exception {
        StringWriter trace = new StringWriter();
        error.printStackTrace(new PrintWriter(trace));

        StringBuilder sb = new StringBuilder();
        sb.append("Cifra ").append(BuildConfig.VERSION_NAME)
                .append(" (").append(BuildConfig.VERSION_CODE).append(")\n");
        sb.append("Android ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n");
        sb.append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.UK).format(new Date()))
                .append("\n");
        sb.append("thread: ").append(thread.getName()).append("\n\n");
        sb.append(trace);

        File file = new File(context.getFilesDir(), FILE);
        try (PrintWriter out = new PrintWriter(file, "UTF-8")) {
            out.print(sb);
        }
        Log.e(TAG, "kept the reason in " + file);
    }

    /** The report waiting to be sent, or null. */
    public static String waiting(Context context) {
        File file = new File(context.getFilesDir(), FILE);
        if (!file.isFile()) {
            return null;
        }
        try (java.io.BufferedReader in = new java.io.BufferedReader(
                new java.io.InputStreamReader(new java.io.FileInputStream(file), "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (Exception e) {
            Log.e(TAG, "could not read the kept reason", e);
            return null;
        }
    }

    /** Asked once. Sent or not, it is not asked again. */
    public static void clear(Context context) {
        File file = new File(context.getFilesDir(), FILE);
        if (file.isFile() && !file.delete()) {
            Log.e(TAG, "could not remove " + file);
        }
    }
}
