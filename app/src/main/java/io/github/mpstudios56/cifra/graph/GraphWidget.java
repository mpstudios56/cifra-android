package io.github.mpstudios56.cifra.graph;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import io.github.mpstudios56.cifra.R;

/**
 * One line of a report: what it is, how much, and a bar for how much of it.
 * <p>
 * Drawn the way the summary screen draws where the money went - the figure
 * right-aligned on the same line as the name, and under it a rounded bar over a
 * faint track, so a column of these reads as a column and not as a ragged edge.
 * <p>
 * The bar is measured against the largest amount in the whole report, not
 * against its own row, which is the only way the lengths mean anything when
 * compared with each other.
 */
public class GraphWidget extends View {

	private static final int TRACK = 0x22FFFFFF;
	private static final int ZERO = 0x66FFFFFF;
	/** Amber rather than red: a report is not a warning, it is a statement. */
	private static final int OUT_BAR = 0xFFE9A742;
	private static final int IN_BAR = 0xFF3FA96F;

	private final int positiveColor;
	private final int negativeColor;

	private final GraphUnit unit;
	private final long maxAmount;
	private final long maxAmountWidth;

	private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final float density;

	public GraphWidget(Context context, GraphUnit unit, long maxAmount, long maxAmountWidth) {
		super(context);
		Resources r = context.getResources();
		positiveColor = r.getColor(R.color.positive_amount);
		negativeColor = r.getColor(R.color.negative_amount);
		density = r.getDisplayMetrics().density;
		this.unit = unit;
		this.maxAmount = maxAmount;
		this.maxAmountWidth = maxAmountWidth;
		barPaint.setStyle(Paint.Style.FILL);
	}

	private int dp(float value) {
		return Math.round(value * density);
	}

	@Override
	protected void onDraw(Canvas canvas) {
		GraphStyle style = unit.style;
		int left = getPaddingLeft() + style.indent;
		int right = getWidth() - getPaddingRight();
		int y = getPaddingTop();

		int barHeight = dp(8);
		int radius = barHeight / 2;
		int gapAfterName = dp(7);
		int gapAfterBar = dp(14);
		// The figures live in a column of their own on the right, so they line up
		// with each other instead of trailing off the end of their own bar.
		int amountColumn = (int) maxAmountWidth + dp(10);
		int trackRight = right - amountColumn;

		style.namePaint.setTextAlign(Paint.Align.LEFT);
		canvas.drawText(unit.name, left, y + style.nameHeight, style.namePaint);

		boolean firstAmountOnNameLine = unit.size() == 1;
		if (!firstAmountOnNameLine) {
			y += style.nameHeight + gapAfterName;
		}

		style.amountPaint.setTextAlign(Paint.Align.RIGHT);
		for (Amount a : unit) {
			long amount = a.amount;
			int colour = amount == 0 ? ZERO : (amount > 0 ? positiveColor : negativeColor);
			style.amountPaint.setColor(colour);
			canvas.drawText(a.getAmountText(), right,
					y + style.nameHeight, style.amountPaint);
			if (firstAmountOnNameLine) {
				y += style.nameHeight + gapAfterName;
				firstAmountOnNameLine = false;
			} else {
				y += style.nameHeight + dp(5);
			}

			barPaint.setColor(TRACK);
			canvas.drawRoundRect(new RectF(left, y, trackRight, y + barHeight),
					radius, radius, barPaint);

			if (maxAmount > 0) {
				float share = Math.min(1f, Math.abs(amount) / (float) maxAmount);
				float width = share * (trackRight - left);
				if (width > 0) {
					// Never thinner than the bar is tall, or a small figure draws a
					// sliver that reads as nothing at all.
					width = Math.max(width, barHeight);
					barPaint.setColor(amount == 0 ? ZERO : (amount > 0 ? IN_BAR : OUT_BAR));
					canvas.drawRoundRect(new RectF(left, y, left + width, y + barHeight),
							radius, radius, barPaint);
				}
			}
			y += barHeight + gapAfterBar;
		}
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		GraphStyle style = unit.style;
		int specWidth = MeasureSpec.getSize(widthMeasureSpec);
		int rows = Math.max(1, unit.size());
		int h = style.nameHeight + dp(7);
		if (unit.size() > 1) {
			h += style.nameHeight + dp(7);
		}
		h += rows * (dp(8) + dp(14));
		if (unit.size() > 1) {
			h += (rows - 1) * (style.nameHeight + dp(5));
		}
		setMeasuredDimension(specWidth, getPaddingTop() + h + getPaddingBottom());
	}

}
