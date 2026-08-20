package io.github.mpstudios56.cifra.bus;

/**
 * Take me to that tab.
 * <p>
 * There was one of these for the menu alone, written when the menu was the only
 * place anything wanted to send somebody. This one carries the name of the tab.
 */
public class SwitchToTab {

    public final String tag;

    public SwitchToTab(String tag) {
        this.tag = tag;
    }
}
