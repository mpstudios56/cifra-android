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
package io.github.mpstudios56.cifra.activity;

import static android.app.Activity.RESULT_OK;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.ListAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.mpstudios56.cifra.R;
import io.github.mpstudios56.cifra.adapter.ScheduledListAdapter;
import io.github.mpstudios56.cifra.blotter.BlotterFilter;
import io.github.mpstudios56.cifra.filter.WhereFilter;
import io.github.mpstudios56.cifra.model.TransactionInfo;
import io.github.mpstudios56.cifra.service.RecurrenceScheduler;

import java.util.ArrayList;

public class ScheduledListFragment extends BlotterFragment {
    private ArrayList<TransactionInfo> transactions;
    private boolean pendingReschedule = false;

    private RecurrenceScheduler scheduler;

    public ScheduledListFragment() {}

    public ScheduledListFragment(int layoutId) {
        super(layoutId);
    }

    @Override
    protected void calculateTotals(WhereFilter filter) {
        // do nothing
    }

    @Override
    protected Cursor loadInBackground() {
        if (pendingReschedule == false) {
            transactions = scheduler.getSortedSchedules(System.currentTimeMillis());
        }
        else {
            long now = System.currentTimeMillis();
            transactions = scheduler.scheduleAll(getContext(), now);
            pendingReschedule = false;
        }
        return null;
    }

    @Override
    protected ListAdapter createAdapter(Context context, Cursor cursor) {
        return new ScheduledListAdapter(context, transactions);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        View planner = view.findViewById(R.id.bPlanner);
        if (planner != null) {
            // What is still to come belongs beside the movements, not here.
            planner.setVisibility(View.GONE);
        }
        View quick = view.findViewById(R.id.bQuick);
        if (quick != null) {
            // Quick entry has nothing to do among templates and scheduled rows.
            quick.setVisibility(View.GONE);
        }

        scheduler = new RecurrenceScheduler(db);
        // remove filter button and totals
        if (bFilter != null) {
            bFilter.setVisibility(View.GONE);
        }
        if (bGoToToday != null) {
            bGoToToday.setVisibility(View.GONE);
        }
        view.findViewById(R.id.total).setVisibility(View.GONE);
        internalOnCreateTemplates();
    }

    protected void internalOnCreateTemplates() {
        // change empty list message
        ((TextView) getView().findViewById(android.R.id.empty)).setText(R.string.no_scheduled_transactions);
        // fix filter
        blotterFilter = new WhereFilter(getString(R.string.scheduled_transactions));
        blotterFilter.eq(BlotterFilter.IS_TEMPLATE, String.valueOf(2));
        blotterFilter.eq(BlotterFilter.PARENT_ID, String.valueOf(0));
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            pendingReschedule = true;
            recreateCursor();
        }
    }

    @Override
    public void afterDeletingTransaction(long id) {
        super.afterDeletingTransaction(id);
        scheduler.cancelPendingWorkForSchedule(getContext(), id);
    }

    @Override
    public void integrityCheck() {
        new InstalledOnSdCardCheckTask(getActivity()).execute();
    }


    @Override
    protected boolean filterButtonFilters() {
        return false;
    }
}
