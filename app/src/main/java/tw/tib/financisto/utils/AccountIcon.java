/*
 * This program is made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */
package tw.tib.financisto.utils;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import tw.tib.financisto.R;

/**
 * A symbol chosen for an account, independent of what kind of account it is, so
 * a current account and a securities account at the same institution can carry
 * the same mark.
 * <p>
 * Stored in the account's existing icon field, which until now held a single
 * emoji, using a leading marker no emoji starts with. Anything without that
 * marker is still text and is shown as before, so nothing that was set by hand
 * stops working.
 */
public enum AccountIcon {

    BANK("bank", R.drawable.account_type_bank, R.string.account_type_bank),
    SAVINGS("savings", R.drawable.account_type_savings, R.string.account_type_savings),
    INVESTMENT("investment", R.drawable.account_type_investment, R.string.account_type_investment),
    PENSION("pension", R.drawable.account_type_pension, R.string.account_type_pension),
    LOAN("loan", R.drawable.account_type_loan, R.string.account_type_loan),
    CARD("card", R.drawable.account_type_card, R.string.account_type_debit_card),
    DEBIT_CARD("debit", R.drawable.account_type_debit_card, R.string.account_type_debit_card),
    CREDIT_CARD("credit", R.drawable.account_type_credit_card, R.string.account_type_credit_card),
    CASH("cash", R.drawable.account_type_cash, R.string.account_type_cash),
    ELECTRONIC("electronic", R.drawable.account_type_electronic, R.string.account_type_electronic),
    WALLET("wallet", R.drawable.account_type_digital_wallet, R.string.account_type_electronic),
    ASSET("asset", R.drawable.account_type_asset, R.string.account_type_asset),
    LIABILITY("liability", R.drawable.account_type_liability, R.string.account_type_liability),
    OTHER("other", R.drawable.account_type_other, R.string.account_type_other);

    /** No emoji begins with this, so stored text is never mistaken for a symbol. */
    public static final String MARKER = "@";
    private static final String TARGET_SEPARATOR = "+";

    /** What the accent colour paints. Colouring both is rarely what is wanted. */
    public enum Target {
        BOTH(""), ICON("icon"), BAR("bar");

        public final String tag;

        Target(String tag) {
            this.tag = tag;
        }

        static Target of(String tag) {
            for (Target t : values()) {
                if (t.tag.equals(tag)) {
                    return t;
                }
            }
            return BOTH;
        }
    }

    public final String tag;
    @DrawableRes public final int iconId;
    @StringRes public final int titleId;

    AccountIcon(String tag, int iconId, int titleId) {
        this.tag = tag;
        this.iconId = iconId;
        this.titleId = titleId;
    }

    public String toStoredValue(Target target) {
        return target == Target.BOTH
                ? MARKER + tag
                : MARKER + tag + TARGET_SEPARATOR + target.tag;
    }

    /** The symbol this account's icon field names, or null when it holds text. */
    public static AccountIcon parse(String stored) {
        String tag = symbolTag(stored);
        if (tag == null) {
            return null;
        }
        for (AccountIcon icon : values()) {
            if (icon.tag.equals(tag)) {
                return icon;
            }
        }
        // Written by a later version that knows a symbol this one does not.
        return null;
    }

    /** What the accent colour should paint for this account. */
    public static Target parseTarget(String stored) {
        String tag = symbolTag(stored);
        if (tag == null) {
            return Target.BOTH;
        }
        int sep = stored.indexOf(TARGET_SEPARATOR);
        return sep < 0 ? Target.BOTH : Target.of(stored.substring(sep + 1));
    }

    private static String symbolTag(String stored) {
        if (stored == null || !stored.startsWith(MARKER)) {
            return null;
        }
        String rest = stored.substring(MARKER.length());
        int sep = rest.indexOf(TARGET_SEPARATOR);
        return sep < 0 ? rest : rest.substring(0, sep);
    }
}
