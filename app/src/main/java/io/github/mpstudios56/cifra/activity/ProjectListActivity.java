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

import android.view.View;
import io.github.mpstudios56.cifra.R;
import io.github.mpstudios56.cifra.blotter.BlotterFilter;
import io.github.mpstudios56.cifra.filter.Criterion;
import io.github.mpstudios56.cifra.model.Project;

public class ProjectListActivity extends MyEntityListActivity<Project> {

    public ProjectListActivity() {
        super(Project.class, R.string.no_projects);
    }

    @Override
    protected Class getEditActivityClass() {
        return ProjectActivity.class;
    }

    @Override
    protected Criterion createBlotterCriteria(Project p) {
        return Criterion.eq(BlotterFilter.PROJECT_ID, String.valueOf(p.id));
    }

    @Override
    protected void deleteItem(View v, int position, long id) {
        db.deleteProject(id);
        recreateCursor();
    }

}
