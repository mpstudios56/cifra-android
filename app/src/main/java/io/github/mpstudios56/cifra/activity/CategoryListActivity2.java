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

import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.Map;

import io.github.mpstudios56.cifra.R;
import io.github.mpstudios56.cifra.adapter.CategoryListAdapter2;
import io.github.mpstudios56.cifra.model.Category;
import io.github.mpstudios56.cifra.model.CategoryTree;
import java.util.List;

import io.github.mpstudios56.cifra.utils.MenuItemInfo;

public class CategoryListActivity2 extends AbstractListActivity<Cursor> {

	private static final int NEW_CATEGORY_REQUEST = 1;

	// The moves, numbered past the entries the list menu already has.
	private static final int MENU_POS_TOP = MENU_ADD + 1;
	private static final int MENU_POS_UP = MENU_ADD + 2;
	private static final int MENU_POS_DOWN = MENU_ADD + 3;
	private static final int MENU_POS_BOTTOM = MENU_ADD + 4;
	private static final int MENU_POS_SORT = MENU_ADD + 5;
	private static final int EDIT_CATEGORY_REQUEST = 2;

	public CategoryListActivity2() {
		super(R.layout.category_list);
	}

	private CategoryTree<Category> categories;
	private Map<Long, String> attributes;

	@Override
	protected void internalOnCreate(Bundle savedInstanceState) {
		super.internalOnCreate(savedInstanceState);

		ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.category_list), (v, windowInsets) -> {
			Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
					| WindowInsetsCompat.Type.statusBars()
					| WindowInsetsCompat.Type.captionBar());
			var lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
			lp.topMargin = insets.top;
			lp.bottomMargin = insets.bottom;
			v.setLayoutParams(lp);
			return WindowInsetsCompat.CONSUMED;
		});

		ImageButton b = findViewById(R.id.bAttributes);
		b.setOnClickListener(v -> {
			Intent intent = new Intent(CategoryListActivity2.this, AttributeListActivity.class);
			startActivityForResult(intent, 0);
		});
		b = findViewById(R.id.bCollapseAll);
		b.setOnClickListener(v -> ((CategoryListAdapter2) adapter).collapseAllCategories());
		b = findViewById(R.id.bExpandAll);
		b.setOnClickListener(v -> ((CategoryListAdapter2) adapter).expandAllCategories());
		b = findViewById(R.id.bSort);
		b.setOnClickListener(v -> sortByTitle());
		b = findViewById(R.id.bFix);
		b.setOnClickListener(v -> reIndex());
	}

	private void sortByTitle() {
		if (categories != null && categories.sortByTitle()) {
			db.updateCategoryTree(categories);
			recreateCursor();
		}
	}

	private void reIndex() {
		db.restoreSystemEntities();
		recreateCursor();
	}

	@Override
	protected void addItem() {
		Intent intent = new Intent(CategoryListActivity2.this, CategoryActivity.class);
		startActivityForResult(intent, NEW_CATEGORY_REQUEST);
	}

	@Override
	protected ListAdapter createAdapter(Cursor cursor) {
		CategoryListAdapter2 a = new CategoryListAdapter2(this, categories);
		a.setAttributes(attributes);
		return a;
	}

	@Override
	protected Cursor loadInBackground() {
		long t0 = System.currentTimeMillis();
		categories = db.getCategoriesTree(false);
		attributes = db.getAllAttributesMap();
		long t1 = System.currentTimeMillis();
		Log.d("CategoryListActivity2", "Requery in " + (t1 - t0) + "ms");
		return null;
	}

	@Override
	protected void deleteItem(View v, int position, final long id) {
		Category c = (Category) getListAdapter().getItem(position);
		new AlertDialog.Builder(this)
				.setTitle(c.getTitle())
				.setIcon(android.R.drawable.ic_dialog_alert)
				.setMessage(R.string.delete_category_dialog)
				.setPositiveButton(R.string.yes, (arg0, arg1) -> {
					db.deleteCategory(id);
					recreateCursor();
				})
				.setNegativeButton(R.string.no, null)
				.show();
	}

	@Override
	public void editItem(View v, int position, long id) {
		Intent intent = new Intent(CategoryListActivity2.this, CategoryActivity.class);
		intent.putExtra(CategoryActivity.CATEGORY_ID_EXTRA, id);
		startActivityForResult(intent, EDIT_CATEGORY_REQUEST);
	}

	/**
	 * A touch goes into the category, where the small arrow used to be the only
	 * way in.
	 * <p>
	 * It used to open a box of arrows for moving the category up and down,
	 * which is a thing one does once in a while, while going into a category is
	 * a thing one does constantly. Those arrows are on the held-down menu now,
	 * where the rest of what can be done to a category already lives.
	 */
	@Override
	protected void onItemClick(View v, int position, long id) {
		Category c = (Category) getListAdapter().getItem(position);
		if (c != null && c.hasChildren()) {
			((CategoryListAdapter2) adapter).onListItemClick(c.id);
		} else {
			editItem(v, position, id);
		}
	}

	@Override
	protected void viewItem(View v, int position, long id) {
		editItem(v, position, id);
	}

	/** Where the category stands among the ones it shares a parent with. */
	private CategoryTree<Category> treeOf(Category c) {
		return c.parent == null ? categories : c.parent.children;
	}

	private Category categoryAt(int position) {
		Object item = getListAdapter().getItem(position);
		return item instanceof Category ? (Category) item : null;
	}

	/**
	 * The held-down menu: what can be done to a category, including the moves
	 * that used to be behind a touch.
	 */
	@Override
	protected List<MenuItemInfo> createContextMenus(long id) {
		List<MenuItemInfo> menus = super.createContextMenus(id);
		Category c = findById(id);
		if (c == null) {
			return menus;
		}
		CategoryTree<Category> tree = treeOf(c);
		int pos = tree.indexOf(c);
		if (pos > 0) {
			menus.add(new MenuItemInfo(MENU_POS_TOP, R.string.position_move_top, R.drawable.ic_move_top));
			menus.add(new MenuItemInfo(MENU_POS_UP, R.string.position_move_up, R.drawable.ic_move_up));
		}
		if (pos >= 0 && pos < tree.size() - 1) {
			menus.add(new MenuItemInfo(MENU_POS_DOWN, R.string.position_move_down, R.drawable.ic_move_down));
			menus.add(new MenuItemInfo(MENU_POS_BOTTOM, R.string.position_move_bottom, R.drawable.ic_move_bottom));
		}
		if (c.hasChildren()) {
			menus.add(new MenuItemInfo(MENU_POS_SORT, R.string.sort_by_title, R.drawable.sort));
		}
		return menus;
	}

	private Category findById(long id) {
		ListAdapter a = getListAdapter();
		for (int i = 0; i < a.getCount(); i++) {
			Object item = a.getItem(i);
			if (item instanceof Category && ((Category) item).id == id) {
				return (Category) item;
			}
		}
		return null;
	}

	@Override
	public boolean onPopupItemSelected(int itemId, View view, int position, long id) {
		Category c = categoryAt(position);
		if (c == null) {
			return super.onPopupItemSelected(itemId, view, position, id);
		}
		CategoryTree<Category> tree = treeOf(c);
		int pos = tree.indexOf(c);
		boolean moved;
		if (itemId == MENU_POS_TOP) {
			moved = tree.moveCategoryToTheTop(pos);
		} else if (itemId == MENU_POS_UP) {
			moved = tree.moveCategoryUp(pos);
		} else if (itemId == MENU_POS_DOWN) {
			moved = tree.moveCategoryDown(pos);
		} else if (itemId == MENU_POS_BOTTOM) {
			moved = tree.moveCategoryToTheBottom(pos);
		} else if (itemId == MENU_POS_SORT) {
			moved = tree.sortByTitle();
		} else {
			return super.onPopupItemSelected(itemId, view, position, id);
		}
		if (moved) {
			db.updateCategoryTree(tree);
			notifyDataSetChanged();
		}
		return true;
	}

	protected void notifyDataSetChanged() {
		((CategoryListAdapter2) adapter).notifyDataSetChanged();
	}

	protected void notifyDataSetInvalidated() {
		((CategoryListAdapter2) adapter).notifyDataSetInvalidated();
	}








}
