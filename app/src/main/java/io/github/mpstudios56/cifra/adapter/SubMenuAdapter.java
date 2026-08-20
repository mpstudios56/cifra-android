package io.github.mpstudios56.cifra.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import io.github.mpstudios56.cifra.R;
import io.github.mpstudios56.cifra.utils.EntityEnum;

/**
 * A page opened from the menu: groups with a heading, and entries under them.
 * <p>
 * A heading is a plain string id in the list of rows; everything else is an
 * entry. Fifteen rows of the same weight are a wall, and backup was fifteen
 * rows: the headings turn it into three short lists one can count at a glance.
 */
public class SubMenuAdapter extends BaseAdapter {

    public interface OnPicked {
        void picked(Object row);
    }

    private static final int SECTION = 0;
    private static final int ENTRY = 1;

    private final Object[] rows;
    private final LayoutInflater inflater;
    private final OnPicked onPicked;

    public SubMenuAdapter(Context context, Object[] rows, OnPicked onPicked) {
        this.rows = rows;
        this.onPicked = onPicked;
        this.inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public int getCount() {
        return rows.length;
    }

    @Override
    public Object getItem(int position) {
        return rows[position];
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public int getItemViewType(int position) {
        return rows[position] instanceof Integer ? SECTION : ENTRY;
    }

    /** A heading is not an answer to anything, so it cannot be touched. */
    @Override
    public boolean isEnabled(int position) {
        return getItemViewType(position) == ENTRY;
    }

    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Object row = rows[position];
        if (row instanceof Integer) {
            View view = convertView != null && convertView.findViewById(R.id.section_title) != null
                    ? convertView
                    : inflater.inflate(R.layout.sub_menu_section, parent, false);
            ((TextView) view.findViewById(R.id.section_title)).setText((Integer) row);
            return view;
        }
        View view = convertView != null && convertView.findViewById(R.id.line1) != null
                ? convertView
                : inflater.inflate(R.layout.summary_entity_list_item, parent, false);
        EntityEnum entry = (EntityEnum) row;
        ((TextView) view.findViewById(R.id.line1)).setText(entry.getTitleId());
        view.findViewById(R.id.label).setVisibility(View.GONE);
        ImageView icon = view.findViewById(R.id.icon);
        if (entry.getIconId() > 0) {
            icon.setImageResource(entry.getIconId());
        }
        view.setOnClickListener(v -> onPicked.picked(entry));
        return view;
    }
}
