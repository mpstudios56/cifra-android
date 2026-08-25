package io.github.mpstudios56.cifra.model;

import java.util.HashMap;
import java.util.Map;

import io.github.mpstudios56.cifra.R;
import io.github.mpstudios56.cifra.utils.LocalizableEnum;

/**
 * The extra fields the app keeps for itself, alongside the ones a person makes.
 * <p>
 * Categories can be given fields of their own - a meter reading, a mileage -
 * and those are rows in a table, made and named by whoever wants them. These
 * are not: they mean something to the app itself, so they are written here and
 * carry numbers below zero, where no made field can reach.
 */
public enum SystemAttribute implements LocalizableEnum {

    /** How long a movement stays in the trash before it goes for good. */
    DELETE_AFTER_EXPIRED(-1, R.string.system_attribute_delete_after_expired);

    /**
     * Found by number rather than by walking the list.
     * <p>
     * What is stored on a movement is the number; the lookup happens once per
     * row while a list is being drawn, and a list can be long.
     */
    private static final Map<Long, SystemAttribute> BY_ID = new HashMap<>();

    static {
        for (SystemAttribute attribute : values()) {
            BY_ID.put(attribute.id, attribute);
        }
    }

    public final long id;
    public final int titleId;

    SystemAttribute(long id, int titleId) {
        this.id = id;
        this.titleId = titleId;
    }

    @Override
    public int getTitleId() {
        return titleId;
    }

    /** @return null when the number belongs to a field somebody made */
    public static SystemAttribute forId(long attributeId) {
        return BY_ID.get(attributeId);
    }
}
