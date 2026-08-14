package tw.tib.financisto.view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.ListView;

import androidx.core.content.ContextCompat;

/**
 * Dragging a row sideways to do something to it, the way a mail app does.
 * <p>
 * Written by hand rather than taken from the toolbox: the ready-made helper
 * belongs to RecyclerView and this list is a ListView. Moving the list of
 * movements - the most used screen in the app - to another kind of list, only
 * to gain a gesture, is a large risk for a small thing; a touch listener is a
 * small risk for the same thing.
 * <p>
 * What is seen is what one expects from having used mail: the row slides, and
 * from under it comes a colour with a sign standing in it, on the side the
 * finger came from. The colour is faint while the movement would spring back
 * and turns solid the moment it has gone far enough, so the hand knows the
 * answer before it lets go.
 * <p>
 * It stays out of the way until it is certain: nothing happens until the finger
 * has gone further sideways than down, and further than the distance the system
 * itself calls a drag. Below that, taps and long presses reach the list exactly
 * as they did before.
 */
public class SwipeOnRows implements View.OnTouchListener {

    /** What the screen underneath wants to know. */
    public interface Handler {
        /** Whether this row can be dragged at all. */
        boolean canSwipe(int position, long id);

        /** The colour that comes out from under the row. Zero for no action. */
        int colourFor(boolean toTheRight);

        /** The sign standing in that colour. Zero for none. */
        int iconFor(boolean toTheRight);

        /** The row went far enough. */
        void swiped(int position, long id, boolean toTheRight);
    }

    /** How much of the colour is showing while the row would still spring back. */
    private static final int FAINT = 0x55;
    /** And once it would not. */
    private static final int SOLID = 0xE0;

    private final ListView list;
    private final Handler handler;
    private final int slop;
    private final int minimumDistance;
    private final int iconSize;
    private final int iconMargin;

    private View row;
    private int position = ListView.INVALID_POSITION;
    private long id;
    private float startX;
    private float startY;
    private boolean dragging;
    /** Which way the colour underneath is currently dressed: 0 none, 1 right, -1 left. */
    private int dressedAs;
    private boolean dressedSolid;

    private SwipeOnRows(ListView list, Handler handler) {
        this.list = list;
        this.handler = handler;
        ViewConfiguration vc = ViewConfiguration.get(list.getContext());
        float density = list.getResources().getDisplayMetrics().density;
        this.slop = vc.getScaledTouchSlop() * 2;
        this.minimumDistance = Math.round(96 * density);
        this.iconSize = Math.round(24 * density);
        this.iconMargin = Math.round(20 * density);
    }

    public static void attach(ListView list, Handler handler) {
        list.setOnTouchListener(new SwipeOnRows(list, handler));
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                take(event);
                return false;

            case MotionEvent.ACTION_MOVE:
                if (row == null) {
                    return false;
                }
                float dx = event.getX() - startX;
                float dy = event.getY() - startY;
                if (!dragging) {
                    if (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 2) {
                        dragging = true;
                        list.requestDisallowInterceptTouchEvent(true);
                        // The list is told the touch is over, so that what began
                        // as a press does not also end as a tap or a long press.
                        MotionEvent cancel = MotionEvent.obtain(event);
                        cancel.setAction(MotionEvent.ACTION_CANCEL);
                        list.onTouchEvent(cancel);
                        cancel.recycle();
                    } else {
                        return false;
                    }
                }
                dress(dx);
                slide(dx);
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (row == null || !dragging) {
                    letGo();
                    return false;
                }
                float travelled = event.getX() - startX;
                if (farEnough(travelled) && event.getActionMasked() == MotionEvent.ACTION_UP) {
                    away(travelled > 0);
                } else {
                    back();
                }
                return true;
        }
        return false;
    }

    private boolean farEnough(float dx) {
        return Math.abs(dx) > Math.max(minimumDistance, list.getWidth() / 3f);
    }

    /** Finds the row under the finger, if it is one that can be dragged. */
    private void take(MotionEvent event) {
        letGo();
        int found = list.pointToPosition((int) event.getX(), (int) event.getY());
        if (found == ListView.INVALID_POSITION) {
            return;
        }
        long rowId = list.getItemIdAtPosition(found);
        if (!handler.canSwipe(found, rowId)) {
            return;
        }
        View child = list.getChildAt(found - list.getFirstVisiblePosition());
        if (!(child instanceof ViewGroup)) {
            return;
        }
        row = child;
        position = found;
        id = rowId;
        startX = event.getX();
        startY = event.getY();
        dragging = false;
        dressedAs = 0;
        dressedSolid = false;
    }

    /**
     * Moves the contents of the row and leaves its own background where it is,
     * so that what the row is standing on is what shows through.
     */
    private void slide(float dx) {
        ViewGroup group = (ViewGroup) row;
        for (int i = 0; i < group.getChildCount(); i++) {
            group.getChildAt(i).setTranslationX(dx);
        }
    }

    /** Puts the colour and the sign under the row, on the side being uncovered. */
    private void dress(float dx) {
        int way = dx > 0 ? 1 : -1;
        boolean solid = farEnough(dx);
        if (way == dressedAs && solid == dressedSolid) {
            return;
        }
        dressedAs = way;
        dressedSolid = solid;

        int colour = handler.colourFor(way > 0);
        if (colour == 0) {
            row.setBackgroundColor(0);
            return;
        }
        int alpha = solid ? SOLID : FAINT;
        ColorDrawable ground = new ColorDrawable(
                (alpha << 24) | (colour & 0xFFFFFF));

        int iconId = handler.iconFor(way > 0);
        Drawable icon = iconId == 0 ? null : ContextCompat.getDrawable(list.getContext(), iconId);
        if (icon == null) {
            row.setBackground(ground);
            return;
        }
        icon = icon.mutate();
        icon.setTint(Color.WHITE);
        // The sign stands on the side the row is moving away from, which is the
        // side that is being uncovered; it is the way a hand reads the gesture.
        LayerDrawable both = new LayerDrawable(new Drawable[]{ground, icon});
        both.setLayerSize(1, iconSize, iconSize);
        both.setLayerGravity(1, (way > 0 ? Gravity.START : Gravity.END)
                | Gravity.CENTER_VERTICAL);
        both.setLayerInset(1, way > 0 ? iconMargin : 0, 0, way > 0 ? 0 : iconMargin, 0);
        row.setBackground(both);
    }

    private void away(final boolean toTheRight) {
        final View going = row;
        final int where = position;
        final long which = id;
        letGo();
        final ViewGroup group = (ViewGroup) going;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            child.animate()
                    .translationX(toTheRight ? list.getWidth() : -list.getWidth())
                    .alpha(0)
                    .setDuration(160)
                    .setListener(null)
                    .start();
        }
        // The row is put straight again before anything is done to the movement,
        // and only then is the movement acted on: the list redraws immediately
        // afterwards, and a row still lying on its side comes back carrying
        // somebody else's payment.
        going.postDelayed(() -> {
            undress(group);
            handler.swiped(where, which, toTheRight);
        }, 165);
    }

    private void back() {
        final View going = row;
        letGo();
        final ViewGroup group = (ViewGroup) going;
        for (int i = 0; i < group.getChildCount(); i++) {
            group.getChildAt(i).animate().translationX(0).alpha(1).setDuration(140)
                    .setListener(null).start();
        }
        going.postDelayed(() -> going.setBackgroundColor(0), 145);
    }

    private void undress(ViewGroup group) {
        clean(group);
    }

    /**
     * Puts a row back the way it was born: nothing running, nothing moved,
     * nothing faded, no colour underneath.
     * <p>
     * Called from here when a gesture ends, and by the list itself every time a
     * row is handed a new movement to show - because between those two moments
     * the row may have been reused, and an animation still running on it would
     * otherwise finish onto whatever it is showing now.
     */
    public static void clean(View row) {
        if (!(row instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) row;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            child.animate().cancel();
            child.setTranslationX(0);
            child.setAlpha(1);
        }
        group.setBackgroundColor(0);
    }

    private void letGo() {
        row = null;
        position = ListView.INVALID_POSITION;
        dragging = false;
        dressedAs = 0;
        dressedSolid = false;
    }
}
