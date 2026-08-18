package tw.tib.financisto.activity;

import android.app.Activity;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;

import tw.tib.financisto.R;
import tw.tib.financisto.utils.Privacy;

/**
 * The switch that puts the figures out, floating over whatever is on screen.
 * <p>
 * It is not in a menu and not in one screen's toolbar, because the moment it is
 * wanted is the moment somebody has just leaned over: whatever is open has to
 * go dark in one tap, without hunting. So it rides above every screen in the
 * app, added on top of the content view.
 * <p>
 * At rest it fades and slides half off the right edge - present, but not a
 * green dot following the eye around all day. Any touch on it wakes it and
 * switches at the same time; nobody should have to tap twice.
 */
public class PrivacyButton {

    private static final int SIZE_DP = 44;
    private static final int EDGE_DP = 10;
    private static final int ABOVE_BOTTOM_DP = 150;
    /** How long a wakeful button stays bright before retiring to the edge. */
    private static final long REST_AFTER_MS = 2500;
    private static final float RESTING_ALPHA = 0.30f;
    /** How much of it hides past the edge once it settles. */
    private static final float RESTING_TUCK = 0.52f;

    private static final int TAG_ID = R.id.privacy_button;

    public static void attachTo(Activity activity) {
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null || content.getChildCount() == 0) return;
        View already = content.findViewById(TAG_ID);
        if (already != null) {
            // Coming back to the app: show it for a moment. Whoever put the
            // figures out on the bus wants to see, on picking the phone up
            // again, that they are still out and where the switch is.
            // The whole column, not this button alone: waking the eye by
            // itself is what left the three of them out of step, one tucked
            // against the edge while the others were still out.
            TodayButton.wakeAll(already);
            return;
        }

        ImageButton button = new ImageButton(activity);
        button.setId(TAG_ID);
        button.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        button.setContentDescription(activity.getString(R.string.privacy_toggle));
        int pad = dp(activity, 10);
        button.setPadding(pad, pad, pad, pad);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                dp(activity, SIZE_DP), dp(activity, SIZE_DP));
        lp.gravity = Gravity.END | Gravity.BOTTOM;
        lp.rightMargin = dp(activity, EDGE_DP);
        lp.bottomMargin = dp(activity, ABOVE_BOTTOM_DP);
        button.setLayoutParams(lp);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            button.setElevation(dp(activity, 6));
        }

        dress(button);
        button.setOnClickListener(v -> {
            Privacy.toggle();
            // Everything on screen was drawn from the old answer - lists, totals,
            // the chart under them. Building it again is the one way to be sure
            // nothing is left showing.
            activity.recreate();
        });
        button.setOnLongClickListener(v -> {
            TodayButton.wakeAll(button);
            return true;
        });

        content.addView(button);
        TodayButton.wakeAll(button);
    }

    /** Colour and symbol for the state the app is currently in. */
    private static void dress(ImageButton button) {
        boolean hidden = Privacy.isHidden();
        button.setBackgroundResource(hidden
                ? R.drawable.privacy_button_active : R.drawable.privacy_button_idle);
        button.setImageResource(hidden
                ? R.drawable.ic_privacy_hide : R.drawable.ic_privacy_show);
    }

    private static void wake(View button) {
        button.animate().cancel();
        button.setAlpha(1f);
        button.setTranslationX(0f);
        button.postDelayed(() -> rest(button), REST_AFTER_MS);
    }

    private static void rest(View button) {
        if (button.getParent() == null) return;
        button.animate()
                .alpha(RESTING_ALPHA)
                .translationX(button.getWidth() * RESTING_TUCK)
                .setDuration(400)
                .start();
    }

    private static int dp(Activity activity, int value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                activity.getResources().getDisplayMetrics()));
    }
}
