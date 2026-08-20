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

import android.content.Context;
import android.widget.ListAdapter;

import io.github.mpstudios56.cifra.R;
import io.github.mpstudios56.cifra.adapter.BlotterListAdapter;
import io.github.mpstudios56.cifra.blotter.BlotterFilter;
import io.github.mpstudios56.cifra.filter.WhereFilter;
import io.github.mpstudios56.cifra.utils.MyPreferences;

import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.os.BuildCompat;

public class TemplatesListFragment extends BlotterFragment {

    public TemplatesListFragment() {
    }

    public TemplatesListFragment(int layoutId) {
        super(layoutId);
    }

    @Override
    protected void calculateTotals(WhereFilter filter) {
        // do nothing
    }

    @Override
    protected Cursor loadInBackground() {
        String sortOrder = BlotterFilter.SORT_NEWER_TO_OLDER;

        switch (MyPreferences.getTemplatessSortOrder()) {
            case NAME:
                sortOrder = BlotterFilter.SORT_BY_TEMPLATE_NAME;
                break;

            case ACCOUNT:
                sortOrder = BlotterFilter.SORY_BY_ACCOUNT_NAME;
                break;

            case CATEGORY:
                sortOrder = BlotterFilter.SORT_BY_CATEGORY;
                break;

            case PROJECT:
                // Templates without a project fall in with the ones that have
                // none, in category order: an empty column sorted on its own
                // would put half the list nowhere in particular.
                sortOrder = BlotterFilter.SORT_BY_PROJECT;
                break;
        }

        return db.getAllTemplates(blotterFilter, sortOrder);
    }

    @Override
    protected ListAdapter createAdapter(Context context, Cursor cursor) {
        return new BlotterListAdapter(context, db, cursor) {
            @Override
            protected boolean isShowRunningBalance() {
                return false;
            }
        };
    }

    /**
     * A template is a movement waiting to happen, so touching one makes it
     * happen: the movement is written into the register there and then, dated
     * at this moment, and opened for whatever needs changing - most often the
     * amount. Before, a touch showed the card of a movement that did not exist.
     */
    @Override
    protected void onItemClick(View v, int position, long id) {
        long created = db.duplicateTransaction(id);
        new BlotterOperations(getContext(), this, db, created).editTransaction();
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

        // remove filter button and totals
        if (bFilter != null) {
            // The filter button carries the order of this list. It was hidden
            // because there is nothing to filter here; there is something to
            // sort, and this is where somebody looks for it.
            bFilter.setVisibility(View.VISIBLE);
            // Here it orders rather than filters, so it carries the mark of an
            // order and not the funnel.
            bFilter.setImageResource(R.drawable.format_list_numbered);
            bFilter.setOnClickListener(v -> askForOrder());
        }
        if (showAllBlotterButtons && bTemplate != null) {
            bTemplate.setVisibility(View.GONE);
        }
        View total = view.findViewById(R.id.total);
        if (total != null) {
            total.setVisibility(View.GONE);
        }
        internalOnCreateTemplates();
    }

    @Override
    protected boolean addTemplateToAddButton() {
        return false;
    }

    /**
     * The blotter titles itself from the internal name of its filter, which here is
     * the bare word "templates", under a "Transactions" subtitle that does not apply.
     * <p>
     * Only worth doing where there is a title bar to write into. Inside the main
     * screen there is not: the tab already says which list this is, and a second
     * bar above the buttons only made this tab taller than its neighbours.
     */
    @Override
    protected void applyFilter() {
        super.applyFilter();
        if (isInsideMainScreen()) {
            // Nothing to write: the tab underneath carries the name, and this was
            // the only tab with a title of its own.
            return;
        }
        if (getActivity() instanceof AppCompatActivity) {
            ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
            if (actionBar != null) {
                actionBar.setTitle(R.string.templates);
                actionBar.setSubtitle(null);
            }
        }
    }

    private boolean isInsideMainScreen() {
        return getActivity() instanceof MainActivity;
    }

    protected void internalOnCreateTemplates() {
        // change empty list message
        ((TextView) getView().findViewById(android.R.id.empty)).setText(R.string.no_templates);
        // fix filter
        blotterFilter = new WhereFilter("templates");
        blotterFilter.eq(BlotterFilter.IS_TEMPLATE, String.valueOf(1));
        blotterFilter.eq(BlotterFilter.PARENT_ID, String.valueOf(0));
    }


    /**
     * The order the templates are kept in, asked here rather than in the
     * settings: it belongs to this list, and it is the only list it applies to.
     */
    private void askForOrder() {
        final MyPreferences.TemplatesSortOrder[] orders = {
                MyPreferences.TemplatesSortOrder.DATE,
                MyPreferences.TemplatesSortOrder.CATEGORY,
                MyPreferences.TemplatesSortOrder.PROJECT};
        String[] names = {
                getString(R.string.sort_templates_by_date),
                getString(R.string.sort_templates_by_category),
                getString(R.string.sort_templates_by_project)};
        MyPreferences.TemplatesSortOrder current = MyPreferences.getTemplatessSortOrder();
        int chosen = 0;
        for (int i = 0; i < orders.length; i++) {
            if (orders[i] == current) {
                chosen = i;
            }
        }
        new android.app.AlertDialog.Builder(getContext())
                .setTitle(R.string.sort_order)
                .setSingleChoiceItems(names, chosen, (dialog, which) -> {
                    androidx.preference.PreferenceManager
                            .getDefaultSharedPreferences(getContext()).edit()
                            .putString("sort_templates", orders[which].name()).apply();
                    dialog.dismiss();
                    recreateCursor();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }


    @Override
    protected boolean filterButtonFilters() {
        return false;
    }
}
