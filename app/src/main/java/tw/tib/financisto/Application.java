package tw.tib.financisto;

import android.app.Activity;
import android.os.Bundle;
import android.os.StrictMode;

import androidx.multidex.MultiDexApplication;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;

import tw.tib.financisto.activity.PinActivity;
import tw.tib.financisto.utils.CrashCatcher;
import tw.tib.financisto.activity.PrivacyButton;

public class Application extends MultiDexApplication {
    private static Application instance;
    private static ExecutorService executor;
    // transaction ID -> copied timestamp millis
    private static Long2LongOpenHashMap copiedUneditedTransactions;

    public static Application getInstance() {
        return instance;
    }

    public static ExecutorService getExecutor() {
        return executor;
    }

    public static Long2LongOpenHashMap getCopiedUneditedTransactions() {
        return copiedUneditedTransactions;
    }


    @Override
    public void onCreate()
    {
        super.onCreate();
        instance = this;
        executor = Executors.newCachedThreadPool();
        copiedUneditedTransactions = new Long2LongOpenHashMap();
        CrashCatcher.install(this);
        putPrivacyButtonOnEveryScreen();

        if (BuildConfig.DEBUG) {
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
            //        .detectLeakedSqlLiteObjects()
            //        .detectLeakedClosableObjects()
                    .detectAll()
                    .penaltyLog()
                    .build());
        }
    }

    /**
     * The switch that puts the figures out has to be within reach wherever one
     * happens to be when somebody leans over - so it is added here, once, for
     * every screen, rather than remembered layout by layout.
     * <p>
     * Not on the lock screen: there is nothing to hide there yet, and a button
     * floating over a keypad is only in the way.
     */
    private void putPrivacyButtonOnEveryScreen() {
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            /** How many screens of this app are in front. */
            private int inFront = 0;

            @Override public void onActivityResumed(Activity activity) {
                if (activity instanceof PinActivity) return;
                // Only where there are figures to hide. It used to follow every
                // screen in the app, so it sat on top of the settings, of the
                // currency list, of every dialog - which reads as something
                // broken rather than as something useful.
                if (activity instanceof tw.tib.financisto.activity.MainActivity
                        || activity instanceof tw.tib.financisto.activity.BlotterActivity
                        || activity instanceof tw.tib.financisto.activity.ReportActivity
                        || activity instanceof tw.tib.financisto.activity.ReportPieChartActivity
                        || activity instanceof tw.tib.financisto.activity.Report2DChartActivity) {
                    PrivacyButton.attachTo(activity);
                }
                // Also inside a single account: it is the same list of
                // movements, and today is today there too.
                if (activity instanceof tw.tib.financisto.activity.MainActivity
                        || activity instanceof tw.tib.financisto.activity.BlotterActivity) {
                    tw.tib.financisto.activity.TodayButton.attachTo(activity);
                }
                if (activity instanceof tw.tib.financisto.activity.BlotterActivity) {
                    // A single account: its list is movements in time, so all of
                    // them belong here. The main screen decides tab by tab.
                    tw.tib.financisto.activity.TodayButton.showFor(activity, true);
                    if (activity.getIntent() != null && activity.getIntent()
                            .getBooleanExtra(tw.tib.financisto.report.Report.FROM_REPORT, false)) {
                        tw.tib.financisto.activity.TodayButton.showFold(activity, false);
                    }
                }
                // Rounds used to be started and stopped by the main screen alone,
                // so walking into an account - which is where somebody spends
                // most of their time - stopped the exchange until they came out
                // again. Any screen keeps it going; the last one to leave the
                // front stops it.
                inFront++;
                tw.tib.financisto.sync.AutoSync.whenReturning(activity);
                tw.tib.financisto.sync.AutoSync.keepGoing(activity);
            }
            @Override public void onActivityCreated(Activity a, Bundle b) {
                // Every screen inside the bars, not behind them. Screens made
                // over the years handle this each in their own way and some not
                // at all, which is how the first movement of an account ended
                // up underneath the navigation buttons - present, paid for,
                // invisible. Said once here for all of them: the screens that
                // do their own inset work simply receive nothing to add.
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(a.getWindow(), true);
            }
            @Override public void onActivityStarted(Activity a) {}
            @Override public void onActivityPaused(Activity a) {
                if (a instanceof PinActivity) return;
                // One screen handing over to another passes through here before
                // the next one resumes, so the count decides: nothing stops while
                // any part of the app is still in front.
                inFront--;
                if (inFront <= 0) {
                    inFront = 0;
                    tw.tib.financisto.sync.AutoSync.stop();
                }
            }
            @Override public void onActivityStopped(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            @Override public void onActivityDestroyed(Activity a) {}
        });
    }
}
