package io.github.mpstudios56.cifra.model;

/**
 * A heading written into the list of accounts: Casa, Lavoro, Risparmi.
 * <p>
 * It holds no money and takes no part in any total. What it does is gather
 * accounts under one word, so a list of a dozen reads as three groups of four,
 * and let those be folded away when they are not what one came to look at.
 * <p>
 * The accounts it gathers are the ones ticked for it, wherever they sit in the
 * list: a current account, a card and a wallet can be brought together with
 * three others in between that belong elsewhere. Which they are is written on
 * the accounts themselves.
 * <p>
 * The heading has no place of its own in the order. It is drawn just above the
 * first of its accounts, so it follows them however the list is sorted and
 * never has to be dragged into position.
 */
public class AccountSeparator {

    /** Not yet saved. */
    public static final long NEW = -1;

    public long id = NEW;
    public String title;

    /**
     * The account it is drawn above, or zero for "wherever the first of mine
     * falls".
     * <p>
     * A group follows its own accounts by default, which is what one wants
     * nine times out of ten. This is for the tenth: a heading that has to stand
     * at a particular point of the list whatever the order does.
     */
    public long beforeAccountId;

    /**
     * Whether its accounts are folded away.
     * <p>
     * Kept with the heading itself, so it survives the list being rearranged
     * and is remembered from one opening to the next.
     */
    public boolean folded;

    /**
     * How many accounts are folded away under it at the moment.
     * <p>
     * Not written down anywhere - worked out each time the list is built, and
     * here only so the heading can say how many it is hiding.
     */
    public transient int hidden;

    public AccountSeparator() {
    }

    public AccountSeparator(String title) {
        this.title = title;
    }
}
