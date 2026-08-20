/*
 * Copyright (c) 2012 Denis Solonenko.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */

package io.github.mpstudios56.cifra.activity;

import io.github.mpstudios56.cifra.R;
import io.github.mpstudios56.cifra.db.MyEntityManager;
import io.github.mpstudios56.cifra.model.MyLocation;
import io.github.mpstudios56.cifra.utils.MyPreferences;

/**
 * Created by IntelliJ IDEA.
 * User: denis.solonenko
 * Date: 7/2/12 9:25 PM
 */
public class LocationSelector<A extends AbstractActivity> extends MyEntitySelector<MyLocation, A> {

    public LocationSelector(A activity, MyEntityManager em, ActivityLayout x) {
        this(activity, em, x, R.id.location_add, R.id.location_clear, R.string.current_location);
    }

    public LocationSelector(A activity, MyEntityManager em, ActivityLayout x, int actBtnId, int clearBtnId, int emptyId) {
        super(MyLocation.class, activity, em, x, MyPreferences.isShowLocation(),
                R.id.location, actBtnId, clearBtnId, R.string.location, emptyId,
                R.id.location_filter_toggle, R.id.location_show_list, R.id.location_create);
        if (MyPreferences.getLocationSelectorType() == MyPreferences.EntitySelectorType.SEARCH) {
            setUseSearchAsPrimary(true);
        }
    }

    @Override
    protected Class getEditActivityClass() {
        return LocationActivity.class;
    }

}
