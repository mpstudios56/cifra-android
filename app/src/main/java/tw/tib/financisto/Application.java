package tw.tib.financisto;

import android.app.Activity;
import android.os.Bundle;
import android.os.StrictMode;

import androidx.multidex.MultiDexApplication;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;

import tw.tib.financisto.activity.PinActivity;
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
            @Override public void onActivityResumed(Activity activity) {
                if (activity instanceof PinActivity) return;
                PrivacyButton.attachTo(activity);
            }
            @Override public void onActivityCreated(Activity a, Bundle b) {}
            @Override public void onActivityStarted(Activity a) {}
            @Override public void onActivityPaused(Activity a) {}
            @Override public void onActivityStopped(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            @Override public void onActivityDestroyed(Activity a) {}
        });
    }
}
