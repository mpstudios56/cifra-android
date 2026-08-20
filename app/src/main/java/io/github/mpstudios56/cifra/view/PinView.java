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
package io.github.mpstudios56.cifra.view;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import android.os.Vibrator;
import io.github.mpstudios56.cifra.R;
import io.github.mpstudios56.cifra.utils.Base64Coder;
import android.content.Context;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.CycleInterpolator;
import android.view.animation.TranslateAnimation;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ViewSwitcher;
import io.github.mpstudios56.cifra.utils.MyPreferences;

public class PinView implements OnClickListener {

	private static final int[] buttons = { R.id.b0, R.id.b1, R.id.b2, R.id.b3,
		R.id.b4, R.id.b5, R.id.b6, R.id.b7, R.id.b8, R.id.b9, R.id.bHelp,
		R.id.bClear};

	/**
	 * Six, when choosing a new one. Four digits is ten thousand combinations,
	 * which is an afternoon for anyone holding the phone; six is a hundred times
	 * that. It applies only to choosing: a PIN already in use still unlocks,
	 * whatever length it was set to.
	 */
	private static final int MIN_DIGITS = 6;
	private static final int MAX_DIGITS = 12;

	public static interface PinListener {
		void onConfirm(String pinBase64);
		void onSuccess(String pinBase64);
	}

    private final Context context;
	private final PinListener listener;
	private final View v;	
	private final ViewSwitcher switcher;
	private final MessageDigest digest;
    private final Vibrator vibrator;
	
	private TextView result;
	private TextView hint;
	private String pin1;
	private String pin2;
	private boolean confirmPin;
	/** Choosing a new PIN, rather than being asked for the one already set. */
	private final boolean choosing;

	public PinView(Context context, PinListener listener, int layoutId) {
		this(context, listener, null, layoutId);
	}
	
	public PinView(Context context, PinListener listener, String pin, int layoutId) {
        this.context = context;
		this.listener = listener;
		this.confirmPin = pin == null;
		this.choosing = pin == null;
		this.pin1 = pin;
		LayoutInflater layoutInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		v = layoutInflater.inflate(layoutId, null);
		for (int id : buttons) {
			v.findViewById(id).setOnClickListener(this);
		}
		result = (TextView)v.findViewById(R.id.result1);
		hint = (TextView)v.findViewById(R.id.TextView01);
		if (hint != null) {
			hint.setText(choosing ? context.getString(R.string.pin_choose, MIN_DIGITS) : "");
		}
		switcher = (ViewSwitcher)v.findViewById(R.id.switcher);
		switcher.setInAnimation(inFromRightAnimation());
		switcher.setOutAnimation(outToLeftAnimation());		
		try {
			digest = MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
        this.vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
	}
	
	public View getView() {
		return v;
	}
	
	@Override
	public void onClick(View v) {
		Button b = (Button)v;
		char c = b.getText().charAt(0);
		if (vibrator != null && MyPreferences.isPinHapticFeedbackEnabled()) {
			v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
		}
		switch (c) {
		case 'O':
			nextStep();
			break;
		case 'C':
			result.setText("");
			break;
		default:
            String text = result.getText().toString();
			if (text.length() < MAX_DIGITS) {
				result.setText(text+String.valueOf(c));
			}
			break;
		}
	}

	private void nextStep() {
		if (confirmPin) {
			if (result.getText().length() < MIN_DIGITS) {
				if (hint != null) {
					hint.setText(context.getString(R.string.pin_too_short, MIN_DIGITS));
				}
				result.startAnimation(shakeAnimation());
				return;
			}
			pin1 = pinBase64(result.getText().toString());
			result = (TextView)v.findViewById(R.id.result2);
			confirmPin = false;
			if (hint != null) {
				hint.setText(context.getString(R.string.pin_repeat));
			}
			switcher.showNext();
			listener.onConfirm(pin1);
		} else {
			pin2 = pinBase64(result.getText().toString());
			if (pin1.equals(pin2)) {
				listener.onSuccess(pin2);
			} else {
				// Only while choosing. On the lock screen a wrong PIN is told nothing
				// beyond the shake, which is the point of a lock screen.
				if (choosing && hint != null) {
					hint.setText(context.getString(R.string.pin_mismatch));
				}
				result.startAnimation(shakeAnimation());
			}
		}
	}
	
	private String pinBase64(String pin) {
		byte[] a = digest.digest(pin.getBytes());
        return new String(Base64Coder.encode(a));
	}

	private Animation inFromRightAnimation() {
		Animation inFromRight = new TranslateAnimation(
				Animation.RELATIVE_TO_PARENT, +1.0f,
				Animation.RELATIVE_TO_PARENT, 0.0f,
				Animation.RELATIVE_TO_PARENT, 0.0f,
				Animation.RELATIVE_TO_PARENT, 0.0f);
		inFromRight.setDuration(300);
		inFromRight.setInterpolator(new AccelerateInterpolator());
		return inFromRight;
	}

	private Animation outToLeftAnimation() {
		Animation outtoLeft = new TranslateAnimation(
				Animation.RELATIVE_TO_PARENT, 0.0f,
				Animation.RELATIVE_TO_PARENT, -1.0f,
				Animation.RELATIVE_TO_PARENT, 0.0f,
				Animation.RELATIVE_TO_PARENT, 0.0f);
		outtoLeft.setDuration(300);
		outtoLeft.setInterpolator(new AccelerateInterpolator());
		return outtoLeft;
	}
	
	private Animation shakeAnimation() {
		Animation anim = new TranslateAnimation(0.0f, 10.0f, 0.0f, 0.0f);
		anim.setDuration(300);
		anim.setInterpolator(new CycleInterpolator(5));
		return anim;
	}

}
