package tw.tib.financisto.activity;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;

import tw.tib.financisto.R;

/**
 * Back to today, from wherever one has scrolled to.
 * <p>
 * It was a button in the bar at the foot of the movements, turned on by a
 * setting most people never found. It now floats above the eye, on the two
 * screens where "today" means something: the movements jump to it, and the
 * summary comes back to the period that holds it.
 */
public class TodayButton {

    private static final int SIZE_DP = 44;
    private static final int EDGE_DP = 10;
    /** Just above the eye, which sits at 150. */
    private static final int ABOVE_BOTTOM_DP = 150 + SIZE_DP + 8;
    private static final float RESTING_ALPHA = 0.35f;
    private static final float RESTING_TUCK = 0.35f;
    private static final long REST_AFTER_MS = 2500;

    private TodayButton() {
    }

    public static void attachTo(Activity activity) {
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null || content.getChildCount() == 0) {
            return;
        }
        View already = content.findViewById(R.id.today_button);
        if (already != null) {
            wake(already);
            return;
        }

        ImageButton button = new ImageButton(activity);
        button.setId(R.id.today_button);
        button.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        button.setContentDescription(activity.getString(R.string.go_to_today));
        int pad = dp(activity, 10);
        button.setPadding(pad, pad, pad, pad);
        button.setBackgroundResource(R.drawable.privacy_button_idle);
        button.setImageResource(R.drawable.ic_today);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                dp(activity, SIZE_DP), dp(activity, SIZE_DP));
        lp.gravity = Gravity.END | Gravity.BOTTOM;
        lp.rightMargin = dp(activity, EDGE_DP);
        lp.bottomMargin = dp(activity, ABOVE_BOTTOM_DP);
        button.setLayoutParams(lp);
        button.setElevation(dp(activity, 6));

        button.setOnClickListener(v -> {
            wake(button);
            if (activity instanceof MainActivity) {
                ((MainActivity) activity).goToToday();
            }
        });

        content.addView(button);
        wake(button);
    }

    private static void wake(View button) {
        button.animate().cancel();
        button.setAlpha(1f);
        button.setTranslationX(0f);
        button.postDelayed(() -> rest(button), REST_AFTER_MS);
    }

    private static void rest(View button) {
        if (button.getParent() == null) {
            return;
        }
        button.animate().alpha(RESTING_ALPHA)
                .translationX(button.getWidth() * RESTING_TUCK)
                .setDuration(400).start();
    }

    private static int dp(Activity activity, float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
