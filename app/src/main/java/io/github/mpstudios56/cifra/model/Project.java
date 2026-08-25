package io.github.mpstudios56.cifra.model;

import static io.github.mpstudios56.cifra.db.DatabaseHelper.PROJECT_TABLE;
import static io.github.mpstudios56.cifra.orb.EntityManager.DEF_SORT_COL;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

/**
 * What a movement belonged to, across categories and accounts: a holiday, a
 * house being done up, a job of work.
 * <p>
 * The category says what a thing was; the project says what it was for. A
 * flight, a hotel and three dinners belong to different categories and to one
 * holiday.
 */
@Entity
@Table(name = PROJECT_TABLE)
public class Project extends MyEntity implements SortableEntity {

    /**
     * Belonging to no project at all.
     * <p>
     * Kept as a project with a number of its own rather than as an empty field,
     * so that a movement always has one to point at and the lists have
     * something to offer as the first choice.
     */
    public static final int NO_PROJECT_ID = 0;

    public static Project noProject() {
        Project project = new Project();
        project.id = NO_PROJECT_ID;
        project.title = "<NO_PROJECT>";
        project.isActive = true;
        return project;
    }

    /** Where it sits in the list, when the list is arranged by hand. */
    @Column(name = DEF_SORT_COL)
    public long sortOrder;

    @Override
    public long getSortOrder() {
        return sortOrder;
    }
}
