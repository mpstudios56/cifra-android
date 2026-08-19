package tw.tib.financisto.activity;

import android.content.Context;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LightingColorFilter;
import android.graphics.drawable.Drawable;

import androidx.annotation.ColorInt;
import androidx.core.content.res.ResourcesCompat;

import greendroid.widget.QuickAction;

/**
 * Created by IntelliJ IDEA.
 * User: Denis Solonenko
 * Date: 7/25/11 9:56 PM
 */
public class MyQuickAction extends QuickAction {
    public static int NO_FILTER = -1;
    public int titleId = -1;

    /**
     * The icons are drawn white and were being forced to black for a white
     * bubble. The bubble is dark now, so they are left the colour they were
     * drawn - and the ones that carry their own colour, like the transaction
     * states, still pass NO_FILTER and keep it.
     */
    private static final ColorFilter LIGHT_CF =
            new LightingColorFilter(Color.BLACK, 0xFFF4EFE4);

    public MyQuickAction(Context ctx, int drawableId, int titleId) {
        super(ctx, buildDrawable(ctx, drawableId), titleId);
        this.titleId = titleId;
    }

    public MyQuickAction(Context ctx, int drawableId, @ColorInt int color, int titleId) {
        super(ctx, buildColorDrawable(ctx, color, drawableId), titleId);
        this.titleId = titleId;
    }

    /**
     * The same, drawn larger.
     * <p>
     * For the five states and nothing else: they are read as colours, and a
     * colour needs room to be one. The rest of the ring keeps the size it had.
     */
    public static MyQuickAction big(Context ctx, int drawableId, int titleId) {
        return new MyQuickAction(ctx, drawableId, titleId, true);
    }

    private MyQuickAction(Context ctx, int drawableId, int titleId, boolean big) {
        super(ctx, sized(ctx, ResourcesCompat.getDrawable(
                ctx.getResources(), drawableId, null).mutate()), titleId);
        this.titleId = titleId;
    }

    /**
     * How large a symbol stands in the ring.
     * <p>
     * They were drawn at whatever size the picture happened to be, which for
     * the coloured states meant a dot: the symbol is read before the word under
     * it, so it is given the room to be read.
     */
    private static final int SYMBOL_DP = 34;

    private static Drawable sized(Context ctx, Drawable d) {
        int side = Math.round(SYMBOL_DP * ctx.getResources().getDisplayMetrics().density);
        d.setBounds(0, 0, side, side);
        return d;
    }

    private static Drawable buildDrawable(Context ctx, int drawableId) {
        Drawable d = ResourcesCompat.getDrawable(ctx.getResources(), drawableId, null).mutate();
        d.setColorFilter(LIGHT_CF);
        return d;
    }

    private static Drawable buildColorDrawable(Context ctx, @ColorInt int color, int drawableId) {
        if (color == NO_FILTER) {
            return ResourcesCompat.getDrawable(ctx.getResources(), drawableId, null);
        }
        else {
            Drawable d = ResourcesCompat.getDrawable(ctx.getResources(), drawableId, null).mutate();
            d.setColorFilter(new LightingColorFilter(Color.BLACK, color));
            return d;
        }
    }
}
