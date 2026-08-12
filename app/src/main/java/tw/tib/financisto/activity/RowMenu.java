package tw.tib.financisto.activity;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.List;

import tw.tib.financisto.R;
import tw.tib.financisto.utils.MenuItemInfo;

/**
 * The menu that opens when a row is held down.
 * <p>
 * It was the platform's own popup: a column of bare words, the last thing in
 * the app still dressed the way Android dressed things in 2014, and jarring
 * beside the grid of symbols a short tap brings up. Same actions, so the same
 * symbols.
 * <p>
 * The lines are laid out one under another rather than put in a list, because
 * a list scrolls: a menu of seven short lines that has to be dragged to reach
 * its last line is worse than the plain one it replaced. Laid out whole, it is
 * as tall as it needs to be and is then placed where it fits on screen.
 */
public class RowMenu {

    private static final int MARGIN_DP = 16;

    public interface OnPicked {
        void picked(int menuId);
    }

    public static void show(Context context, View anchor, List<MenuItemInfo> items, OnPicked listener) {
        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setBackgroundResource(R.drawable.popup_menu_background);
        // Room above the first line and below the last one, or the words sit on
        // the rounded edge and the menu looks cut off.
        int air = dp(context, 10);
        body.setPadding(0, air, 0, air);

        PopupWindow popup = new PopupWindow(body,
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(0x00000000));
        popup.setElevation(dp(context, 8));
        popup.setOutsideTouchable(true);

        LayoutInflater inflater = LayoutInflater.from(context);
        boolean any = false;
        for (MenuItemInfo item : items) {
            if (!item.enabled) continue;
            any = true;
            View row = inflater.inflate(R.layout.row_menu_item, body, false);
            ImageView icon = row.findViewById(R.id.menu_icon);
            if (item.iconId != 0) {
                icon.setImageResource(item.iconId);
                icon.setVisibility(View.VISIBLE);
            } else {
                // Kept in place, empty: the words stay in one column even when
                // one of them has no symbol of its own.
                icon.setVisibility(View.INVISIBLE);
            }
            ((TextView) row.findViewById(R.id.menu_title)).setText(item.titleId);
            row.setOnClickListener(v -> {
                popup.dismiss();
                listener.picked(item.menuId);
            });
            body.addView(row);
        }
        if (!any) return;

        place(context, popup, body, anchor);
    }

    /**
     * Puts the menu beside the row it belongs to, and inside the screen: near
     * the bottom of a list it moves up rather than running off the edge.
     */
    private static void place(Context context, PopupWindow popup, View body, View anchor) {
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = context.getResources().getDisplayMetrics().heightPixels;
        int margin = dp(context, MARGIN_DP);

        body.measure(
                View.MeasureSpec.makeMeasureSpec(screenWidth - 2 * margin, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int width = Math.max(body.getMeasuredWidth(), dp(context, 200));
        int height = body.getMeasuredHeight();

        int[] at = new int[2];
        anchor.getLocationOnScreen(at);
        Rect visible = new Rect();
        anchor.getWindowVisibleDisplayFrame(visible);

        int x = Math.max(margin, Math.min(at[0] + margin, screenWidth - width - margin));
        int y = at[1] + anchor.getHeight() / 2;
        int floor = Math.min(visible.bottom, screenHeight) - height - margin;
        if (y > floor) y = floor;
        if (y < visible.top + margin) y = visible.top + margin;

        popup.setWidth(width);
        popup.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
