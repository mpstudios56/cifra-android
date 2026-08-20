package io.github.mpstudios56.cifra.activity;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.appcompat.app.AlertDialog;

import io.github.mpstudios56.cifra.R;

/**
 * The two answers at the foot of a dialog, drawn as they are drawn at the foot
 * of a form: the thing being proposed filled in the app's green, the way out an
 * outline on nothing.
 * <p>
 * Asking for it through the theme did not hold - each kind of dialog builds its
 * buttons its own way, and some of them refuse a background handed to them - so
 * the two are dressed once the dialog is on screen, where they certainly exist.
 */
public class DialogAnswers {

    private DialogAnswers() {
    }

    /** Shows the dialog with its answers in the app's own shape. */
    public static void show(AlertDialog dialog) {
        dialog.setOnShowListener(shown -> dress(dialog));
        dialog.show();
    }

    private static void dress(AlertDialog dialog) {
        Context context = dialog.getContext();
        pill(context, dialog.getButton(DialogInterface.BUTTON_POSITIVE), true);
        pill(context, dialog.getButton(DialogInterface.BUTTON_NEGATIVE), false);
        pill(context, dialog.getButton(DialogInterface.BUTTON_NEUTRAL), false);
    }

    private static void pill(Context context, Button button, boolean proposed) {
        if (button == null) {
            return;
        }
        float density = context.getResources().getDisplayMetrics().density;
        // Built here rather than taken from a file: a button inside a dialog
        // carries a tint of its own, which swallowed the outline of the way out
        // and left the word standing on nothing.
        button.setBackgroundTintList(null);
        android.graphics.drawable.GradientDrawable shape =
                new android.graphics.drawable.GradientDrawable();
        shape.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        shape.setCornerRadius(24 * density);
        if (proposed) {
            shape.setColor(0xFF2F8F63);
        } else {
            shape.setColor(Color.TRANSPARENT);
            shape.setStroke(Math.max(1, Math.round(density)), 0x59FFFFFF);
        }
        button.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(proposed ? 0x33000000 : 0x33FFFFFF),
                shape, null));
        button.setTextColor(proposed ? Color.WHITE : 0xFFF4EFE4);
        button.setAllCaps(false);
        button.setTypeface(button.getTypeface(),
                proposed ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        int side = Math.round(18 * density);
        int top = Math.round(10 * density);
        button.setPadding(side, top, side, top);
        button.setMinWidth(Math.round(92 * density));
        button.setStateListAnimator(null);
        button.setElevation(0f);
        ViewGroup.LayoutParams lp = button.getLayoutParams();
        if (lp instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams l = (LinearLayout.LayoutParams) lp;
            l.setMarginStart(Math.round(8 * density));
            l.height = Math.round(48 * density);
            button.setLayoutParams(l);
        }
    }
}
