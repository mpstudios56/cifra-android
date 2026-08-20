package io.github.mpstudios56.cifra.report;

import java.sql.Array;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import io.github.mpstudios56.cifra.R;
import io.github.mpstudios56.cifra.db.DatabaseAdapter;
import io.github.mpstudios56.cifra.db.DatabaseHelper.TransactionColumns;
import io.github.mpstudios56.cifra.graph.Report2DChart;
import io.github.mpstudios56.cifra.model.Currency;
import io.github.mpstudios56.cifra.model.Project;
import io.github.mpstudios56.cifra.utils.MyPreferences;
import android.content.Context;

/**
 * 2D Chart Report to display monthly results by Projects.
 * @author Abdsandryk
 */
public class ProjectByPeriodReport extends Report2DChart {
	
	public ProjectByPeriodReport(Context context, DatabaseAdapter em, Calendar startPeriod, int periodLength, Currency currency, MyPreferences.ReportAggregateUnit aggregateUnit) {
		super(context, em, startPeriod, periodLength, currency, aggregateUnit);
	}

	@Override
	public int getFilterItemTypeName() {
		return R.string.project;
	}

	/* (non-Javadoc)
	 * @see io.github.mpstudios56.cifra.graph.ReportGraphic2D#getFilterName()
	 */
	@Override
	public String getFilterName() {
		if (filterTitles.size()>0) {
			return filterTitles.get(currentFilterOrder);
		} else {
			// no project
			return context.getString(R.string.no_project);
		}
	}

	@Override
	public List<Report2DChart> getChildrenCharts() {
		return null;
	}

	@Override
	protected void createFilter() {
		columnFilter = TransactionColumns.project_id.name();
		boolean includeNoProject = MyPreferences.includeNoFilterInReport();
		filterIds = new ArrayList<>();
		filterTitles = new ArrayList<>();
		currentFilterOrder = 0;
		List<Project> projects = em.getAllProjectsList(includeNoProject);
		for (Project p : projects) {
			filterIds.add(p.id);
			filterTitles.add(p.title);
		}
	}

	@Override
	public String getNoFilterMessage(Context context) {
		return context.getString(R.string.report_no_project);
	}
}
