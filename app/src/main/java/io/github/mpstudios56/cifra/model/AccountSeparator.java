package io.github.mpstudios56.cifra.model;

/**
 * A heading written into the list of accounts: Casa, Lavoro, Risparmi.
 * <p>
 * It holds no money and takes no part in any total. What it does is gather the
 * accounts under it, so a list of a dozen reads as three groups of four, and
 * let those be folded away when they are not what one came to look at.
 * <p>
 * It has no place of its own in the order. It is fastened to an account and
 * drawn just above it; what belongs to it is whatever follows, as far as the
 * next heading. So it stays where it belongs however the list is sorted, and
 * never has to be dragged into position.
 */
public class AccountSeparator {

    /** Not yet saved. */
    public static final long NEW = -1;

    public long id = NEW;
    public String title;

    /** The account it stands above. */
    public long beforeAccountId;

    /**
     * Whether what is under it is folded away.
     * <p>
     * Kept with the heading itself, so it survives the list being rearranged
     * and is remembered from one opening to the next.
     */
    public boolean folded;

    /**
     * How many accounts are folded away under it at the moment.
     * <p>
     * Not written down anywhere - it is worked out each time the list is built,
     * and is here only so the heading can say how many it is hiding.
     */
    public transient int hidden;

    public AccountSeparator() {
    }

    public AccountSeparator(String title, long beforeAccountId) {
        this.title = title;
        this.beforeAccountId = beforeAccountId;
    }
}
