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

    /** One set of measurements for all three buttons, shared with the eye. */
    static final int SIZE_DP = 48;
    static final int EDGE_DP = 10;
    static final int FIRST_BOTTOM_DP = 150;
    /** The three sit in a column: the eye at 150, then the oldest, then today. */
    private static final int OLDEST_BOTTOM_DP = FIRST_BOTTOM_DP + SIZE_DP + 8;
    private static final int ABOVE_BOTTOM_DP = FIRST_BOTTOM_DP + 2 * (SIZE_DP + 8);
    private static final int NEWEST_BOTTOM_DP = FIRST_BOTTOM_DP + 3 * (SIZE_DP + 8);
    private static final float RESTING_ALPHA = 0.42f;
    private static final float RESTING_TUCK = 0.22f;
    private static final long REST_AFTER_MS = 2500;

    private TodayButton() {
    }

    public static void attachTo(Activity activity) {
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null || content.getChildCount() == 0) {
            return;
        }
        attachOldest(activity, content);
        attachTop(activity, content);
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
            wakeAll(button);
            if (activity instanceof MainActivity) {
                ((MainActivity) activity).goToToday();
                return;
            }
            BlotterFragment blotter = blotterIn(activity);
            if (blotter != null) {
                blotter.goToToday();
            }
        });

        // Born hidden. Whoever knows what screen this is turns on the ones
        // that belong to it; attaching them visible is what made them flicker
        // into sight for a frame on screens where they have no business.
        button.setVisibility(View.GONE);
        content.addView(button);
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
            wakeAll(button);
            if (activity instanceof MainActivity) {
                ((MainActivity) activity).goToOldest();
                return;
            }
            BlotterFragment blotter = blotterIn(activity);
            if (blotter != null) {
                blotter.goToOldest();
            }
        });
        // Born hidden. Whoever knows what screen this is turns on the ones
        // that belong to it; attaching them visible is what made them flicker
        // into sight for a frame on screens where they have no business.
        button.setVisibility(View.GONE);
        content.addView(button);
    }

    /**
     * The movements inside a single account are the same list on a screen of
     * their own, so the two buttons mean the same thing there and are asked of
     * the fragment directly.
     */
    private static BlotterFragment blotterIn(Activity activity) {
        if (!(activity instanceof androidx.fragment.app.FragmentActivity)) {
            return null;
        }
        for (androidx.fragment.app.Fragment f : ((androidx.fragment.app.FragmentActivity) activity)
                .getSupportFragmentManager().getFragments()) {
            if (f instanceof BlotterFragment && f.isAdded()) {
                return (BlotterFragment) f;
            }
        }
        return null;
    }

    /**
     * Puts the buttons that are showing in a column with no gaps.
     * <p>
     * Each used to sit at a height of its own, so on a screen where one of them
     * is not wanted the others stood apart with a hole between them, as though
     * something were missing. Whoever is on screen is stacked from the eye
     * upwards, in the order eye, oldest, today.
     */
    /**
     * Turns on the buttons a screen has a use for, and leaves the rest off.
     * <p>
     * The eye belongs anywhere there are figures; the three arrows belong only
     * to a list of movements in time.
     */
    public static void showFor(Activity activity, boolean movements) {
        View eye = activity.findViewById(R.id.privacy_button);
        if (eye != null) {
            eye.setVisibility(View.VISIBLE);
        }
        for (int id : new int[]{R.id.oldest_button, R.id.today_button, R.id.top_button}) {
            View one = activity.findViewById(id);
            if (one != null) {
                one.setVisibility(movements ? View.VISIBLE : View.GONE);
            }
        }
        stack(activity);
    }

    public static void stack(Activity activity) {
        View eye = activity.findViewById(R.id.privacy_button);
        View oldest = activity.findViewById(R.id.oldest_button);
        View today = activity.findViewById(R.id.today_button);
        View newest = activity.findViewById(R.id.top_button);
        // Above the navigation keys, wherever the screen has not already made
        // room for them: inside an account nothing had, and the column ended up
        // underneath the phone's own buttons.
        int under = 0;
        View content = activity.findViewById(android.R.id.content);
        if (content != null && content.getPaddingBottom() == 0) {
            androidx.core.view.WindowInsetsCompat insets =
                    androidx.core.view.ViewCompat.getRootWindowInsets(content);
            if (insets != null) {
                under = insets.getInsets(
                        androidx.core.view.WindowInsetsCompat.Type.systemBars()).bottom;
            }
        }
        int step = SIZE_DP + 8;
        int at = FIRST_BOTTOM_DP;
        for (View button : new View[]{eye, oldest, today, newest}) {
            if (button == null || button.getVisibility() != View.VISIBLE) {
                continue;
            }
            android.view.ViewGroup.LayoutParams lp = button.getLayoutParams();
            if (lp instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams f = (FrameLayout.LayoutParams) lp;
                int wanted = dp(activity, at) + under;
                int side = dp(activity, SIZE_DP);
                int edge = dp(activity, EDGE_DP);
                // Height, width and distance from the edge all set here, so the
                // three cannot drift apart however each of them was made.
                if (f.bottomMargin != wanted || f.rightMargin != edge
                        || f.width != side || f.height != side) {
                    f.bottomMargin = wanted;
                    f.rightMargin = edge;
                    f.width = side;
                    f.height = side;
                    f.gravity = Gravity.END | Gravity.BOTTOM;
                    button.setLayoutParams(f);
                }
            }
            at += step;
        }
    }

    /**
     * Wakes the whole column, not one of its buttons.
     * <p>
     * Each used to keep its own clock, so one would be tucked against the edge
     * while the one above it was still out: three buttons in a line that never
     * looked like a line.
     */
    static void wakeAll(View button) {
        android.view.ViewParent parent = button.getParent();
        if (!(parent instanceof ViewGroup)) {
            wake(button);
            return;
        }
        ViewGroup content = (ViewGroup) parent;
        for (int id : new int[]{R.id.privacy_button, R.id.oldest_button, R.id.today_button, R.id.top_button}) {
            View one = content.findViewById(id);
            if (one != null && one.getVisibility() == View.VISIBLE) {
                wake(one);
            }
        }
    }

    /** And back to the top of the list, which is where the newest movement is. */
    private static void attachTop(Activity activity, ViewGroup content) {
        View already = content.findViewById(R.id.top_button);
        if (already != null) {
            wake(already);
            return;
        }
        ImageButton button = new ImageButton(activity);
        button.setId(R.id.top_button);
        button.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        button.setContentDescription(activity.getString(R.string.go_to_top));
        int pad = dp(activity, 10);
        button.setPadding(pad, pad, pad, pad);
        button.setBackgroundResource(R.drawable.privacy_button_idle);
        button.setImageResource(R.drawable.ic_top);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                dp(activity, SIZE_DP), dp(activity, SIZE_DP));
        lp.gravity = Gravity.END | Gravity.BOTTOM;
        lp.rightMargin = dp(activity, EDGE_DP);
        lp.bottomMargin = dp(activity, FIRST_BOTTOM_DP + 3 * (SIZE_DP + 8));
        button.setLayoutParams(lp);
        button.setElevation(dp(activity, 6));
        button.setOnClickListener(v -> {
            wakeAll(button);
            if (activity instanceof MainActivity) {
                ((MainActivity) activity).goToTop();
                return;
            }
            BlotterFragment blotter = blotterIn(activity);
            if (blotter != null) {
                blotter.goToTop();
            }
        });
        // Born hidden. Whoever knows what screen this is turns on the ones
        // that belong to it; attaching them visible is what made them flicker
        // into sight for a frame on screens where they have no business.
        button.setVisibility(View.GONE);
        content.addView(button);
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
