package tw.tib.financisto.activity;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import tw.tib.financisto.Application;
import tw.tib.financisto.R;
import tw.tib.financisto.adapter.BlotterListAdapter;
import tw.tib.financisto.model.Account;
import tw.tib.financisto.model.Category;
import tw.tib.financisto.model.CategoryTree;
import tw.tib.financisto.model.CategoryTreeNavigator;
import tw.tib.financisto.utils.CategoryIcons;
import tw.tib.financisto.utils.MenuItemInfo;
import tw.tib.financisto.utils.MyPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class CategorySelectorActivity extends AbstractListActivity<Cursor> {

    public static final String SELECTED_CATEGORY_ID = "SELECTED_CATEGORY_ID";
    public static final String SELECTED_ACCOUNT_ID = "SELECTED_ACCOUNT_ID";
    public static final String EXCLUDED_SUB_TREE_ID = "EXCLUDED_SUB_TREE_ID";
    public static final String INCLUDE_SPLIT_CATEGORY = "INCLUDE_SPLIT_CATEGORY";

    public static final long NO_SELECTED_ACCOUNT = Long.MIN_VALUE;

    private int incomeColor;
    private int expenseColor;

    private CategoryTreeNavigator navigator;
    private Map<Long, String> attributes;

    private Button bBack;

    private boolean isShowRecentlyUsedCategory = false;

    public CategorySelectorActivity() {
        super(R.layout.category_selector);
        enablePin = false;
    }

    @Override
    protected void internalOnCreate(Bundle savedInstanceState) {
        Resources resources = getResources();
        this.incomeColor = resources.getColor(R.color.category_type_income);
        this.expenseColor = resources.getColor(R.color.category_type_expense);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.category_selector), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.statusBars()
                    | WindowInsetsCompat.Type.captionBar());
            var lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            lp.topMargin = insets.top;
            lp.bottomMargin = insets.bottom;
            v.setLayoutParams(lp);
            return WindowInsetsCompat.CONSUMED;
        });

        bBack = findViewById(R.id.bBack);
        bBack.setOnClickListener(view -> {
            if (navigator != null && navigator.goBack()) {
                setListAdapter(createAdapter(null));
            }
        });
        Button bSelect = findViewById(R.id.bSelect);
        bSelect.setOnClickListener(view -> confirmSelection());

        isShowRecentlyUsedCategory = MyPreferences.isShowRecentlyUsedCategory();
        if (isShowRecentlyUsedCategory) {
            View v = findViewById(R.id.suggestedCategoriesBarView);
            if (v != null) v.setVisibility(View.VISIBLE);
        }
    }

    private void confirmSelection() {
        if (navigator != null) {
            Intent data = new Intent();
            data.putExtra(SELECTED_CATEGORY_ID, navigator.selectedCategoryId);
            setResult(RESULT_OK, data);
        }
        finish();
    }

    @Override
    protected List<MenuItemInfo> createContextMenus(long id) {
        return Collections.emptyList();
    }

    @Override
    protected Cursor loadInBackground() {
        long excTreeId = -1;
        Intent intent = getIntent();

        if (isShowRecentlyUsedCategory) {
            Application.getExecutor().execute(() -> {
                var suggestedCategories = loadSuggestedCategories(intent);
                runOnUiThread(() -> fillSuggestedCategories(suggestedCategories));
            });
        }

        if (intent != null) {
            excTreeId = intent.getLongExtra(EXCLUDED_SUB_TREE_ID, -1);
        }
        navigator = new CategoryTreeNavigator(db, excTreeId);
        if (MyPreferences.isSeparateIncomeExpense()) {
            navigator.separateIncomeAndExpense();
        }
        attributes = db.getAllAttributesMap();

        if (intent != null) {
            boolean includeSplit = intent.getBooleanExtra(INCLUDE_SPLIT_CATEGORY, false);
            if (includeSplit) {
                navigator.addSplitCategoryToTheTop();
            }
            navigator.selectCategory(intent.getLongExtra(SELECTED_CATEGORY_ID, 0));
        }

        return null;
    }

    private class CategoryTag {
        public long id;
        public String title;

        public CategoryTag(long id, String title) {
            this.id = id;
            this.title = title;
        }
    }

    private List<CategoryTag> loadSuggestedCategories(Intent intent) {
        long selectedAccountId = NO_SELECTED_ACCOUNT;
        if (intent != null) {
            selectedAccountId = intent.getLongExtra(SELECTED_ACCOUNT_ID, NO_SELECTED_ACCOUNT);
        }

        // Generate recently used categories from last two months
        var c = db.getRecentlyUsedCategories(selectedAccountId, System.currentTimeMillis() - (86400000L * 60));

        var suggestedCategories = new ArrayList<CategoryTag>();
        int suggestionCount = 0;
        try (c) {
            while (c.moveToNext() && suggestionCount < 10) {
                suggestedCategories.add(new CategoryTag(c.getLong(0), c.getString(1)));
                suggestionCount += 1;
            }
        }
        return suggestedCategories;
    }

    private void fillSuggestedCategories(List<CategoryTag> suggestedCategories) {
        var container = (LinearLayout)findViewById(R.id.suggestedCategoriesBar);
        Button placeholder = findViewById(R.id.suggestedCategoriesBarLoadingPlaceholder);

        if (suggestedCategories == null || suggestedCategories.isEmpty()) {
            placeholder.setText(R.string.no_suggestion);
            return;
        }

        placeholder.setVisibility(View.GONE);

        for (var c: suggestedCategories) {
            var v = buildViewForCategory(c);
            v.setOnClickListener(cv -> {
                while (navigator.canGoBack()) {
                    navigator.goBack();
                }
                navigator.selectCategory(c.id);
                confirmSelection();
            });
            container.addView(v);
        }
    }

    private View buildViewForCategory(CategoryTag c) {
        var res = new Button(this);
        res.setText(c.title);
        return res;
    }

    @Override
    protected ListAdapter createAdapter(Cursor cursor) {
        if (navigator == null) {
            return null;
        }
        if (bBack != null) {
            bBack.setEnabled(navigator.canGoBack());
        }
        return new CategoryAdapter(navigator.categories);
    }

    @Override
    protected void deleteItem(View v, int position, long id) {
    }

    @Override
    protected void editItem(View v, int position, long id) {
    }

    @Override
    protected void viewItem(View v, int position, long id) {
        if (navigator.navigateTo(id)) {
            setListAdapter(createAdapter(null));
        } else {
            if (MyPreferences.isAutoSelectChildCategory()) {
                confirmSelection();
            }
        }
    }

    public static boolean pickCategory(Activity activity, boolean forceHierSelector, long selectedId, Account selectedAccount, long excludingTreeId, boolean includeSplit) {
        if (forceHierSelector || MyPreferences.isUseHierarchicalCategorySelector()) {
            Intent intent = new Intent(activity, CategorySelectorActivity.class);
            intent.putExtra(CategorySelectorActivity.SELECTED_CATEGORY_ID, selectedId);
            intent.putExtra(CategorySelectorActivity.SELECTED_ACCOUNT_ID, selectedAccount == null ? NO_SELECTED_ACCOUNT : selectedAccount.getId());
            intent.putExtra(CategorySelectorActivity.EXCLUDED_SUB_TREE_ID, excludingTreeId);
            intent.putExtra(CategorySelectorActivity.INCLUDE_SPLIT_CATEGORY, includeSplit);
            activity.startActivityForResult(intent, R.id.category_pick);
            return true;
        }
        return false;
    }

    private class CategoryAdapter extends BaseAdapter {

        private final CategoryTree<Category> categories;

        private CategoryAdapter(CategoryTree<Category> categories) {
            this.categories = categories;
        }

        @Override
        public int getCount() {
            return categories.size();
        }

        @Override
        public Category getItem(int i) {
            return categories.getAt(i);
        }

        @Override
        public long getItemId(int i) {
            return getItem(i).id;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.category_selector_item, parent, false);
            }
            Category c = getItem(position);
            TextView title = convertView.findViewById(R.id.title);
            TextView tag = convertView.findViewById(R.id.tag);
            ImageView symbol = convertView.findViewById(R.id.category_icon);
            TextView symbolText = convertView.findViewById(R.id.category_icon_text);
            View kind = convertView.findViewById(R.id.kind);
            View deeper = convertView.findViewById(R.id.deeper);

            if (c.id == CategoryTreeNavigator.INCOME_CATEGORY_ID) {
                title.setText(getString(R.string.income));
            } else if (c.id == CategoryTreeNavigator.EXPENSE_CATEGORY_ID) {
                title.setText(getString(R.string.expense));
            } else {
                title.setText(c.title);
            }

            // The attribute of a category, where it has one, reads as a note
            // beside the name rather than as a figure on the right.
            String note = attributes != null && attributes.containsKey(c.id)
                    ? attributes.get(c.id) : c.tag;
            tag.setText(note == null ? "" : note);
            tag.setVisibility(note == null || note.isEmpty() ? View.GONE : View.VISIBLE);

            CategoryIcons.show(symbol, symbolText, c);
            kind.setBackgroundColor(c.isIncome() ? incomeColor : expenseColor);
            deeper.setVisibility(c.hasChildren() ? View.VISIBLE : View.GONE);
            convertView.setActivated(navigator.isSelected(c.id));
            return convertView;
        }
    }
    

}
