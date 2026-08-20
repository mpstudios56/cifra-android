package io.github.mpstudios56.cifra.utils;

import android.graphics.Color;
import android.widget.ImageView;
import android.widget.TextView;

import io.github.mpstudios56.cifra.model.Account;
import io.github.mpstudios56.cifra.model.AccountType;
import io.github.mpstudios56.cifra.model.CardIssuer;
import io.github.mpstudios56.cifra.model.ElectronicPaymentType;

/**
 * The mark an account wears, drawn wherever an account has to be recognised.
 * <p>
 * The accounts screen has always shown it; the list a movement offers showed
 * only names, so the same account looked like one line of text in one place and
 * like a card with a colour in the other.
 */
public class AccountMark {

    private AccountMark() {
    }

    /**
     * Puts the account's mark into an image, falling back to the letters it was
     * given when the mark is a letter rather than a symbol.
     */
    public static void drawInto(ImageView icon, TextView letters, Account a) {
        if (icon == null) {
            return;
        }
        AccountIcon chosen = AccountIcon.parse(a.icon);
        if (chosen != null) {
            show(icon, letters);
            icon.setImageResource(chosen.iconId);
            if (!chosen.tintable || AccountIcon.targetOf(a) == AccountIcon.Target.BAR) {
                icon.clearColorFilter();
            } else {
                tint(icon, a.accentColor);
            }
            return;
        }
        if (!Utils.isEmpty(a.icon)) {
            // Two letters standing for the account: the image steps aside.
            if (letters != null) {
                icon.setVisibility(ImageView.INVISIBLE);
                letters.setVisibility(TextView.VISIBLE);
                letters.setText(a.icon);
                return;
            }
            show(icon, null);
        }
        show(icon, letters);
        AccountType type = AccountType.valueOf(a.type);
        boolean itIsALogo = false;
        if (type.isCard && a.cardIssuer != null) {
            icon.setImageResource(CardIssuer.valueOf(a.cardIssuer).iconId);
            itIsALogo = true;
        } else if (type.isElectronic && a.cardIssuer != null) {
            icon.setImageResource(ElectronicPaymentType.valueOf(a.cardIssuer).iconId);
            itIsALogo = true;
        } else {
            icon.setImageResource(type.iconId);
        }
        // A recoloured logo is a wrong logo.
        if (itIsALogo || AccountIcon.targetOf(a) == AccountIcon.Target.BAR) {
            icon.clearColorFilter();
        } else {
            tint(icon, a.accentColor);
        }
    }

    private static void show(ImageView icon, TextView letters) {
        icon.setVisibility(ImageView.VISIBLE);
        if (letters != null) {
            letters.setVisibility(TextView.INVISIBLE);
        }
    }

    private static void tint(ImageView icon, String accent) {
        if (Utils.isEmpty(accent)) {
            icon.clearColorFilter();
            return;
        }
        try {
            icon.setColorFilter(Color.parseColor(accent));
        } catch (IllegalArgumentException wrongColour) {
            icon.clearColorFilter();
        }
    }
}
