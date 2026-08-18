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
    /** The three sit in a column: the eye at 150, then the oldest, then today. */
    private static final int OLDEST_BOTTOM_DP = 150 + SIZE_DP + 8;
    private static final int ABOVE_BOTTOM_DP = 150 + 2 * (SIZE_DP + 8);
    private static final float RESTING_ALPHA = 0.30f;
    private static final float RESTING_TUCK = 0.52f;
    private static final long REST_AFTER_MS = 2500;

    private TodayButton() {
    }

    public static void attachTo(Activity activity) {
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null || content.getChildCount() == 0) {
            return;
        }
        attachOldest(activity, content);
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

    /**
     * The other end of the same idea: down to the first movement ever written.
     * <p>
     * Only on the movements, where "the first one" means something. On a
     * summary or a list of accounts it would be a button that scrolls to
     * nothing in particular.
     */
    private static void attachOldest(Activity activity, ViewGroup content) {
        View already = content.findViewById(R.id.oldest_button);
        if (already != null) {
            wake(already);
            return;
        }
        ImageButton button = new ImageButton(activity);
        button.setId(R.id.oldest_button);
        button.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        button.setContentDescription(activity.getString(R.string.go_to_oldest));
        int pad = dp(activity, 10);
        button.setPadding(pad, pad, pad, pad);
        button.setBackgroundResource(R.drawable.privacy_button_idle);
        button.setImageResource(R.drawable.ic_oldest);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                dp(activity, SIZE_DP), dp(activity, SIZE_DP));
        lp.gravity = Gravity.END | Gravity.BOTTOM;
        lp.rightMargin = dp(activity, EDGE_DP);
        lp.bottomMargin = dp(activity, OLDEST_BOTTOM_DP);
        button.setLayoutParams(lp);
        button.setElevation(dp(activity, 6));
        button.setOnClickListener(v -> {
            wake(button);
            if (activity instanceof MainActivity) {
                ((MainActivity) activity).goToOldest();
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
