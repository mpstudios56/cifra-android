package tw.tib.financisto.activity;

import android.content.Context;
import android.widget.ImageButton;

import tw.tib.financisto.R;
import tw.tib.financisto.filter.WhereFilter;

/**
 * How the filter button says whether it is doing anything.
 * <p>
 * It used to be tinted blue when a filter was set, which is a colour the app
 * uses nowhere else and which said nothing about what had been set. The funnel
 * fills instead - the same one the planner has always shown - and carries the
 * app's own green when it holds something.
 */
class FilterState {

    static void updateFilterColor(Context context, WhereFilter filter, ImageButton button) {
        if (button == null) {
            return;
        }
        button.setColorFilter(null);
        button.setImageResource(filter.isEmpty()
                ? R.drawable.ic_filter_off : R.drawable.ic_filter_on);
    }

}
