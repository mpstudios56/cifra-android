/*
 * This program is made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */
package tw.tib.financisto.activity;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.appcompat.app.AlertDialog;

import tw.tib.financisto.R;
import tw.tib.financisto.utils.CategoryIcon;
import tw.tib.financisto.utils.Identity;
import tw.tib.financisto.view.FlowLayout;

/**
 * Says who one of the two people is: a name, a colour, a symbol.
 * <p>
 * A dialog rather than a screen. It is three choices, two of them made by
 * looking rather than by reading, and a screen of its own would be a screen
 * one has to come back from.
 */
public class IdentityDialog {

    private IdentityDialog() {
    }

    public interface OnSaved {
        void saved();
    }

    public static void show(Activity activity, int which, OnSaved onSaved) {
        Identity current = which == Identity.MINE
                ? Identity.mine(activity) : Identity.theirs(activity);

        View view = LayoutInflater.from(activity).inflate(R.layout.identity_dialog, null);
        EditText name = view.findViewById(R.id.identity_name);
        name.setText(current.name);

        int[] chosenColour = {current.colour};
        String[] chosenIcon = {current.icon};

        FlowLayout colours = view.findViewById(R.id.identity_colours);
        View[] swatches = new View[Identity.COLOURS.length];
        for (int i = 0; i < Identity.COLOURS.length; i++) {
            final int colour = Identity.COLOURS[i];
            View swatch = swatch(activity, colour, colour == current.colour);
            swatches[i] = swatch;
            swatch.setOnClickListener(v -> {
                chosenColour[0] = colour;
                for (int j = 0; j < swatches.length; j++) {
                    mark(swatches[j], Identity.COLOURS[j], Identity.COLOURS[j] == colour);
                }
            });
            colours.addView(swatch);
        }

        // No symbols here any more. What tells one person from another is the
        // colour, and a wall of forty-four pictures under a name was a shop
        // window rather than a choice.


        new AlertDialog.Builder(activity)
                .setTitle(which == Identity.MINE ? R.string.sync_author : R.string.sync_partner)
                .setView(view)
                .setPositiveButton(R.string.save, (d, w) -> {
                    Identity.save(activity, which, name.getText().toString(),
                            chosenColour[0], chosenIcon[0]);
                    if (onSaved != null) {
                        onSaved.saved();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static View swatch(Context context, int colour, boolean chosen) {
        View view = new View(context);
        int side = Math.round(38 * context.getResources().getDisplayMetrics().density);
        view.setLayoutParams(new android.view.ViewGroup.LayoutParams(side, side));
        mark(view, colour, chosen);
        return view;
    }

    /** A ring around the one chosen: on a round swatch there is nowhere else to say it. */
    private static void mark(View view, int colour, boolean chosen) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(colour);
        if (chosen) {
            shape.setStroke(Math.round(3 * view.getResources().getDisplayMetrics().density),
                    0xFFFFFFFF);
        }
        view.setBackground(shape);
    }

    private static ImageView symbol(Context context, CategoryIcon icon, boolean chosen) {
        ImageView view = new ImageView(context);
        int side = Math.round(38 * context.getResources().getDisplayMetrics().density);
        view.setLayoutParams(new android.view.ViewGroup.LayoutParams(side, side));
        view.setImageResource(icon.iconId);
        view.setImageTintList(ColorStateList.valueOf(0xFFF4EFE4));
        view.setAlpha(chosen ? 1f : 0.45f);
        return view;
    }
}
