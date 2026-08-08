package tw.tib.financisto.activity;

import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import tw.tib.financisto.utils.TransactionDraft;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import tw.tib.financisto.R;
import tw.tib.financisto.bus.GreenRobotBus;
import tw.tib.financisto.bus.RefreshCurrentTab;
import tw.tib.financisto.bus.SwitchToMenuTabEvent;
import tw.tib.financisto.db.DatabaseAdapter;
import tw.tib.financisto.db.DatabaseHelper;
import tw.tib.financisto.dialog.WebViewDialog;
import tw.tib.financisto.bus.GreenRobotBus_;
import tw.tib.financisto.utils.CurrencyCache;
import tw.tib.financisto.utils.DonatePrompt;
import tw.tib.financisto.utils.MyPreferences;
import tw.tib.financisto.utils.PinProtection;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    public static final String GO_TO_SCREEN = "GO_TO_SCREEN";

    private GreenRobotBus greenRobotBus;
    HashMap<String, TabLayout.Tab> tabs;
    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private FragmentStateAdapter pagerAdapter;
    private TabLayoutMediator tabLayoutMediator;
    private List<MainTab> visibleTabs = new ArrayList<>();

    /** The tabs this screen can show, in the order they appear when all are on. */
    private enum MainTab {
        SUMMARY("summary", R.drawable.ic_tab_summary),
        ACCOUNTS("accounts", R.drawable.ic_tab_accounts),
        BLOTTER("blotter", R.drawable.ic_tab_blotter),
        DRAFTS("drafts", R.drawable.ic_tab_drafts),
        TEMPLATES("templates", R.drawable.ic_tab_templates),
        BUDGETS("budgets", R.drawable.ic_tab_budgets),
        REPORTS("reports", R.drawable.ic_tab_reports),
        MENU("menu", R.drawable.ic_tab_menu);

        final String tag;
        final int iconId;

        MainTab(String tag, int iconId) {
            this.tag = tag;
            this.iconId = iconId;
        }
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(MyPreferences.switchLocale(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Two questions worth asking once, before anything is on screen to
        // distract from them. It comes back here when it is done.
        if (WelcomeActivity.isPending(this)) {
            startActivity(new Intent(this, WelcomeActivity.class));
            finish();
            return;
        }

        if (MyPreferences.isSecureWindow()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }

        greenRobotBus = GreenRobotBus_.getInstance_(this);
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.main2);

        initialLoad();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.tabs), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.statusBars()
                    | WindowInsetsCompat.Type.captionBar());
            v.setPadding(0, insets.top, 0, 0);
            return WindowInsetsCompat.CONSUMED;
        });

        tabLayout = findViewById(R.id.tabs);
        viewPager = findViewById(R.id.viewpager);

        viewPager.setUserInputEnabled(false);

        tabs = new HashMap<>();

        visibleTabs = buildVisibleTabs();
        pagerAdapter = new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position)
            {
                switch (visibleTabs.get(position)) {
                    case SUMMARY: return new SummaryFragment();
                    case ACCOUNTS: return new AccountRecyclerFragment();
                    case BLOTTER: return new BlotterFragment(true);
                    case DRAFTS: return new DraftListFragment();
                    case TEMPLATES: return new TemplatesListFragment();
                    case BUDGETS: return new BudgetListFragment();
                    case REPORTS: return new ReportsListFragment();
                    default: return new MenuListFragment_();
                }
            }

            @Override
            public int getItemCount() {
                return visibleTabs.size();
            }

            // Identify pages by which tab they are, not by where they sit. With the
            // default position-based ids, removing a tab from the middle leaves the
            // cached pages bound to the old positions and the later tabs stop working.
            @Override
            public long getItemId(int position) {
                return visibleTabs.get(position).ordinal();
            }

            @Override
            public boolean containsItem(long itemId) {
                for (MainTab tab : visibleTabs) {
                    if (tab.ordinal() == itemId) {
                        return true;
                    }
                }
                return false;
            }
        };
        viewPager.setAdapter(pagerAdapter);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                refreshCurrentTab();
            }
        });

        attachTabs();

        // Addressed by name, not by index: a hidden tab would otherwise send this
        // to whichever screen happened to slide into that position.
        var intent = getIntent();
        String wanted = intent != null ? intent.getStringExtra(GO_TO_SCREEN) : null;
        if (wanted == null) {
            wanted = MyPreferences.getStartupScreen().tag;
        }
        int start = positionOf(wanted);
        viewPager.setCurrentItem(Math.max(start, 0), false);
    }

    /**
     * Which tabs to show, in order. Budgets and reports can be turned off by anyone
     * who does not use them, and drafts appear only while something is unfinished,
     * so nothing here can be assumed from a position alone.
     */
    private List<MainTab> buildVisibleTabs() {
        List<MainTab> result = new ArrayList<>();
        if (MyPreferences.isShowSummaryTab(this)) {
            result.add(MainTab.SUMMARY);
        }
        result.add(MainTab.ACCOUNTS);
        result.add(MainTab.BLOTTER);
        if (TransactionDraft.count(this) > 0) {
            result.add(MainTab.DRAFTS);
        }
        if (MyPreferences.isTemplatesAsTab(this)) {
            result.add(MainTab.TEMPLATES);
        }
        if (MyPreferences.isShowBudgetsTab(this)) {
            result.add(MainTab.BUDGETS);
        }
        if (MyPreferences.isShowReportsTab(this)) {
            result.add(MainTab.REPORTS);
        }
        result.add(MainTab.MENU);
        return result;
    }

    private void attachTabs() {
        if (tabLayoutMediator != null) {
            tabLayoutMediator.detach();
        }
        tabs.clear();
        tabLayoutMediator = new TabLayoutMediator(tabLayout, viewPager, true, false,
                (tab, position) -> {
                    MainTab which = visibleTabs.get(position);
                    tab.setIcon(ResourcesCompat.getDrawable(getResources(), which.iconId, getTheme()));
                    tabs.put(which.tag, tab);
                });
        tabLayoutMediator.attach();
    }

    /** Position of a tab by name, or -1 when it is not currently shown. */
    private int positionOf(String tag) {
        for (int i = 0; i < visibleTabs.size(); i++) {
            if (visibleTabs.get(i).tag.equals(tag)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Rebuilds the strip when what belongs on it has changed: a draft was started or
     * dealt with, or a tab was switched off in the preferences. Keeps whichever tab
     * was open where possible, since positions shift underneath.
     */
    public void refreshTabs() {
        List<MainTab> wanted = buildVisibleTabs();
        if (wanted.equals(visibleTabs)) {
            return;
        }
        String current = visibleTabs.isEmpty() ? null
                : visibleTabs.get(Math.min(viewPager.getCurrentItem(), visibleTabs.size() - 1)).tag;
        visibleTabs = wanted;
        pagerAdapter.notifyDataSetChanged();
        // The mediator caches the tab count, so it has to be rebuilt around the new one.
        attachTabs();
        int restored = current != null ? positionOf(current) : -1;
        if (restored < 0) {
            // The tab being viewed is the one that just went away, which happens on
            // clearing the last draft. Fall back to the entries rather than landing
            // on whatever slid into that slot.
            restored = Math.max(positionOf(MainTab.BLOTTER.tag), 0);
        }
        viewPager.setCurrentItem(restored, false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshTabs();
        greenRobotBus.register(this);
        PinProtection.unlock(this);
        if (PinProtection.isUnlocked()) {
            WebViewDialog.checkVersionAndShowWhatsNewIfNeeded(this);
            DonatePrompt.maybeAsk(this);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        greenRobotBus.unregister(this);
        PinProtection.lock(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        PinProtection.immediateLock(this);
    }

    private void initialLoad() {
        long t4, t3, t2, t1, t0 = System.currentTimeMillis();
        DatabaseAdapter db = new DatabaseAdapter(this);
        db.open();
        try {
            SQLiteDatabase x = db.db();
            x.beginTransaction();
            t1 = System.currentTimeMillis();
            try {
                updateFieldInTable(x, DatabaseHelper.CATEGORY_TABLE, 0, "title", getString(R.string.no_category));
                updateFieldInTable(x, DatabaseHelper.CATEGORY_TABLE, -1, "title", getString(R.string.split));
                updateFieldInTable(x, DatabaseHelper.PROJECT_TABLE, 0, "title", getString(R.string.no_project));
                updateFieldInTable(x, DatabaseHelper.LOCATIONS_TABLE, 0, "title", getString(R.string.current_location));
                x.setTransactionSuccessful();
            } finally {
                x.endTransaction();
            }
            t2 = System.currentTimeMillis();
            if (MyPreferences.shouldUpdateHomeCurrency()) {
                db.setDefaultHomeCurrency();
            }
            CurrencyCache.initialize(db);
            t3 = System.currentTimeMillis();
            if (MyPreferences.shouldRebuildRunningBalance()) {
                db.rebuildRunningBalances();
            }
            t4 = System.currentTimeMillis();
            if (MyPreferences.shouldUpdateSplitParentAccountId()) {
                db.updateSplitParentAccountId();
            }
            if (MyPreferences.shouldUpdateAccountsLastTransactionDate()) {
                db.updateAccountsLastTransactionDate();
            }
        } finally {
            db.close();
        }
        long t5 = System.currentTimeMillis();
        Log.d(getLocalClassName(), "Load time = " + (t5 - t0) + "ms = " + (t2 - t1) + "ms+" + (t3 - t2) + "ms+" + (t4 - t3) + "ms+" + (t5 - t4) + "ms");
    }

    public void refreshCurrentTab() {
        Fragment f = getSupportFragmentManager().findFragmentByTag("f" + viewPager.getCurrentItem());
        if (f instanceof RefreshSupportedActivity) {
            if (f.isAdded()) {
                RefreshSupportedActivity r = (RefreshSupportedActivity) f;
                r.recreateCursor();
                r.integrityCheck();
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onSwitchToMenuTab(SwitchToMenuTabEvent event) {
        TabLayout.Tab tab = tabs.get("menu");
        if (tab != null) {
            tabLayout.selectTab(tab);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshCurrentTab(RefreshCurrentTab e) {
        refreshCurrentTab();
    }

    private void updateFieldInTable(SQLiteDatabase db, String table, long id, String field, String value) {
        db.execSQL("update " + table + " set " + field + "=? where _id=?", new Object[]{value, id});
    }
}
