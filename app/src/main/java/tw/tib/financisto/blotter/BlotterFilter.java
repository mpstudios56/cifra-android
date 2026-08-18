/*******************************************************************************
 * Copyright (c) 2010 Denis Solonenko.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 *
 * Contributors:
 *     Denis Solonenko - initial API and implementation
 ******************************************************************************/
package tw.tib.financisto.blotter;

import tw.tib.financisto.db.DatabaseHelper.BlotterColumns;

public interface BlotterFilter {

	String FROM_ACCOUNT_ID = BlotterColumns.from_account_id.name();
	String FROM_ACCOUNT_CURRENCY_ID = BlotterColumns.from_account_currency_id.name();
	String ORIGINAL_CURRENCY_ID = BlotterColumns.original_currency_id.name();
	String CATEGORY_ID = BlotterColumns.category_id.name();
	String CATEGORY_LEFT = BlotterColumns.category_left.name();
	String CATEGORY_NAME = BlotterColumns.category_title.name();
	String LOCATION_ID = BlotterColumns.location_id.name();
	String PROJECT_ID = BlotterColumns.project_id.name();
	String PAYEE = BlotterColumns.payee.name();
	String PAYEE_ID = BlotterColumns.payee_id.name();
	String NOTE = BlotterColumns.note.name();
	// Names rather than ids, for searching by what the user typed
	String PROJECT_NAME = BlotterColumns.project.name();
	String LOCATION_NAME = BlotterColumns.location.name();
	String ACCOUNT_NAME = BlotterColumns.from_account_title.name();
	String TEMPLATE_NAME = BlotterColumns.template_name.name();
	String DATETIME = BlotterColumns.datetime.name();
	String BUDGET_ID = "budget_id";
	String IS_TEMPLATE = BlotterColumns.is_template.name();
	String PARENT_ID = BlotterColumns.parent_id.name();
	String STATUS = BlotterColumns.status.name();
	String SPLIT = "split";
	String FROM_AMOUNT = BlotterColumns.from_amount.name();
	String ORIGINAL_FROM_AMOUNT = BlotterColumns.original_from_amount.name();

	String SORT_NEWER_TO_OLDER = BlotterColumns.datetime+" desc";
	String SORT_OLDER_TO_NEWER = BlotterColumns.datetime+" asc";

	String SORT_NEWER_TO_OLDER_BY_ID = "_id desc";
	String SORT_OLDER_TO_NEWER_BY_ID = "_id asc";

	String SORT_BY_TEMPLATE_NAME = BlotterColumns.template_name + " asc";
	String SORY_BY_ACCOUNT_NAME = BlotterColumns.from_account_title + " asc";

	String SORT_BY_CATEGORY = BlotterColumns.category_title + " asc";
	String SORT_BY_PROJECT = BlotterColumns.project + " asc, " + BlotterColumns.category_title + " asc";
}
