package io.github.mpstudios56.cifra.model;

import javax.persistence.Column;
import javax.persistence.Transient;

/**
 * A category, and the categories under it.
 * <p>
 * Categories are a tree - Casa holds Affitto and Bollette - but a tree is
 * awkward to ask questions of in a database: finding everything under Casa
 * would mean walking down it a step at a time. So each one also carries two
 * numbers, a left and a right, given by walking the whole tree once: everything
 * beneath Casa has a left between Casa's two. "All of Casa" then becomes one
 * question with two numbers in it, however deep the tree goes.
 * <p>
 * The price is that adding a category means renumbering its neighbours, which
 * is why the numbers are given out by the database and never written by hand.
 * <p>
 * Whether it is money out or money in belongs to the whole branch: a category
 * under an expense is an expense, and {@link #addChild} sets it so.
 *
 * @param <T> the kind of category held, so that a tree of one kind cannot take
 *            a branch of another
 */
public class CategoryEntity<T extends CategoryEntity<T>> extends MyEntity {

    public static final int TYPE_EXPENSE = 0;
    public static final int TYPE_INCOME = 1;

    /** Not a column: the tree is rebuilt from the two numbers when read. */
    @Transient
    public T parent;

    @Column(name = "left")
    public int left = 1;

    @Column(name = "right")
    public int right = 2;

    @Column(name = "type")
    public int type = TYPE_EXPENSE;

    @Transient
    public CategoryTree<T> children;

    public long getParentId() {
        return parent != null ? parent.id : 0;
    }

    @SuppressWarnings("unchecked")
    public void addChild(T category) {
        if (children == null) {
            children = new CategoryTree<>();
        }
        category.parent = (T) this;
        // Out or in is decided by the branch, not by the leaf: an expense
        // cannot hold an income under it without the totals ceasing to mean
        // anything.
        category.type = this.type;
        children.add(category);
    }

    public void removeChild(T category) {
        if (children != null) {
            children.remove(category);
        }
    }

    public boolean hasChildren() {
        return children != null && !children.isEmpty();
    }

    public boolean isExpense() {
        return type == TYPE_EXPENSE;
    }

    public boolean isIncome() {
        return type == TYPE_INCOME;
    }

    public void makeThisCategoryIncome() {
        this.type = TYPE_INCOME;
    }

    public void makeThisCategoryExpense() {
        this.type = TYPE_EXPENSE;
    }
}
