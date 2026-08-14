package tw.tib.financisto.view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.ListView;

/**
 * Dragging a row sideways to do something to it, the way a mail app does.
 * <p>
 * Written by hand rather than taken from the toolbox: the ready-made helper
 * belongs to RecyclerView and this list is a ListView. Moving the list of
 * movements - the most used screen in the app - to another kind of list, only
 * to gain a gesture, is a large risk for a small thing; a touch listener is a
 * small risk for the same thing.
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

        /** The colour to show under the finger once the row would act. Zero for none. */
        int colourFor(boolean toTheRight);

        /** The row went far enough. The row is put back by the screen redrawing. */
        void swiped(int position, long id, boolean toTheRight);
    }

    private final ListView list;
    private final Handler handler;
    private final int slop;
    private final int minimumDistance;

    private View row;
    private int position = ListView.INVALID_POSITION;
    private long id;
    private float startX;
    private float startY;
    private boolean dragging;

    private SwipeOnRows(ListView list, Handler handler) {
        this.list = list;
        this.handler = handler;
        ViewConfiguration vc = ViewConfiguration.get(list.getContext());
        this.slop = vc.getScaledTouchSlop() * 2;
        this.minimumDistance = Math.round(96 * list.getResources().getDisplayMetrics().density);
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
                row.setTranslationX(dx);
                row.setAlpha(Math.max(0.3f, 1 - Math.abs(dx) / (list.getWidth() * 0.7f)));
                paint(dx);
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (row == null || !dragging) {
                    letGo();
                    return false;
                }
                float travelled = event.getX() - startX;
                boolean far = Math.abs(travelled) > Math.max(minimumDistance, list.getWidth() / 3f);
                if (far && event.getActionMasked() == MotionEvent.ACTION_UP) {
                    away(travelled > 0);
                } else {
                    back();
                }
                return true;
        }
        return false;
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
        if (child == null) {
            return;
        }
        row = child;
        position = found;
        id = rowId;
        startX = event.getX();
        startY = event.getY();
        dragging = false;
    }

    /** The colour of what is about to happen, once it is about to happen. */
    private void paint(float dx) {
        boolean far = Math.abs(dx) > Math.max(minimumDistance, list.getWidth() / 3f);
        int colour = far ? handler.colourFor(dx > 0) : 0;
        row.setBackgroundColor(colour == 0 ? 0 : (0x66000000 | (colour & 0xFFFFFF)));
    }

    private void away(final boolean toTheRight) {
        final View going = row;
        final int where = position;
        final long which = id;
        letGo();
        going.animate()
                .translationX(toTheRight ? list.getWidth() : -list.getWidth())
                .alpha(0)
                .setDuration(160)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        // Put back as it was before anything is done: the row is
                        // reused for another movement the moment the list is
                        // redrawn, and a row that stays invisible and pushed to
                        // one side comes back as somebody else's payment.
                        going.setTranslationX(0);
                        going.setAlpha(1);
                        going.setBackgroundColor(0);
                        handler.swiped(where, which, toTheRight);
                    }
                });
    }

    private void back() {
        final View going = row;
        letGo();
        going.animate().translationX(0).alpha(1).setDuration(140)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        going.setBackgroundColor(0);
                    }
                });
    }

    private void letGo() {
        row = null;
        position = ListView.INVALID_POSITION;
        dragging = false;
    }
}
