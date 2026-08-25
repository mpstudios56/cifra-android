package io.github.mpstudios56.cifra.model;

import io.github.mpstudios56.cifra.R;
import io.github.mpstudios56.cifra.utils.EntityEnum;

/**
 * Whose card it is, for the mark it wears in the list of accounts.
 * <p>
 * Nothing here changes how anything is counted: it is a way of telling one card
 * from another at a glance, in a list where three of them may be the same bank.
 * <p>
 * A few of these have no mark of their own. Their logos exist only as pictures
 * built from gradients, and one of those enlarged to the size of a row reads
 * worse than the plain card does, so they wear the plain card.
 */
public enum CardIssuer implements EntityEnum {

    DEFAULT(R.string.card_issuer_default, R.drawable.account_type_card_default),
    VISA(R.string.card_issuer_visa, R.drawable.account_type_card_visa),
    VISA_ELECTRON(R.string.card_issuer_electron, R.drawable.account_type_card_visa_electron),
    MASTERCARD(R.string.card_issuer_mastercard, R.drawable.account_type_card_mastercard),
    MAESTRO(R.string.card_issuer_maestro, R.drawable.account_type_card_maestro),
    CIRRUS(R.string.card_issuer_cirrus, R.drawable.account_type_card_cirrus),
    AMEX(R.string.card_issuer_amex, R.drawable.account_type_card_amex),
    JCB(R.string.card_issuer_jcb, R.drawable.account_type_card_default),
    DINERS(R.string.card_issuer_diners, R.drawable.account_type_card_diners),
    DISCOVER(R.string.card_issuer_discover, R.drawable.account_type_card_default),
    MIR(R.string.card_issuer_mir, R.drawable.account_type_card_mir),
    NETS(R.string.card_issuer_nets, R.drawable.account_type_card_nets),
    UNIONPAY(R.string.card_issuer_unionpay, R.drawable.account_type_card_unionpay),
    RUPAY(R.string.card_issuer_rupay, R.drawable.account_type_card_rupay);

    public final int titleId;
    public final int iconId;

    CardIssuer(int titleId, int iconId) {
        this.titleId = titleId;
        this.iconId = iconId;
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
