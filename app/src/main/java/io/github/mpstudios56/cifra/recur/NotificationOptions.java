package io.github.mpstudios56.cifra.recur;

import static androidx.core.app.NotificationCompat.DEFAULT_ALL;
import static androidx.core.app.NotificationCompat.DEFAULT_LIGHTS;
import static androidx.core.app.NotificationCompat.DEFAULT_VIBRATE;

import android.content.Context;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.provider.Settings;

import androidx.core.app.NotificationCompat;

import io.github.mpstudios56.cifra.R;
import io.github.mpstudios56.cifra.utils.LocalizableEnum;
import io.github.mpstudios56.cifra.utils.Utils;

/**
 * How a scheduled movement announces itself: a sound, a buzz, a light.
 * <p>
 * The three answers are kept together because they are asked together, and are
 * written down as one line so a scheduled movement can carry them in a single
 * column. Two of the three combinations have names of their own - everything as
 * the phone does it, and nothing at all - so the card can say which it is
 * without listing three settings.
 */
public class NotificationOptions {

    private static final String DEFAULT_SOUND = Settings.System.DEFAULT_NOTIFICATION_URI.toString();
    private static final String SEPARATOR = ";";

    /** How long the phone buzzes, in pauses and buzzes. */
    public enum VibrationPattern implements LocalizableEnum {
        OFF(R.string.notification_options_off, null),
        DEFAULT(R.string.notification_options_default, null),
        SHORT(R.string.notification_options_short, new long[]{0, 200}),
        SHORT_SHORT(R.string.notification_options_2_short, new long[]{0, 200, 200, 200}),
        THREE_SHORTS(R.string.notification_options_3_short, new long[]{0, 200, 200, 200, 200, 200}),
        LONG(R.string.notification_options_long, new long[]{0, 500}),
        LONG_LONG(R.string.notification_options_2_long, new long[]{0, 500, 300, 500}),
        THREE_LONG(R.string.notification_options_3_long, new long[]{0, 500, 300, 500, 300, 500});

        public final int titleId;
        /** Nothing for the two that are not patterns but decisions. */
        public final long[] pattern;

        VibrationPattern(int titleId, long[] pattern) {
            this.titleId = titleId;
            this.pattern = pattern;
        }

        @Override
        public int getTitleId() {
            return titleId;
        }
    }

    /** The colour of the small light, on the phones that still have one. */
    public enum LedColor implements LocalizableEnum {
        OFF(R.string.notification_options_off, Color.BLACK),
        DEFAULT(R.string.notification_options_default, Color.BLACK),
        GREEN(R.string.notification_options_led_green, Color.GREEN),
        BLUE(R.string.notification_options_led_blue, Color.BLUE),
        YELLOW(R.string.notification_options_led_yellow, Color.YELLOW),
        RED(R.string.notification_options_led_red, Color.RED),
        PINK(R.string.notification_options_led_pink, 0xFFFF00FF);

        public final int titleId;
        public final int color;

        LedColor(int titleId, int color) {
            this.titleId = titleId;
            this.color = color;
        }

        @Override
        public int getTitleId() {
            return titleId;
        }
    }

    public String sound;
    public VibrationPattern vibration;
    public LedColor ledColor;

    private NotificationOptions(String sound, VibrationPattern vibration, LedColor ledColor) {
        this.sound = sound;
        this.vibration = vibration;
        this.ledColor = ledColor;
    }

    /** Everything as the phone does it. */
    public static NotificationOptions createDefault() {
        return new NotificationOptions(DEFAULT_SOUND, VibrationPattern.DEFAULT, LedColor.DEFAULT);
    }

    /** Nothing at all: the movement arrives without announcing itself. */
    public static NotificationOptions createOff() {
        return new NotificationOptions(null, VibrationPattern.OFF, LedColor.OFF);
    }

    public boolean isDefault() {
        return DEFAULT_SOUND.equals(sound)
                && vibration == VibrationPattern.DEFAULT
                && ledColor == LedColor.DEFAULT;
    }

    public boolean isOff() {
        return sound == null
                && vibration == VibrationPattern.OFF
                && ledColor == LedColor.OFF;
    }

    public static NotificationOptions parse(String written) {
        String[] parts = written.split(SEPARATOR);
        return new NotificationOptions(
                Utils.isEmpty(parts[0]) ? null : parts[0],
                VibrationPattern.valueOf(parts[1]),
                LedColor.valueOf(parts[2]));
    }

    public String stateToString() {
        return (sound == null ? "" : sound) + SEPARATOR
                + vibration + SEPARATOR + ledColor + SEPARATOR;
    }

    /** One word for the card: as the phone does it, nothing, or set by hand. */
    public String toInfoString(Context context) {
        if (isDefault()) {
            return context.getString(R.string.notification_options_default);
        }
        if (isOff()) {
            return context.getString(R.string.notification_options_off);
        }
        return context.getString(R.string.notification_options_custom);
    }

    /** The name of the chosen sound, as the phone calls it. */
    public String getSoundName(Context context) {
        if (sound == null) {
            return context.getString(R.string.notification_options_off);
        }
        Uri chosen = Uri.parse(sound);
        if (Settings.System.DEFAULT_NOTIFICATION_URI.equals(chosen)) {
            return context.getString(R.string.notification_options_default);
        }
        Ringtone ringtone = RingtoneManager.getRingtone(context, chosen);
        return ringtone != null
                ? ringtone.getTitle(context)
                : context.getString(R.string.notification_options_off);
    }

    /** Dresses a notification about to be shown with these three answers. */
    public void apply(NotificationCompat.Builder builder) {
        if (isOff()) {
            builder.setSilent(true);
            return;
        }
        if (isDefault()) {
            builder.setDefaults(DEFAULT_ALL);
            return;
        }
        builder.setSound(Uri.parse(sound), AudioManager.STREAM_NOTIFICATION);
        if (vibration == VibrationPattern.DEFAULT) {
            builder.setDefaults(DEFAULT_VIBRATE);
        } else {
            builder.setVibrate(vibration.pattern);
        }
        if (ledColor == LedColor.DEFAULT) {
            builder.setDefaults(DEFAULT_LIGHTS);
        } else {
            builder.setLights(ledColor.color, 200, 200);
        }
    }
}
