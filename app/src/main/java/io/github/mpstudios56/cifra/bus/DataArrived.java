package io.github.mpstudios56.cifra.bus;

/**
 * Something came in from the other phone, and the screens are now out of date.
 * <p>
 * Every tab used to redraw itself only when it was opened or reopened, which is
 * right when the only thing that changes the figures is the person holding the
 * phone. It stopped being right the moment a round of sharing could add a
 * hundred movements while the summary sat on screen: the account list was one
 * total behind, the balances came right only after walking into an account and
 * out again, and there is no way to tell a wrong figure from a late one.
 */
public class DataArrived {
}
