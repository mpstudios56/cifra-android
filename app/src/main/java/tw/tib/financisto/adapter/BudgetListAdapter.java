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
package tw.tib.financisto.adapter;

import java.util.List;

import tw.tib.financisto.R;
import tw.tib.financisto.model.Budget;
import tw.tib.financisto.model.Currency;
import tw.tib.financisto.utils.Utils;
import tw.tib.financisto.utils.RecurUtils.Recur;
import tw.tib.financisto.utils.RecurUtils.RecurInterval;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ProgressBar;
import android.widget.TextView;

public class BudgetListAdapter extends BaseAdapter {

	private final Context context;
	private final LayoutInflater inflater;
	private final Utils u;

    private List<Budget> budgets;
	
	public BudgetListAdapter(Context context, List<Budget> budgets) {
		this.context = context;
		this.inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		this.budgets = budgets;
		this.u = new Utils(context);
	}
	
	public void setBudgets(List<Budget> budgets) {
		this.budgets = budgets;
		notifyDataSetChanged();
	}

	@Override
	public int getCount() {
		if (budgets == null) return 0;
		return budgets.size();
	}

	@Override
	public Budget getItem(int i) {
		return budgets.get(i);
	}

	@Override
	public long getItemId(int i) {
		return getItem(i).id;
	}

	private final StringBuilder sb = new StringBuilder();

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		Holder v;
		if (convertView == null) {
			convertView = inflater.inflate(R.layout.budget_list_item, parent, false);
			v = Holder.create(convertView);
		} else {
			v = (Holder)convertView.getTag();
		}
		Budget b = getItem(position);
		v.centerView.setText(b.title);
		
		Currency c = b.getBudgetCurrency();
		long amount = b.amount;
		v.rightCenterView.setText(Utils.amountToString(c, Math.abs(amount)));
		
		StringBuilder sb = this.sb;

		sb.setLength(0);
		Recur r = b.getRecur();
		if (r.interval != RecurInterval.NO_RECUR) {
			sb.append("#").append(b.recurNum+1).append(" ");
		}
		sb.append(DateUtils.formatDateRange(context, b.startDate, b.endDate, 
				DateUtils.FORMAT_SHOW_TIME|DateUtils.FORMAT_SHOW_DATE|DateUtils.FORMAT_ABBREV_MONTH));
		v.topView.setText(sb.toString());
		
		if (b.updated) {			
			long spent = b.spent;
			long balance = amount+spent;
			u.setAmountText(v.rightView1, c, balance, false);
			u.setAmountText(v.rightView2, c, spent, false);

			sb.setLength(0);
			String categoriesText = b.categoriesText;
			if (Utils.isEmpty(categoriesText)) {
				sb.append("*");
			} else {
				sb.append( categoriesText);
			}
			if (b.includeSubcategories) {
				sb.append("/*");
			}
			if (!Utils.isEmpty(b.projectsText)) {
				sb.append(" [").append(b.projectsText).append("]");
			}
			v.bottomView.setText(sb.toString());
            if (b.amount > 0) {
			    v.progressBar.setMax((int)Math.abs(b.amount));
			    v.progressBar.setProgress((int)(balance-1));
            } else {
                v.progressBar.setMax((int)Math.abs(b.amount));
                v.progressBar.setProgress((int)(spent-1));
            }
			showPace(v.progressBar, b, balance);
		} else {
			v.bottomView.setText("*/*");
			v.rightView1.setText(R.string.calculating);
			v.rightView2.setText(R.string.calculating);
			v.progressBar.setMax(1);
			v.progressBar.setSecondaryProgress(0);
			v.progressBar.setProgress(0);						
		}
		v.progressBar.setVisibility(View.VISIBLE);
		return convertView;
	}

	private static final int OVER = Color.parseColor("#FFD0453B");
	private static final int BEHIND = Color.parseColor("#FFE9A742");
	private static final int WITHIN = Color.parseColor("#FF3FA96F");

	/**
	 * Colours the bar by how the spending compares with how much of the period has
	 * gone, and marks where it should stand today. Half a budget left on the last
	 * day and half left on the first day are not the same thing, and the bar alone
	 * could not tell them apart.
	 */
	private void showPace(ProgressBar bar, Budget b, long balance) {
		long amount = Math.abs(b.amount);
		if (amount == 0) {
			return;
		}
		long span = b.endDate - b.startDate;
		double elapsed = span > 0
				? Math.max(0, Math.min(1, (System.currentTimeMillis() - b.startDate) / (double) span))
				: 1;
		// The bar counts down what is left, so at any moment this much should be.
		long shouldRemain = (long) (amount * (1 - elapsed));
		bar.setSecondaryProgress((int) shouldRemain);

		long left = b.amount > 0 ? balance : amount + b.spent;
		int color = left < 0 ? OVER : (left < shouldRemain ? BEHIND : WITHIN);
		bar.setProgressTintList(ColorStateList.valueOf(color));
	}

    private static class Holder {

		public TextView topView;
		public TextView centerView;
		public TextView bottomView;
		public TextView rightView1;
		public TextView rightView2;
		public TextView rightCenterView;
		public ProgressBar progressBar;
		
		public static Holder create(View view) {
			Holder v = new Holder();
			v.topView = (TextView)view.findViewById(R.id.top);
			v.centerView = (TextView)view.findViewById(R.id.center);		
			v.bottomView = (TextView)view.findViewById(R.id.bottom);
			v.rightView1 = (TextView)view.findViewById(R.id.right1);
			v.rightView2 = (TextView)view.findViewById(R.id.right2);
			v.rightCenterView = (TextView)view.findViewById(R.id.right_center);
			v.progressBar = (ProgressBar)view.findViewById(R.id.progress);
			view.setTag(v);
			return v;
		}
		
	}

}
