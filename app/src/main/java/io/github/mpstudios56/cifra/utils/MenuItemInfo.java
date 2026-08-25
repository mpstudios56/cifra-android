package io.github.mpstudios56.cifra.utils;

/**
 * One line of a menu, before there is a menu to put it in.
 * <p>
 * Screens say what they want offered - the number that will come back when it
 * is chosen, the words, and a mark - and whoever builds the menu turns these
 * into rows. It keeps the deciding of what to offer away from the drawing of
 * it, so a screen can say "and this one is greyed out today" without touching a
 * view.
 */
public class MenuItemInfo {

    /** What comes back when this line is chosen. */
    public final int menuId;
    public int titleId;
    public boolean enabled = true;
    /** Optional: the symbol shown beside the words. 0 leaves the space empty. */
    public int iconId;

    public MenuItemInfo(int menuId, int titleId) {
        this.menuId = menuId;
        this.titleId = titleId;
    }

    public MenuItemInfo(int menuId, int titleId, int iconId) {
        this(menuId, titleId);
        this.iconId = iconId;
    }
}
