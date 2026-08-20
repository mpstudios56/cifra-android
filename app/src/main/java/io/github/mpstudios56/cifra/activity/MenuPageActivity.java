package io.github.mpstudios56.cifra.activity;

import android.os.Bundle;
import android.view.View;
import android.widget.ListView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.ListFragment;

import io.github.mpstudios56.cifra.R;
import io.github.mpstudios56.cifra.adapter.SubMenuAdapter;
import io.github.mpstudios56.cifra.utils.ExecutableEntityEnum;

/**
 * A page opened from the menu: a screen of its own, arriving the way every
 * other screen arrives.
 * <p>
 * It was a box dropped over the menu, and then a list swapped in place, and
 * neither of those is how the rest of the app opens anything. The entries it
 * shows are decided by whoever opened it.
 */
public class MenuPageActivity extends AppCompatActivity {

    /** Which page to show: one of the names below. */
    public static final String PAGE_EXTRA = "page";
    public static final String PAGE_CURRENCIES = "currencies";
    public static final String PAGE_ENTITIES = "entities";

    private static Object[] rowsFor(String page) {
        if (PAGE_ENTITIES.equals(page)) {
            return MenuListItem.entitiesPage();
        }
        return MenuListItem.currencyPage();
    }

    private static int titleFor(String page) {
        return PAGE_ENTITIES.equals(page) ? R.string.menu_records : R.string.menu_currencies;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.menu_page);

        String page = getIntent() != null ? getIntent().getStringExtra(PAGE_EXTRA) : null;
        setTitle(titleFor(page));

        View root = findViewById(R.id.menu_page);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.statusBars()
                    | WindowInsetsCompat.Type.captionBar());
            v.setPadding(0, insets.top, 0, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        if (savedInstanceState == null) {
            Page fragment = new Page();
            Bundle args = new Bundle();
            args.putString(PAGE_EXTRA, page);
            fragment.setArguments(args);
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.menu_page_list, fragment)
                    .commit();
        }
    }

    /** The list itself, as a fragment, because the entries want one to act on. */
    public static class Page extends ListFragment {

        private Object[] rows;

        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            String page = getArguments() != null ? getArguments().getString(PAGE_EXTRA) : null;
            rows = rowsFor(page);
            setListAdapter(new SubMenuAdapter(getContext(), rows, this::run));
        }

        @Override
        public void onListItemClick(ListView l, View v, int position, long id) {
            run(rows[position]);
        }

        @SuppressWarnings("unchecked")
        private void run(Object row) {
            if (row instanceof ExecutableEntityEnum) {
                ((ExecutableEntityEnum<androidx.fragment.app.Fragment>) row).execute(this);
            }
        }
    }
}
