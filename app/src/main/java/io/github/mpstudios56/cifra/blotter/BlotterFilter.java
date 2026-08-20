package io.github.mpstudios56.cifra.blotter;

import io.github.mpstudios56.cifra.db.DatabaseHelper.BlotterColumns;

/**
 * The names by which a filter asks its questions of the movements.
 * <p>
 * Each is the name of a column in the view the list reads from, written once
 * here so that a filter, a screen and a query all say the same word. The few
 * that are not column names - the budget, a split - are the questions the app
 * answers for itself.
 */
public interface BlotterFilter {

    // Which account, and in which currency.
    String FROM_ACCOUNT_ID = BlotterColumns.from_account_id.name();
    String FROM_ACCOUNT_CURRENCY_ID = BlotterColumns.from_account_currency_id.name();
    String ORIGINAL_CURRENCY_ID = BlotterColumns.original_currency_id.name();

    // What it was for.
    String CATEGORY_ID = BlotterColumns.category_id.name();
    String CATEGORY_LEFT = BlotterColumns.category_left.name();
    String CATEGORY_NAME = BlotterColumns.category_title.name();
    String PROJECT_ID = BlotterColumns.project_id.name();
    String LOCATION_ID = BlotterColumns.location_id.name();
    String PAYEE = BlotterColumns.payee.name();
    String PAYEE_ID = BlotterColumns.payee_id.name();
    String NOTE = BlotterColumns.note.name();

    // The same things by name rather than by number, for the search box, which
    // has words to go on and not identifiers.
    String PROJECT_NAME = BlotterColumns.project.name();
    String LOCATION_NAME = BlotterColumns.location.name();
    String ACCOUNT_NAME = BlotterColumns.from_account_title.name();
    String TEMPLATE_NAME = BlotterColumns.template_name.name();

    // When, what kind, and where it stands.
    String DATETIME = BlotterColumns.datetime.name();
    String IS_TEMPLATE = BlotterColumns.is_template.name();
    String PARENT_ID = BlotterColumns.parent_id.name();
    String STATUS = BlotterColumns.status.name();

    // How much.
    String FROM_AMOUNT = BlotterColumns.from_amount.name();
    String ORIGINAL_FROM_AMOUNT = BlotterColumns.original_from_amount.name();

    // Not columns: questions the app answers on its own.
    String BUDGET_ID = "budget_id";
    String SPLIT = "split";

    // The orders a list can be read in.
    String SORT_NEWER_TO_OLDER = BlotterColumns.datetime + " desc";
    String SORT_OLDER_TO_NEWER = BlotterColumns.datetime + " asc";

    /**
     * By the order they were written down rather than by their date: two
     * movements of the same minute keep the order they were entered in.
     */
    String SORT_NEWER_TO_OLDER_BY_ID = "_id desc";
    String SORT_OLDER_TO_NEWER_BY_ID = "_id asc";

    String SORT_BY_TEMPLATE_NAME = BlotterColumns.template_name + " asc";
    String SORY_BY_ACCOUNT_NAME = BlotterColumns.from_account_title + " asc";
    String SORT_BY_CATEGORY = BlotterColumns.category_title + " asc";
    String SORT_BY_PROJECT = BlotterColumns.project + " asc, "
            + BlotterColumns.category_title + " asc";
}
