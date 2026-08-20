package io.github.mpstudios56.cifra.report;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import io.github.mpstudios56.cifra.R;
import io.github.mpstudios56.cifra.db.DatabaseAdapter;
import io.github.mpstudios56.cifra.db.DatabaseHelper.TransactionColumns;
import io.github.mpstudios56.cifra.graph.Report2DChart;
import io.github.mpstudios56.cifra.model.Currency;
import io.github.mpstudios56.cifra.model.MyLocation;
import io.github.mpstudios56.cifra.utils.MyPreferences;
import android.content.Context;

/**
 * 2D Chart Report to display monthly results by Locations.
 * @author Abdsandryk
 */
public class LocationByPeriodReport extends Report2DChart {

	public LocationByPeriodReport(Context context, DatabaseAdapter em, Calendar startPeriod, int periodLength, Currency currency, MyPreferences.ReportAggregateUnit aggregateUnit) {
		super(context, em, startPeriod, periodLength, currency, aggregateUnit);
	}

	/* (non-Javadoc)
	 * @see io.github.mpstudios56.cifra.graph.ReportGraphic2D#getChildrenGraphics()
	 */
	@Override
	public List<Report2DChart> getChildrenCharts() {
		return null;
	}

	@Override
	public int getFilterItemTypeName() {
		return R.string.location;
	}

	/* (non-Javadoc)
	 * @see io.github.mpstudios56.cifra.graph.ReportGraphic2D#getFilterName()
	 */
	@Override
	public String getFilterName() {
		if (filterTitles.size()>0) {
			return filterTitles.get(currentFilterOrder);
		} else {
			// no location
			return context.getString(R.string.current_location);
		}
	}

	@Override
	protected void createFilter() {
		columnFilter = TransactionColumns.location_id.name();
		boolean includeNoLocation = MyPreferences.includeNoFilterInReport();
		filterIds = new ArrayList<>();
		filterTitles = new ArrayList<>();
		List<MyLocation> locations = em.getAllLocationsList(includeNoLocation);
		for (MyLocation l : locations) {
			filterIds.add(l.id);
			filterTitles.add(l.title);
		}
	}

	@Override
	public String getNoFilterMessage(Context context) {
		return context.getString(R.string.report_no_location);
	}

}
