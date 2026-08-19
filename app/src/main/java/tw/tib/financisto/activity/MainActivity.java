package tw.tib.financisto.activity;

import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.View;
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
import tw.tib.financisto.sync.AutoSync;
import tw.tib.financisto.utils.CrashCatcher;
import tw.tib.financisto.utils.MyPreferences;
import tw.tib.financisto.service.QuickBar;
import tw.tib.financisto.utils.PinProtection;

public class MainActivity extends AppCompatActivity {

    /** Asked by the widget: show me the templates, however this app shows them. */
    public static final String GO_TO_TEMPLATES = "go_to_templates";
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
        SUMMARY("summary", R.drawable.ic_tab_summary, R.string.summary),
        BLOTTER("blotter", R.drawable.ic_tab_blotter, R.string.blotter),
        DRAFTS("drafts", R.drawable.ic_tab_drafts, R.string.drafts),
        TEMPLATES("templates", R.drawable.ic_tab_templates, R.string.templates),
        BUDGETS("budgets", R.drawable.ic_tab_budgets, R.string.budgets),
        REPORTS("reports", R.drawable.ic_tab_reports, R.string.reports),
        ACCOUNTS("accounts", R.drawable.ic_tab_accounts, R.string.accounts),
        MENU("menu", R.drawable.ic_tab_menu, R.string.menu);

        final String tag;
        final int iconId;
        final int titleId;

        MainTab(String tag, int iconId, int titleId) {
            this.tag = tag;
            this.iconId = iconId;
            this.titleId = titleId;
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
            // The strip sits at the bottom now, so it is the navigation bar it has
            // to keep clear of, not the status bar.
            v.setPadding(0, 0, 0, insets.bottom);
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
        result.add(MainTab.ACCOUNTS);
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
                    tab.setText(which.titleId);
                    tabs.put(which.tag, tab);
                });
        tabLayoutMediator.attach();

        // The floating buttons follow the tab: see floatingButtonsFor.
        viewPager.registerOnPageChangeCallback(new androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                // After the pager has finished with the frame, not during it:
                // changing the layout of anything in the window while the pager
                // is settling makes it drop the page it was asked for, which is
                // why a tab sometimes lit up while the screen stayed behind.
                viewPager.post(() -> floatingButtonsFor(position));
            }
        });
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
        QuickBar.refresh(this);
        greenRobotBus.register(this);
        PinProtection.unlock(this);
        if (PinProtection.isUnlocked()) {
            WebViewDialog.checkVersionAndShowWhatsNewIfNeeded(this);
            DonatePrompt.maybeAsk(this);
        }
        // The rounds themselves are driven from the application, so that they
        // carry on while somebody is inside an account. What is left here is the
        // one thing this screen owes them: if a round landed while its tabs were
        // not being looked at, they are redrawn now rather than left showing what
        // they had loaded before.
        // The four rows that carry a placeholder instead of a name are put
        // right on every visit, not only at the first start: restoring a backup
        // brings the placeholders back with it, and until now they stayed on
        // screen as "<current>" until the app was closed and opened again.
        nameTheSpecialRows();
        if (AutoSync.tookSomethingIn()) {
            redrawEveryTab();
        }
        offerToReportACrash();
        answerTheWidget(getIntent());
        // The buttons are attached by the application after this runs, so the
        // first decision about them is taken once they exist.
        findViewById(android.R.id.content).post(
                () -> floatingButtonsFor(viewPager.getCurrentItem()));
    }

    /**
     * Asks, once, whether to send the reason the app closed last time.
     * <p>
     * Testers can say "si è chiuso" and no more, and the store only collects
     * crashes from people who agreed to send them - so the ones who did not are
     * invisible. Nothing leaves the phone unless this button is pressed, and
     * what leaves is the version, the phone and the trace.
     */
    private void offerToReportACrash() {
        String report = CrashCatcher.waiting(this);
        if (report == null) {
            return;
        }
        CrashCatcher.clear(this);
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.crash_title)
                .setMessage(R.string.crash_message)
                .setPositiveButton(R.string.crash_send, (d, w) -> {
                    Intent send = new Intent(Intent.ACTION_SEND);
                    send.setType("text/plain");
                    send.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.crash_subject));
                    send.putExtra(Intent.EXTRA_TEXT, report);
                    startActivity(Intent.createChooser(send, getString(R.string.crash_send)));
                })
                .setNegativeButton(R.string.crash_no, null)
                .show();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        answerTheWidget(intent);
    }

    @Override
    protected void onPause() {
        super.onPause();
        greenRobotBus.unregister(this);
        PinProtection.lock(this);
        AutoSync.stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        PinProtection.immediateLock(this);
    }

    /** Gives the four special rows the names of this language. */
    private void nameTheSpecialRows() {
        DatabaseAdapter db = new DatabaseAdapter(this);
        try {
            db.open();
            SQLiteDatabase x = db.db();
            updateFieldInTable(x, DatabaseHelper.CATEGORY_TABLE, 0, "title", getString(R.string.no_category));
            updateFieldInTable(x, DatabaseHelper.CATEGORY_TABLE, -1, "title", getString(R.string.split));
            updateFieldInTable(x, DatabaseHelper.PROJECT_TABLE, 0, "title", getString(R.string.no_project));
            updateFieldInTable(x, DatabaseHelper.LOCATIONS_TABLE, 0, "title", getString(R.string.current_location));
        } catch (Exception e) {
            Log.e(TAG, "could not name the special rows", e);
        } finally {
            db.close();
        }
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

    /**
     * The widget's templates button. With the tab on it lands there; with the
     * tab off it lands on the movements and opens the chooser, which is what
     * the plus button does in the same case.
     */
    private void answerTheWidget(Intent intent) {
        if (intent == null || !intent.getBooleanExtra(GO_TO_TEMPLATES, false)) {
            return;
        }
        intent.removeExtra(GO_TO_TEMPLATES);
        if (MyPreferences.isTemplatesAsTab(this)) {
            TabLayout.Tab tab = tabs.get("templates");
            if (tab != null) {
                tabLayout.selectTab(tab);
                return;
            }
        }
        TabLayout.Tab blotter = tabs.get("blotter");
        if (blotter != null) {
            tabLayout.selectTab(blotter);
        }
        BlotterFragment.templatesWanted = true;
    }

    /**
     * Back to today on whichever tab is in front: the movements scroll to it,
     * the summary returns to the period that holds it, and anything else is
     * left alone rather than made to pretend.
     */
    /**
     * Which of the two floating buttons the tab in front has any use for.
     * <p>
     * The eye hides figures, so it belongs wherever figures are shown - not on
     * the menu, and not on the drafts, which are movements not yet written
     * down. Today jumps to a date, so it belongs only where there are dates in
     * a row: the summary and the movements.
     */
    private void floatingButtonsFor(int position) {
        String tag = position >= 0 && position < visibleTabs.size()
                ? visibleTabs.get(position).tag : "";
        View eye = findViewById(R.id.privacy_button);
        View today = findViewById(R.id.today_button);
        if (eye != null) {
            eye.setVisibility("menu".equals(tag) || "drafts".equals(tag)
                    ? View.GONE : View.VISIBLE);
        }
        if (today != null) {
            today.setVisibility("summary".equals(tag) || "blotter".equals(tag)
                    ? View.VISIBLE : View.GONE);
        }
        View oldest = findViewById(R.id.oldest_button);
        if (oldest != null) {
            oldest.setVisibility("blotter".equals(tag) ? View.VISIBLE : View.GONE);
        }
        View top = findViewById(R.id.top_button);
        if (top != null) {
            top.setVisibility("blotter".equals(tag) ? View.VISIBLE : View.GONE);
        }
        TodayButton.stack(this);
    }

    /** Down to the first movement ever written down. */
    /** Up to the newest movement, at the top of the list. */
    public void goToTop() {
        Fragment f = getSupportFragmentManager().findFragmentByTag("f" + viewPager.getCurrentItem());
        if (f instanceof BlotterFragment) {
            ((BlotterFragment) f).goToTop();
        }
    }

    public void goToOldest() {
        Fragment f = getSupportFragmentManager().findFragmentByTag("f" + viewPager.getCurrentItem());
        if (f instanceof BlotterFragment) {
            ((BlotterFragment) f).goToOldest();
        }
    }

    public void goToToday() {
        Fragment f = getSupportFragmentManager().findFragmentByTag("f" + viewPager.getCurrentItem());
        if (f instanceof BlotterFragment) {
            ((BlotterFragment) f).goToToday();
        } else if (f instanceof SummaryFragment) {
            ((SummaryFragment) f).goToToday();
        }
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
    public void onSwitchToTab(tw.tib.financisto.bus.SwitchToTab event) {
        TabLayout.Tab tab = tabs.get(event.tag);
        if (tab != null) {
            tabLayout.selectTab(tab);
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

    /**
     * A round of sharing brought something in: every tab that is alive redraws,
     * not only the one in front.
     * <p>
     * The pager keeps the neighbouring tabs built and does not take them through
     * onResume when they slide into view, so refreshing the current one left the
     * summary and the account list showing what they had loaded before the round
     * - right until somebody went into an account and came out, which is exactly
     * the moment a figure stops being trustworthy.
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDataArrived(tw.tib.financisto.bus.DataArrived e) {
        redrawEveryTab();
    }

    private void redrawEveryTab() {
        for (Fragment f : getSupportFragmentManager().getFragments()) {
            if (!f.isAdded()) {
                continue;
            }
            if (f instanceof SummaryFragment) {
                ((SummaryFragment) f).redraw();
            } else if (f instanceof RefreshSupportedActivity) {
                ((RefreshSupportedActivity) f).recreateCursor();
            }
        }
    }

    private void updateFieldInTable(SQLiteDatabase db, String table, long id, String field, String value) {
        db.execSQL("update " + table + " set " + field + "=? where _id=?", new Object[]{value, id});
    }
}
