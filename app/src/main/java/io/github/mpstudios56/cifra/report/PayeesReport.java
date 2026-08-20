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

import io.github.mpstudios56.cifra.blotter.BlotterFilter;
import io.github.mpstudios56.cifra.filter.WhereFilter;
import io.github.mpstudios56.cifra.filter.Criterion;
import io.github.mpstudios56.cifra.db.DatabaseAdapter;
import io.github.mpstudios56.cifra.model.Currency;

import static io.github.mpstudios56.cifra.db.DatabaseHelper.V_REPORT_PAYEES;

public class PayeesReport extends Report {

	public PayeesReport(Context context, Currency currency) {
		super(ReportType.BY_PAYEE, context, currency);
	}

	@Override
	public ReportData getReport(DatabaseAdapter db, WhereFilter filter) {
        cleanupFilter(filter);
		return queryReport(db, V_REPORT_PAYEES, filter);
	}

	@Override
	public Criterion getCriteriaForId(DatabaseAdapter db, long id) {
		return Criterion.eq(BlotterFilter.PAYEE_ID, String.valueOf(id));
	}		
	
}
