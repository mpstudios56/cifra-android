package io.github.mpstudios56.cifra.model;

/**
 * Something that can be ticked in a list of many.
 * <p>
 * Payees, projects, places and categories are all offered the same way when a
 * filter asks for several at once, and the dialog that offers them does not
 * need to know which of them it is holding: a name to show, a number to give
 * back, and whether the tick is in.
 */
public interface MultiChoiceItem {

    long getId();

    String getTitle();

    boolean isChecked();

    void setChecked(boolean checked);
}
