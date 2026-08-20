package io.github.mpstudios56.cifra.report;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import io.github.mpstudios56.cifra.R;
import io.github.mpstudios56.cifra.db.DatabaseAdapter;
import io.github.mpstudios56.cifra.db.DatabaseHelper.TransactionColumns;
import io.github.mpstudios56.cifra.graph.Report2DChart;
import io.github.mpstudios56.cifra.model.Account;
import io.github.mpstudios56.cifra.model.Currency;
import io.github.mpstudios56.cifra.model.ReportDataByPeriod;
import io.github.mpstudios56.cifra.utils.CurrencyCache;
import io.github.mpstudios56.cifra.utils.MyPreferences;

import android.content.Context;

/**
 * 2D Chart Report to display monthly account results.
 * @author Abdsandryk
 */
public class AccountByPeriodReport extends Report2DChart {

	public AccountByPeriodReport(Context context, DatabaseAdapter em, Calendar startPeriod, int periodLength, Currency currency, MyPreferences.ReportAggregateUnit aggregateUnit) {
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
		return R.string.account;
	}

	/* (non-Javadoc)
	 * @see io.github.mpstudios56.cifra.graph.ReportGraphic2D#getFilterName()
	 */
	@Override
	public String getFilterName() {
		if (filterTitles.size()>0) {
			return filterTitles.get(currentFilterOrder);
		} else {
			return context.getString(R.string.no_account);
		}
	}

	@Override
	protected void createFilter() {
		columnFilter = TransactionColumns.from_account_id.name();

		filterIds = new ArrayList<>();
		filterTitles = new ArrayList<>();
		currentFilterOrder = 0;
		List<Account> accounts = em.getAllAccountsList();
		for (Account a: accounts) {
			if (!a.isIncludeIntoReports) continue;   // excluded from reports, no chart entry
			filterIds.add(a.id);
			filterTitles.add(a.title);
		}
	}

	@Override
	public String getNoFilterMessage(Context context) {
		return context.getString(R.string.report_no_account);
	}

	@Override
	public Currency getCurrency() {
		if (filterIds.size() > 0) {
			return em.getAccount(filterIds.get(currentFilterOrder)).currency;
		}
		else {
			return CurrencyCache.getHomeCurrency();
		}
	}

	@Override
	protected ReportDataByPeriod createDataBuilder() {
		return new ReportDataByPeriod(context, startPeriod, periodLength, currency, columnFilter,
				filterIds.get(currentFilterOrder), em, ReportDataByPeriod.ValueAggregation.SUM,
				true, false, aggregateUnit);
	}
}
