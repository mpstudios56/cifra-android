package io.github.mpstudios56.cifra.recur;

import android.widget.LinearLayout;

/**
 * One panel of the repetition screen.
 * <p>
 * Each way of repeating - every day, every week, on certain days of the month -
 * knows how to draw its own questions, how to write its answers down as a
 * single line of text, and how to read that line back. The screen holds several
 * of them and shows the one that matches what has been chosen.
 */
public interface RecurrenceView {

    /** Draws this panel's questions into the space given. */
    void createNodes(LinearLayout layout);

    /** The answers as one line, to be stored with the movement. */
    String stateToString();

    /** Puts the answers back on screen from a line stored earlier. */
    void stateFromString(String state);

    /** Whether what has been answered makes sense; complains on screen if not. */
    boolean validateState();
}
