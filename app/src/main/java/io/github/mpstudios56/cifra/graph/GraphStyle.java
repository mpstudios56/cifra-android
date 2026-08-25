package io.github.mpstudios56.cifra.graph;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;

/**
 * The measurements and the brushes a report is drawn with.
 * <p>
 * Settled once, when the screen is built, and handed to every row: the letters
 * are all the same size, so measuring them once per row would be the same
 * answer arrived at over and over while somebody is scrolling.
 * <p>
 * Every measurement is given in the units that keep their apparent size across
 * phones, and turned into pixels here, against the density of the screen it
 * will actually be drawn on.
 */
public class GraphStyle {

    /** Gap above a bar. */
    public final int dy;
    /** Gap between a bar and the figure written on it. */
    public final int textDy;
    /** The whole height of one row. */
    public final int lineHeight;
    /** How far a row is pushed in - used to show a category inside another. */
    public final int indent;

    /** Height of a capital letter in the name, measured once. */
    public final int nameHeight;
    /** Height of a digit in the figure, measured once. */
    public final int amountHeight;

    public final Paint namePaint;
    public final Paint amountPaint;
    public final Paint linePaint;

    private GraphStyle(int dy, int textDy, int indent, int lineHeight,
                       int nameHeight, int amountHeight,
                       Paint namePaint, Paint amountPaint, Paint linePaint) {
        this.dy = dy;
        this.textDy = textDy;
        this.indent = indent;
        this.lineHeight = lineHeight;
        this.nameHeight = nameHeight;
        this.amountHeight = amountHeight;
        this.namePaint = namePaint;
        this.amountPaint = amountPaint;
        this.linePaint = linePaint;
    }

    /**
     * Collects the measurements, then works them all out at once.
     * <p>
     * They arrive a few at a time and in no fixed order, and none of them can
     * be turned into pixels until the screen is known, so nothing is decided
     * until {@link #build()}.
     */
    public static class Builder {

        private final Context context;

        private int dy = 2;
        private int textDy = 5;
        private int lineHeight = 30;
        private int nameTextSize = 14;
        private int amountTextSize = 12;
        private int indent = 0;

        public Builder(Context context) {
            this.context = context;
        }

        public Builder dy(int x) {
            this.dy = x;
            return this;
        }

        public Builder textDy(int x) {
            this.textDy = x;
            return this;
        }

        public Builder lineHeight(int x) {
            this.lineHeight = x;
            return this;
        }

        public Builder nameTextSize(int x) {
            this.nameTextSize = x;
            return this;
        }

        public Builder amountTextSize(int x) {
            this.amountTextSize = x;
            return this;
        }

        public Builder indent(int x) {
            this.indent = x;
            return this;
        }

        public GraphStyle build() {
            float density = context.getResources().getDisplayMetrics().density;
            Rect measured = new Rect();

            // The name: bold, written from the left edge of its row.
            Paint namePaint = new Paint();
            namePaint.setColor(Color.WHITE);
            namePaint.setAntiAlias(true);
            namePaint.setTextAlign(Paint.Align.LEFT);
            namePaint.setTextSize(inPixels(nameTextSize, density));
            namePaint.setTypeface(Typeface.DEFAULT_BOLD);
            // A capital A stands for the tallest letter a name can hold.
            namePaint.getTextBounds("A", 0, 1, measured);
            int nameHeight = measured.height();

            // The figure: lighter, centred on the bar it belongs to.
            Paint amountPaint = new Paint();
            amountPaint.setColor(Color.WHITE);
            amountPaint.setAntiAlias(true);
            amountPaint.setTextAlign(Paint.Align.CENTER);
            amountPaint.setTextSize(inPixels(amountTextSize, density));
            // Digits are all one height; an eight speaks for the rest.
            amountPaint.getTextBounds("8", 0, 1, measured);
            int amountHeight = measured.height();

            // The bars themselves, filled rather than outlined.
            Paint linePaint = new Paint();
            linePaint.setStyle(Paint.Style.FILL);

            return new GraphStyle(
                    inPixels(dy, density),
                    inPixels(textDy, density),
                    inPixels(indent, density),
                    inPixels(lineHeight, density),
                    nameHeight,
                    amountHeight,
                    namePaint,
                    amountPaint,
                    linePaint);
        }

        /** Rounded to the nearest pixel: half of one draws as a blurred edge. */
        private int inPixels(int scaled, float density) {
            return (int) (0.5f + density * scaled);
        }
    }
}
