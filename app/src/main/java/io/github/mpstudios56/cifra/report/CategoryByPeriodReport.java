/**
 * 
 */
package io.github.mpstudios56.cifra.report;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import io.github.mpstudios56.cifra.R;
import io.github.mpstudios56.cifra.blotter.BlotterFilter;
import io.github.mpstudios56.cifra.db.DatabaseAdapter;
import io.github.mpstudios56.cifra.db.DatabaseHelper;
import io.github.mpstudios56.cifra.db.DatabaseHelper.CategoryColumns;
import io.github.mpstudios56.cifra.db.DatabaseHelper.TransactionColumns;
import io.github.mpstudios56.cifra.filter.Criterion;
import io.github.mpstudios56.cifra.graph.Report2DChart;
import io.github.mpstudios56.cifra.graph.Report2DPoint;
import io.github.mpstudios56.cifra.model.Category;
import io.github.mpstudios56.cifra.model.Currency;
import io.github.mpstudios56.cifra.model.PeriodValue;
import io.github.mpstudios56.cifra.model.ReportDataByPeriod;
import io.github.mpstudios56.cifra.utils.MyPreferences;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/**
 * 2D Chart Report to display monthly results by Categories.
 * @author Abdsandryk
 */
public class CategoryByPeriodReport extends Report2DChart {
	
	public CategoryByPeriodReport(Context context, DatabaseAdapter db, Calendar startPeriod, int periodLength, Currency currency, MyPreferences.ReportAggregateUnit aggregateUnit) {
		super(context, db, startPeriod, periodLength, currency, aggregateUnit);
	}

	@Override
	public int getFilterItemTypeName() {
		return R.string.category;
	}

	@Override
	public String getFilterName() {
		if (filterTitles.size()>0) {
			return filterTitles.get(currentFilterOrder);
		} else {
			// no category
			return context.getString(R.string.no_category);
		}
	}

	@Override
	public List<Report2DChart> getChildrenCharts() {
		return null;
	}

	@Override
	public boolean isRoot() {
		return false;
	}

	@Override
	protected void createFilter() {
		columnFilter = TransactionColumns.category_id.name();
		boolean includeSubCategories = MyPreferences.includeSubCategoriesInReport();
		boolean includeNoCategory = MyPreferences.includeNoFilterInReport();
		filterIds = new ArrayList<>();
		filterTitles = new ArrayList<>();
		currentFilterOrder = 0;
		List<Category> categories = em.getCategoriesList(includeNoCategory);
		for (Category c : categories) {
			if (includeSubCategories) {
				filterIds.add(c.id);
				filterTitles.add(c.getTitle());
			} else {
				// do not include sub categories
				if (c.level == 1) {
					// filter root categories only
					filterIds.add(c.id);
					filterTitles.add(c.getTitle());
				}
			}
		}
	}

	/**
	 * Request data and fill data objects (list of points, max, min, etc.)
	 */
	@Override
	protected void build() {
		boolean addSubs = MyPreferences.addSubCategoriesToSum();
		if (addSubs) {
			SQLiteDatabase db = em.db();
			Cursor cursor = null;
			try {
				long categoryId = filterIds.get(currentFilterOrder);
				Category parent = em.getCategory(categoryId);
				String where = CategoryColumns.left+" BETWEEN ? AND ?";
				String[] pars = new String[]{String.valueOf(parent.left), String.valueOf(parent.right)};
				cursor = db.query(DatabaseHelper.CATEGORY_TABLE, new String[]{CategoryColumns._id.name()}, where, pars, null, null, null);
				long[] categories = new long[cursor.getCount()+1];
				int i=0;
				while (cursor.moveToNext()) {
					categories[i] = (int)cursor.getInt(0);
					i++;
				}
				categories[i] = filterIds.get(currentFilterOrder);
				data = new ReportDataByPeriod(context, startPeriod, periodLength, currency, columnFilter, categories, em, aggregateUnit);
			} finally {
				if (cursor!=null) cursor.close();
			}
		} else {
			// only root category
			data = new ReportDataByPeriod(context, startPeriod, periodLength, currency, columnFilter, filterIds.get(currentFilterOrder), em, aggregateUnit);
		}
		
		points = new ArrayList<Report2DPoint>();
		List<PeriodValue> pvs = data.getPeriodValues();

        for (PeriodValue pv : pvs) {
            points.add(new Report2DPoint(pv));
        }
	}

	@Override
	public Criterion getCriteria() {
		boolean addSubs = MyPreferences.addSubCategoriesToSum();
		if (addSubs) {
			long categoryId = filterIds.get(currentFilterOrder);
			Category parent = em.getCategory(categoryId);
			return Criterion.btw(BlotterFilter.CATEGORY_LEFT, String.valueOf(parent.left), String.valueOf(parent.right));
		} else {
			return Criterion.eq(columnFilter, filterIds.get(currentFilterOrder).toString());
		}
	}

	@Override
	public String getNoFilterMessage(Context context) {
		return context.getString(R.string.report_no_category);
	}

}
