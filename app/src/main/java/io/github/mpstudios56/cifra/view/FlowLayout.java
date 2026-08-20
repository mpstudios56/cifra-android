/*
 * This program is made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */
package io.github.mpstudios56.cifra.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/**
 * A row of things that carries on onto the next line when it runs out of width.
 * <p>
 * Written rather than borrowed because the app's theme is a Holo one, and the
 * Material chip group - which would do this - refuses to work outside a
 * Material theme. Sixty lines here is cheaper than restyling the whole app.
 * <p>
 * The point is that nothing is ever half off the edge: a shortcut sliced down
 * the middle by a scrolling strip looks like a mistake, and gives no hint that
 * there is more of it to the right.
 */
public class FlowLayout extends ViewGroup {

    private int horizontalGap;
    private int verticalGap;

    public FlowLayout(Context context) {
        this(context, null);
    }

    public FlowLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        horizontalGap = Math.round(6 * density);
        verticalGap = Math.round(6 * density);
    }

    public void setGaps(int horizontal, int vertical) {
        horizontalGap = horizontal;
        verticalGap = vertical;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec)
                - getPaddingLeft() - getPaddingRight();
        int childSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST);

        int x = 0, rowHeight = 0, height = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            measureChild(child, childSpec, heightMeasureSpec);
            if (x > 0 && x + child.getMeasuredWidth() > width) {
                // Does not fit on this line, so it starts the next one.
                height += rowHeight + verticalGap;
                x = 0;
                rowHeight = 0;
            }
            x += child.getMeasuredWidth() + horizontalGap;
            rowHeight = Math.max(rowHeight, child.getMeasuredHeight());
        }
        height += rowHeight;

        setMeasuredDimension(
                MeasureSpec.getSize(widthMeasureSpec),
                resolveSize(height + getPaddingTop() + getPaddingBottom(), heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int width = getWidth() - getPaddingLeft() - getPaddingRight();
        int x = getPaddingLeft(), y = getPaddingTop(), rowHeight = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            if (x > getPaddingLeft() && x + child.getMeasuredWidth() > width + getPaddingLeft()) {
                y += rowHeight + verticalGap;
                x = getPaddingLeft();
                rowHeight = 0;
            }
            child.layout(x, y,
                    x + child.getMeasuredWidth(), y + child.getMeasuredHeight());
            x += child.getMeasuredWidth() + horizontalGap;
            rowHeight = Math.max(rowHeight, child.getMeasuredHeight());
        }
    }
}
