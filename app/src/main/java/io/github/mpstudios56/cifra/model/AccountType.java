package io.github.mpstudios56.cifra.model;

import io.github.mpstudios56.cifra.R;
import io.github.mpstudios56.cifra.utils.EntityEnum;

/**
 * What kind of account this is, and what the app should therefore ask about it.
 * <p>
 * The kind is not only a mark in the list: it decides which fields the account
 * screen shows. A card is asked whose it is and for its last four figures; a
 * wallet is asked which service holds it; cash is asked nothing at all. So each
 * kind carries what it wants rather than being asked about in a run of tests
 * scattered through the screens.
 * <p>
 * A credit card is also a card, and an electronic purse is neither: the flags
 * are not exclusive, and are read as questions - does this one have an issuer,
 * has it a number worth keeping.
 */
public enum AccountType implements EntityEnum {

    CASH(R.string.account_type_cash, R.drawable.account_type_cash,
            false, false, false, false, false),
    BANK(R.string.account_type_bank, R.drawable.account_type_bank,
            true, false, false, false, false),
    /**
     * Money set aside and earning something, kept apart from the current
     * account it usually sits beside so the two can be told apart at a glance.
     */
    SAVINGS(R.string.account_type_savings, R.drawable.account_type_savings,
            true, false, false, false, false),
    DEBIT_CARD(R.string.account_type_debit_card, R.drawable.account_type_debit_card,
            true, true, true, false, false),
    CREDIT_CARD(R.string.account_type_credit_card, R.drawable.account_type_credit_card,
            true, true, true, true, false),
    ELECTRONIC(R.string.account_type_electronic, R.drawable.account_type_electronic,
            false, false, false, false, true),
    /** Securities: shares, funds, bonds. These used to fall under ASSET. */
    INVESTMENT(R.string.account_type_investment, R.drawable.account_type_investment,
            true, false, false, false, false),
    PENSION(R.string.account_type_pension, R.drawable.account_type_pension,
            true, false, false, false, false),
    /** Borrowed money with a schedule, as against the general LIABILITY. */
    LOAN(R.string.account_type_loan, R.drawable.account_type_loan,
            true, false, false, false, false),
    ASSET(R.string.account_type_asset, R.drawable.account_type_asset,
            false, false, false, false, false),
    LIABILITY(R.string.account_type_liability, R.drawable.account_type_liability,
            false, false, false, false, false),
    OTHER(R.string.account_type_other, R.drawable.account_type_other,
            false, false, false, false, false);

    public final int titleId;
    public final int iconId;

    /** Worth asking which bank or which card scheme stands behind it. */
    public final boolean hasIssuer;
    /** Worth keeping the last figures of its number, to tell two apart. */
    public final boolean hasNumber;
    public final boolean isCard;
    /** A card whose balance is money owed rather than money held. */
    public final boolean isCreditCard;
    /** A purse held by a service rather than by a bank. */
    public final boolean isElectronic;

    AccountType(int titleId, int iconId, boolean hasIssuer, boolean hasNumber,
                boolean isCard, boolean isCreditCard, boolean isElectronic) {
        this.titleId = titleId;
        this.iconId = iconId;
        this.hasIssuer = hasIssuer;
        this.hasNumber = hasNumber;
        this.isCard = isCard;
        this.isCreditCard = isCreditCard;
        this.isElectronic = isElectronic;
    }

    @Override
    public int getTitleId() {
        return titleId;
    }

    @Override
    public int getIconId() {
        return iconId;
    }
}
