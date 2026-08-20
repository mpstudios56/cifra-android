package io.github.mpstudios56.cifra.report;

import android.content.Context;
import io.github.mpstudios56.cifra.R;
import io.github.mpstudios56.cifra.db.DatabaseAdapter;
import io.github.mpstudios56.cifra.db.DatabaseHelper.TransactionColumns;
import io.github.mpstudios56.cifra.graph.Report2DChart;
import io.github.mpstudios56.cifra.model.Currency;
import io.github.mpstudios56.cifra.model.Payee;
import io.github.mpstudios56.cifra.utils.MyPreferences;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * 2D Chart Report to display monthly results by Payees.
 * @author Denis Solonenko
 */
public class PayeeByPeriodReport extends Report2DChart {

	public PayeeByPeriodReport(Context context, DatabaseAdapter em, Calendar startPeriod, int periodLength, Currency currency, MyPreferences.ReportAggregateUnit aggregateUnit) {
		super(context, em, startPeriod, periodLength, currency, aggregateUnit);
	}

	@Override
	public int getFilterItemTypeName() {
		return R.string.payee;
	}

	@Override
	public String getFilterName() {
		if (filterTitles.size()>0) {
			return filterTitles.get(currentFilterOrder);
		} else {
			// no payee
			return context.getString(R.string.no_payee);
		}
	}

	@Override
	public List<Report2DChart> getChildrenCharts() {
		return null;
	}

	@Override
	protected void createFilter() {
		columnFilter = TransactionColumns.payee_id.name();
		filterIds = new ArrayList<>();
		filterTitles = new ArrayList<>();
		currentFilterOrder = 0;
		List<Payee> payees = em.getAllPayeeList();
		for (Payee p : payees) {
			filterIds.add(p.id);
			filterTitles.add(p.title);
		}
	}

	@Override
	public String getNoFilterMessage(Context context) {
		return context.getString(R.string.report_no_payee);
	}
}
