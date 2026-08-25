package io.github.mpstudios56.cifra.model;

import static io.github.mpstudios56.cifra.db.DatabaseHelper.PAYEE_TABLE;
import static io.github.mpstudios56.cifra.orb.EntityManager.DEF_SORT_COL;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

/**
 * Who the money went to, or came from: a shop, a landlord, an employer.
 * <p>
 * It remembers the category it was last written down with, so that choosing the
 * same shop again offers the same category without being asked - most payees
 * mean one kind of spending, and the ones that do not are simply changed.
 */
@Entity
@Table(name = PAYEE_TABLE)
public class Payee extends MyEntity implements SortableEntity {

    /** Stands for a movement written without a payee. */
    public static final Payee EMPTY = new Payee();

    static {
        EMPTY.id = 0;
        EMPTY.title = "No payee";
    }

    @Column(name = "last_category_id")
    public long lastCategoryId;

    /** Where it sits in the list, when the list is arranged by hand. */
    @Column(name = DEF_SORT_COL)
    public long sortOrder;

    @Override
    public long getSortOrder() {
        return sortOrder;
    }
}
