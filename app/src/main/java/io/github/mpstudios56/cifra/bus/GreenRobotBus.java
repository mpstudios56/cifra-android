package io.github.mpstudios56.cifra.bus;

import org.androidannotations.annotations.EBean;
import org.greenrobot.eventbus.EventBus;

/**
 * How one part of the app tells the others that something happened.
 * <p>
 * A screen says "the data changed" without knowing who is listening, and
 * whoever is on screen hears it and reads itself again. A single one of these
 * is shared by the whole app.
 * <p>
 * Some messages are kept rather than only announced: one sent while nobody was
 * listening is still waiting when the next screen registers, which is what
 * carries a change across a screen that had not opened yet.
 */
@EBean(scope = EBean.Scope.Singleton)
public class GreenRobotBus {

    public final EventBus bus = new EventBus();

    /** Says it once, to whoever is listening now. */
    public void post(Object event) {
        bus.post(event);
    }

    /** Says it and leaves it standing, for whoever listens next. */
    public void postSticky(Object event) {
        bus.postSticky(event);
    }

    /** Takes a standing message away, once it has been acted on. */
    public <T> T removeSticky(Class<T> eventClass) {
        return bus.removeStickyEvent(eventClass);
    }

    /** Starts listening, unless this one already is. */
    public void register(Object subscriber) {
        if (!bus.isRegistered(subscriber)) {
            bus.register(subscriber);
        }
    }

    public void unregister(Object subscriber) {
        bus.unregister(subscriber);
    }
}
