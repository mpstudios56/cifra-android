/*
 * This program is made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */
package tw.tib.financisto.utils;

import android.graphics.Color;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import tw.tib.financisto.model.Category;

/**
 * Puts a category's symbol on screen.
 * <p>
 * In one place because more than one list shows it - the categories themselves,
 * and every transaction filed under one - and a symbol drawn by two different
 * rules is two different symbols.
 * <p>
 * The rule: a chosen symbol, tinted with the chosen colour; failing that,
 * whatever text was typed; failing that, nothing at all - but still taking up
 * its space, so the names stay in a column instead of stepping in and out.
 */
public class CategoryIcons {

    private CategoryIcons() {
    }

    /**
     * @param image where a drawn symbol goes
     * @param text  where typed text goes; may be null in a list with no room for it
     */
    public static void show(ImageView image, TextView text, String icon, String accentColor) {
        CategoryIcon chosen = CategoryIcon.parse(icon);
        if (chosen != null) {
            image.setVisibility(View.VISIBLE);
            image.setImageResource(chosen.iconId);
            image.setColorFilter(colorOf(accentColor));
            if (text != null) {
                text.setVisibility(View.INVISIBLE);
            }
            return;
        }
        if (text != null && !Utils.isEmpty(icon)) {
            image.setVisibility(View.INVISIBLE);
            text.setVisibility(View.VISIBLE);
            text.setText(icon);
            text.setTextColor(colorOf(accentColor));
            return;
        }
        image.setVisibility(View.INVISIBLE);
        image.setImageDrawable(null);
        if (text != null) {
            text.setVisibility(View.INVISIBLE);
        }
    }

    public static void show(ImageView image, TextView text, Category category) {
        show(image, text, category.icon, category.accentColor);
    }

    public static void show(ImageView image, Category category) {
        show(image, null, category.icon, category.accentColor);
    }

    /** The chosen colour, or white, which is what the rest of the row is. */
    public static int colorOf(String accentColor) {
        try {
            return Color.parseColor(accentColor.trim());
        } catch (Exception e) {
            return Color.WHITE;
        }
    }
}
