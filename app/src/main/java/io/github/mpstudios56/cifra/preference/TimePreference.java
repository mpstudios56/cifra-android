package io.github.mpstudios56.cifra.preference;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.DialogPreference;

import io.github.mpstudios56.cifra.R;

public class TimePreference extends DialogPreference {
    private static final int DEFAULT_VALUE = 600;
    private int time;

    public TimePreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public TimePreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public TimePreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public TimePreference(@NonNull Context context) {
        super(context);
    }

    public void setTime(int time) {
        final boolean wasBlocking = shouldDisableDependents();

        this.time = time;

        persistInt(time);

        final boolean isBlocking = shouldDisableDependents();
        if (isBlocking != wasBlocking) {
            notifyDependencyChange(isBlocking);
        }

        showSummary();
        notifyChanged();
    }

    public int getTime() {
        return this.time;
    }

    private void showSummary() {
        setSummary(said(getContext(), time / 100, time % 100));
    }

    /**
     * The hour written out, and never the recipe for writing it.
     * <p>
     * The sentence is translated fifteen times over and one of those copies is
     * enough to make the formatting fail, at which point what appeared on the
     * settings screen was a piece of the pattern itself.
     */
    public static String said(android.content.Context context, int hour, int minute) {
        String clock = String.format(java.util.Locale.getDefault(), "%02d:%02d", hour, minute);
        try {
            return context.getString(R.string.auto_backup_time_summary, hour, minute);
        } catch (Exception e) {
            return clock;
        }
    }

    @Nullable
    @Override
    protected Object onGetDefaultValue(@NonNull TypedArray a, int index) {
        return a.getInt(index, DEFAULT_VALUE);
    }

    @Override
    protected void onSetInitialValue(@Nullable Object defaultValue) {
        if (defaultValue == null) {
            defaultValue = DEFAULT_VALUE;
        }
        setTime(getPersistedInt((Integer) defaultValue));
    }
}
