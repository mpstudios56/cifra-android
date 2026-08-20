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
package io.github.mpstudios56.cifra.report;

import android.content.Context;
import android.content.Intent;

import io.github.mpstudios56.cifra.activity.ReportActivity;
import io.github.mpstudios56.cifra.blotter.BlotterFilter;
import io.github.mpstudios56.cifra.activity.ReportsListFragment;
import io.github.mpstudios56.cifra.filter.Criterion;
import io.github.mpstudios56.cifra.filter.WhereFilter;
import io.github.mpstudios56.cifra.db.DatabaseAdapter;
import io.github.mpstudios56.cifra.model.Category;
import io.github.mpstudios56.cifra.model.Currency;

import static io.github.mpstudios56.cifra.db.DatabaseHelper.V_REPORT_CATEGORY;

public class CategoryReport extends Report {
	
	public CategoryReport(Context context, Currency currency) {
		super(ReportType.BY_CATEGORY, context, currency, false);
	}

	@Override
	public ReportData getReport(DatabaseAdapter db, WhereFilter filter) {
        cleanupFilter(filter);
		filter.eq("parent_id", "0");
		return queryReport(db, V_REPORT_CATEGORY, filter);
	}

	@Override
	public Intent createActivityIntent(Context context, DatabaseAdapter db, WhereFilter parentFilter, long id) {
        WhereFilter filter = createFilterForSubCategory(db, parentFilter, id);
		Intent intent = new Intent(context, ReportActivity.class);
		filter.toIntent(intent);
		intent.putExtra(ReportsListFragment.EXTRA_REPORT_TYPE, ReportType.BY_SUB_CATEGORY.name());
        intent.putExtra(ReportActivity.FILTER_INCOME_EXPENSE, incomeExpense.name());
		return intent;
	}

    public WhereFilter createFilterForSubCategory(DatabaseAdapter db, WhereFilter parentFilter, long id) {
        WhereFilter filter = WhereFilter.copyOf(parentFilter);
		filter.remove("left");
		filter.remove("right");
        Category category = db.getCategory(id);
        filter.put(Criterion.gte("left", String.valueOf(category.left)));
        filter.put(Criterion.lte("right", String.valueOf(category.right)));
        return filter;
    }

    @Override
	public Criterion getCriteriaForId(DatabaseAdapter db, long id) {
		Category c = db.getCategory(id);
		return Criterion.btw(BlotterFilter.CATEGORY_LEFT, String.valueOf(c.left), String.valueOf(c.right));
	}
}

