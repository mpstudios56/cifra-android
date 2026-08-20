/*
 * Copyright (C) 2010 Cyril Mottier (http://www.cyrilmottier.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package greendroid.widget;

import android.content.Context;
import android.graphics.Rect;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.TextView;
import io.github.mpstudios56.cifra.R;

import java.util.List;

/**
 * A {@link QuickActionGrid} is an implementation of a {@link QuickActionWidget}
 * that displays {@link greendroid.widget.QuickAction}s in a grid manner. This is usually used to create
 * a shortcut to jump between different type of information on screen.
 * 
 * @author Benjamin Fellous
 * @author Cyril Mottier
 */
public class QuickActionGrid extends QuickActionWidget {

    private GridView mGridView;
    private List<QuickAction> quickActions;

    public QuickActionGrid(Context context) {
        super(context);

        setContentView(R.layout.gd_quick_action_grid);

        final View v = getContentView();
        mGridView = (GridView) v.findViewById(R.id.gdi_grid);
    }

    public void setNumColumns(int columns) {
        mGridView.setNumColumns(columns);
    }

    @Override
    protected void populateQuickActions(final List<QuickAction> quickActions) {
        this.quickActions = quickActions;
        mGridView.setAdapter(new BaseAdapter() {

            public View getView(int position, View view, ViewGroup parent) {

                TextView textView = (TextView) view;

                if (view == null) {
                    final LayoutInflater inflater = LayoutInflater.from(getContext());
                    textView = (TextView) inflater.inflate(R.layout.gd_quick_action_grid_item, mGridView, false);
                }

                QuickAction quickAction = quickActions.get(position);
                textView.setText(quickAction.mTitle);
                textView.setCompoundDrawablesWithIntrinsicBounds(null, quickAction.mDrawable, null, null);

                return textView;

            }

            public long getItemId(int position) {
                return position;
            }

            public Object getItem(int position) {
                return null;
            }

            public int getCount() {
                return quickActions.size();
            }
        });

        mGridView.setOnItemClickListener(mInternalItemClickListener);
    }

    @Override
    protected void onMeasureAndLayout(Rect anchorRect, View contentView) {

        //contentView.setLayoutParams(new GridView.LayoutParams(GridView.LayoutParams.WRAP_CONTENT, GridView.LayoutParams.WRAP_CONTENT));
        // As wide as its entries need and no wider. Left to ask for what its
        // contents wanted, the words broke in two; given the whole screen, two
        // buttons sat in a bubble the width of the phone. So: room enough for
        // each entry, up to the width of the screen.
        float density = contentView.getResources().getDisplayMetrics().density;
        int margin = Math.round(16 * density);
        int roomPerEntry = Math.round(104 * density);
        // Never wider than what it actually holds: a ring of two, in a grid
        // still willing to lay out three, was a bubble with an empty column in
        // it.
        int columns = mGridView.getNumColumns();
        if (quickActions != null && !quickActions.isEmpty()) {
            columns = Math.min(columns, quickActions.size());
        }
        int width = getScreenWidth() - 2 * margin;
        if (columns > 0) {
            width = Math.min(width, columns * roomPerEntry);
        }
        setWidth(width);
        contentView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                ViewGroup.LayoutParams.WRAP_CONTENT);

        int rootHeight = contentView.getMeasuredHeight();

        int offsetY = getArrowOffsetY();
        int dyTop = anchorRect.top;
        int dyBottom = getScreenHeight() - anchorRect.bottom;

        boolean onTop = (dyTop > dyBottom);
        int popupY = (onTop) ? anchorRect.top - rootHeight + offsetY : anchorRect.bottom - offsetY;

        setWidgetSpecs(popupY, onTop);
    }

    private OnItemClickListener mInternalItemClickListener = new OnItemClickListener() {
        public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
            getOnQuickActionClickListener().onQuickActionClicked(QuickActionGrid.this, position, quickActions.get(position));
            if (getDismissOnClick()) {
                dismiss();
            }
        }
    };

}
