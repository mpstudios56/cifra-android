/*******************************************************************************
 * Copyright (c) 2010 Denis Solonenko.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 *
 * Contributors:
 *     Denis Solonenko - initial API and implementation
 ******************************************************************************/
package tw.tib.financisto.widget;

import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.androidannotations.annotations.EFragment;
import org.androidannotations.annotations.FragmentArg;

import java.math.BigDecimal;

import tw.tib.financisto.R;
import tw.tib.financisto.model.Currency;
import tw.tib.financisto.utils.CurrencyCache;
import tw.tib.financisto.utils.Utils;

/**
 * Typing an amount on a movement.
 * <p>
 * It used to be a column of rolling wheels, one per digit, from the Android of
 * 2010: to write 12,50 somebody had to drag four columns into place, and on a
 * modern screen it read as a stack of numbers with no way in. Everybody types
 * an amount the way a till does - digits from the right, the comma looking
 * after itself - which is what the quick entry two taps away already does.
 * <p>
 * Same keys, same circles, same behaviour. Nothing else about the amount
 * changes: this hands back a plain figure exactly as the wheels did.
 */
@EFragment
public class QuickAmountInput extends DialogFragment {

    @FragmentArg
    protected long currencyId;
    @FragmentArg
    protected long amount;

    private AmountListener listener;
    private Currency currency;
    /** What has been typed, in the currency's smallest unit. */
    private long cents;
    private TextView display;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        return dialog;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             Bundle savedInstanceState) {
        currency = CurrencyCache.getCurrency(currencyId);
        cents = Math.abs(amount);

        View view = inflater.inflate(R.layout.amount_keypad, container, false);
        display = view.findViewById(R.id.keypad_amount);

        int[] keys = {R.id.k0, R.id.k1, R.id.k2, R.id.k3, R.id.k4,
                R.id.k5, R.id.k6, R.id.k7, R.id.k8, R.id.k9};
        for (int digit = 0; digit < keys.length; digit++) {
            final int value = digit;
            view.findViewById(keys[digit]).setOnClickListener(v -> type(value));
        }
        view.findViewById(R.id.kClear).setOnClickListener(v -> {
            cents = 0;
            show();
        });
        view.findViewById(R.id.kBack).setOnClickListener(v -> {
            cents = cents / 10;
            show();
        });
        view.findViewById(R.id.kCancel).setOnClickListener(v -> dismiss());
        view.findViewById(R.id.kOk).setOnClickListener(v -> {
            if (listener != null) {
                listener.onAmountChanged(figure().toPlainString());
            }
            dismiss();
        });

        show();
        return view;
    }

    /**
     * A digit typed at the right, the rest shifting up.
     * <p>
     * Stopped before the figure grows past what the column can hold: a
     * thirteen-digit amount is a finger held down, not an amount.
     */
    private void type(int digit) {
        if (cents > 99999999999L) {
            return;
        }
        cents = cents * 10 + digit;
        show();
    }

    private BigDecimal figure() {
        int scale = currency == null ? 2 : currency.getScale();
        return new BigDecimal(cents).movePointLeft(scale);
    }

    private void show() {
        display.setText(Utils.amountToStringPlain(currency, cents));
    }

    public void setListener(AmountListener listener) {
        this.listener = listener;
    }
}
