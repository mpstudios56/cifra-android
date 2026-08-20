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
package io.github.mpstudios56.cifra.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import io.github.mpstudios56.cifra.R;
import io.github.mpstudios56.cifra.utils.SummaryEntityEnum;

public class SummaryEntityListAdapter extends BaseAdapter {

    /** Told which line was touched, when the screen would rather hear it here. */
    public interface OnPicked {
        void picked(int position);
    }

    private final io.github.mpstudios56.cifra.utils.EntityEnum[] entities;
    private final LayoutInflater inflater;
    private OnPicked onPicked;

    /**
     * Hands the row its own listener instead of leaving the list to work out
     * which line a touch belongs to.
     * <p>
     * A list decides that after the fact, and drops the whole thing if anything
     * lays the screen out again between the finger going down and coming up -
     * which is why a tap on the menu did nothing now and then, and why nudging
     * the list first made it work. A listener on the row itself does not care.
     */
    public void setOnPicked(OnPicked listener) {
        this.onPicked = listener;
        notifyDataSetChanged();
    }

    public SummaryEntityListAdapter(Context context, io.github.mpstudios56.cifra.utils.EntityEnum[] entries) {
        this.entities = entries;
        this.inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public int getCount() {
        return entities.length;
    }

    @Override
    public Object getItem(int position) {
        return entities[position];
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Holder h;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.summary_entity_list_item, parent, false);
            h = new Holder();
            h.icon = convertView.findViewById(R.id.icon);
            h.title = convertView.findViewById(R.id.line1);
            h.label = convertView.findViewById(R.id.label);
            convertView.setTag(h);
        } else {
            h = (Holder)convertView.getTag();
        }
        if (onPicked != null) {
            final int line = position;
            convertView.setOnClickListener(v -> onPicked.picked(line));
        }
        io.github.mpstudios56.cifra.utils.EntityEnum r = entities[position];
        h.title.setText(r.getTitleId());
        // A line of explanation where there is one; the sub-lists are single
        // words - "Backup", "Ripristina" - and a second line under them would
        // only repeat the first.
        if (r instanceof SummaryEntityEnum) {
            h.label.setVisibility(View.VISIBLE);
            h.label.setText(((SummaryEntityEnum) r).getSummaryId());
        } else {
            h.label.setVisibility(View.GONE);
        }
        if (r.getIconId() > 0) {
            h.icon.setImageResource(r.getIconId());
        }
        return convertView;
    }

    private static final class Holder {
        public ImageView icon;
        public TextView title;
        public TextView label;
    }

}