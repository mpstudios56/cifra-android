package io.github.mpstudios56.cifra.model;

import io.github.mpstudios56.cifra.R;
import io.github.mpstudios56.cifra.utils.EntityEnum;

/**
 * The kinds of purse that hold money without a bank behind them.
 * <p>
 * An account marked as electronic is asked which of these it is, and the answer
 * only decides the mark it wears in the list: money in a wallet is money, and
 * nothing here changes how it is counted.
 */
public enum ElectronicPaymentType implements EntityEnum {

    PAYPAL(R.string.electronic_type_paypal, R.drawable.electronic_type_paypal),
    REVOLUT(R.string.electronic_type_revolut, R.drawable.electronic_type_revolut),
    COINBASE(R.string.electronic_type_coinbase, R.drawable.electronic_type_coinbase),
    AMAZON(R.string.electronic_type_amazon, R.drawable.electronic_type_amazon),
    EBAY(R.string.electronic_type_ebay, R.drawable.electronic_type_ebay),
    GOOGLE_WALLET(R.string.electronic_type_google_wallet, R.drawable.electronic_type_google_wallet),
    BITCOIN(R.string.electronic_type_bitcoin, R.drawable.electronic_type_bitcoin),
    ALIPAY(R.string.electronic_type_alipay, R.drawable.electronic_type_alipay),
    WEB_MONEY(R.string.electronic_type_web_money, R.drawable.electronic_type_webmoney),
    YANDEX_MONEY(R.string.electronic_type_yandex_money, R.drawable.electronic_type_yandex_money);

    public final int titleId;
    public final int iconId;

    ElectronicPaymentType(int titleId, int iconId) {
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
