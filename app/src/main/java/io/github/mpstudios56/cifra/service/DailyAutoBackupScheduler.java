/*
 * Copyright (c) 2011 Denis Solonenko.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */

package io.github.mpstudios56.cifra.service;

import android.content.Context;
import android.util.Log;

import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import io.github.mpstudios56.cifra.db.DatabaseAdapter;
import io.github.mpstudios56.cifra.utils.MyPreferences;
import io.github.mpstudios56.cifra.worker.AutoBackupWorker;

/**
 * Created by IntelliJ IDEA.
 * User: Denis Solonenko
 * Date: 12/16/11 12:54 AM
 */
public class DailyAutoBackupScheduler {

    private final int hh;
    private final int mm;
    private final long now;

    private String TAG;

    public static void scheduleNextAutoBackup(Context context) {
        scheduleNextAutoBackupAfterTimestamp(context, System.currentTimeMillis());
    }

    public static void scheduleNextAutoBackupAfterTimestamp(Context context, long timestamp) {
        if (MyPreferences.isAutoBackupEnabled()) {
            int hhmm = MyPreferences.getAutoBackupTime();
            int hh = hhmm/100;
            int mm = hhmm - 100*hh;
            new DailyAutoBackupScheduler(hh, mm, timestamp).scheduleBackup(context);
        }
        else {
            WorkManager.getInstance(context)
                    .cancelUniqueWork(AutoBackupWorker.WORK_NAME);
        }
    }

    DailyAutoBackupScheduler(int hh, int mm, long now) {
        this.hh = hh;
        this.mm = mm;
        this.now = now;

        TAG = getClass().getSimpleName();
    }

    private void scheduleBackup(Context context) {
        StringBuilder log = new StringBuilder();
        Date scheduledTime = getScheduledTime();

        long initialDelay = scheduledTime.getTime() - System.currentTimeMillis();

        Log.i(TAG, "Initial delay: " + initialDelay + " ms");

        // Every day, or every two, or every seven: whatever the settings say.
        // The hour stays the hour that was chosen; only how often changes.
        int everyDays = MyPreferences.getAutoBackupEveryDays();
        var builder = new PeriodicWorkRequest.Builder(AutoBackupWorker.class,
                24L * everyDays, TimeUnit.HOURS, 1, TimeUnit.HOURS);

        builder.setNextScheduleTimeOverride(scheduledTime.getTime());

        if (MyPreferences.isDropboxUploadAutoBackups()
                || MyPreferences.isGoogleDriveUploadAutoBackups())
        {
            builder.setConstraints(new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED).build());
        }

        builder.setInputData(new Data.Builder()
                .putLong(AutoBackupWorker.SCHEDULE_TIME, scheduledTime.getTime())
                .build());

        PeriodicWorkRequest backupWorkRequest = builder.build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                AutoBackupWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                backupWorkRequest);

        Log.i(TAG, "Next auto-backup scheduled at " + scheduledTime);
    }

    Date getScheduledTime() {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        c.set(Calendar.HOUR_OF_DAY, hh);
        c.set(Calendar.MINUTE, mm);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() < (now + (2 * 3600 * 1000))) {
            c.add(Calendar.DAY_OF_MONTH, 1);
        }
        return c.getTime();
    }

}
