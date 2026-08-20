package io.github.mpstudios56.cifra.activity;

import static android.app.Activity.RESULT_FIRST_USER;
import static android.app.Activity.RESULT_OK;
import static android.content.Context.MODE_PRIVATE;

import static java.lang.String.format;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.CursorAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ListAdapter;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.MenuProvider;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.Lifecycle;
import androidx.loader.content.Loader;

import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import greendroid.widget.QuickActionGrid;
import greendroid.widget.QuickActionWidget;
import io.github.mpstudios56.cifra.Application;
import io.github.mpstudios56.cifra.R;
import io.github.mpstudios56.cifra.adapter.BlotterListAdapter;
import io.github.mpstudios56.cifra.adapter.TransactionsListAdapter;
import io.github.mpstudios56.cifra.blotter.AccountTotalCalculationTask;
import io.github.mpstudios56.cifra.blotter.BlotterFilter;
import io.github.mpstudios56.cifra.blotter.BlotterTotalCalculationTask;
import io.github.mpstudios56.cifra.blotter.TotalCalculationTask;
import io.github.mpstudios56.cifra.db.DatabaseHelper;
import io.github.mpstudios56.cifra.dialog.TransactionInfoDialog;
import io.github.mpstudios56.cifra.filter.Criterion;
import io.github.mpstudios56.cifra.filter.DateTimeCriterion;
import io.github.mpstudios56.cifra.filter.WhereFilter;
import io.github.mpstudios56.cifra.db.DatabaseAdapter;
import io.github.mpstudios56.cifra.model.Account;
import io.github.mpstudios56.cifra.model.AccountType;
import io.github.mpstudios56.cifra.model.Budget;
import io.github.mpstudios56.cifra.model.Transaction;
import io.github.mpstudios56.cifra.model.TransactionAttribute;
import io.github.mpstudios56.cifra.model.TransactionStatus;
import io.github.mpstudios56.cifra.rates.ExchangeRate;
import io.github.mpstudios56.cifra.utils.IntegrityCheckRunningBalance;
import io.github.mpstudios56.cifra.utils.MenuItemInfo;
import io.github.mpstudios56.cifra.utils.MyPreferences;
import io.github.mpstudios56.cifra.utils.PinProtection;
import io.github.mpstudios56.cifra.utils.TotalsPopup;
import io.github.mpstudios56.cifra.utils.Utils;
import io.github.mpstudios56.cifra.view.NodeInflater;
import io.github.mpstudios56.cifra.orb.EntityManager;

public class BlotterFragment extends AbstractListFragment<Cursor> implements BlotterOperations.BlotterOperationsCallback {
    private String TAG = getClass().getSimpleName();

    public static final String MAIN_BLOTTER = "mainBlotter";
    public static final String EXTRA_FILTER_ACCOUNTS = "filterAccounts";
    public static final String GO_TO_TRANSACTION = "goToTransaction";

    private static final int NEW_TRANSACTION_REQUEST = 1;
    private static final int NEW_TRANSFER_REQUEST = 3;
    private static final int NEW_TRANSACTION_FROM_TEMPLATE_REQUEST = 5;
    private static final int MONTHLY_VIEW_REQUEST = 6;
    private static final int BILL_PREVIEW_REQUEST = 7;
    private static final int SHOW_TOTALS_REQUEST = 8;

    protected static final int FILTER_REQUEST = 6;
    private static final int MENU_DUPLICATE = MENU_ADD + 1;
    private static final int MENU_SAVE_AS_TEMPLATE = MENU_ADD + 2;
    private static final int MENU_SHOW_IN_ACCOUNT_BLOTTER = MENU_ADD + 3;
    private static final int MENU_CHANGE_TO_TRANSACTION = MENU_ADD + 4;
    private static final int MENU_CHANGE_TO_TRANSFER = MENU_ADD + 5;

    protected TextView totalText;
    protected TextView emptyText;
    protected TextView period;
    protected ProgressBar progressBar;

    protected ImageButton bFilter;
    protected ImageButton bTransfer;
    protected ImageButton bTemplate;
    protected ImageButton bSearch;
    protected ImageButton bGoToToday;
//    protected ImageButton bMenu;

    protected QuickActionGrid transactionActionGrid;
    protected QuickActionGrid addButtonActionGrid;

    private TotalCalculationTask calculationTask;

    protected boolean mainBlotter;
    protected WhereFilter blotterFilter = WhereFilter.empty();

    protected static final long BEFORE_INITIAL_LOAD = -1;
    protected long lastTxId = BEFORE_INITIAL_LOAD;
    protected long lastDay = BEFORE_INITIAL_LOAD;

    protected boolean isAccountBlotter = false;
    protected boolean showAllBlotterButtons = true;
    protected boolean isQuickMenuEnabledForTransaction = false;
    protected boolean isQuickMenuShowAdditionalTransactionStatus = false;
    protected boolean isQuickMenuShowDuplicateKeepTime = false;
    protected boolean isQuickMenuShowDuplicateKeepDateTime = false;

    protected OnBackPressedCallback backCallback;

    private static final Pattern amountSearchPattern = Pattern.compile("^([<>])?(\\d+(?:\\.\\d+)?)(?:~(\\d+(?:\\.\\d+)?))?$");

    private NodeInflater inflater;
    private long selectedId = -1;
    private long duplicatedTransactionId = -1;

    public BlotterFragment(int layoutId) {
        super(layoutId);
    }

    public BlotterFragment() {
        super(R.layout.blotter);
    }

    public BlotterFragment(boolean mainBlotter) {
        super(R.layout.blotter);
        this.mainBlotter = mainBlotter;
    }

    protected void calculateTotals(WhereFilter filter) {
        if (calculationTask != null) {
            calculationTask.stop();
            calculationTask.cancel(true);
        }
        calculationTask = createTotalCalculationTask(filter);
        if (calculationTask != null) {
            calculationTask.execute();
        }
    }

    protected TotalCalculationTask createTotalCalculationTask(WhereFilter filter) {
        Context context = getContext();
        if (context == null) {
            return null;
        }
        context = context.getApplicationContext();
        if (filter.getAccountId() > 0) {
            return new AccountTotalCalculationTask(context, db, filter, totalText);
        } else {
            return new BlotterTotalCalculationTask(context, db, filter, totalText);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater layoutInflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = super.onCreateView(layoutInflater, container, savedInstanceState);
        inflater = new NodeInflater(layoutInflater);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Bundle args = getArguments();
        if (args != null) {
            blotterFilter = WhereFilter.fromBundle(args);
            isAccountBlotter = args.getBoolean(BlotterFilterActivity.IS_ACCOUNT_FILTER, false);
        }
        if (savedInstanceState != null) {
            mainBlotter = savedInstanceState.getBoolean(MAIN_BLOTTER);
            blotterFilter = WhereFilter.fromBundle(savedInstanceState);
        }
        if (mainBlotter && blotterFilter.isEmpty()) {
            blotterFilter = WhereFilter.fromSharedPreferences(getContext().getSharedPreferences(this.getClass().getName(), 0));
        }
        // onViewCreated will create and start loader, which will use blotterFilter prepared above
        super.onViewCreated(view, savedInstanceState);

        // Fast scroll is toggled dynamically: disabled while idle so the invisible
        // thumb no longer intercepts right-edge touches, enabled while scrolling.
        // MassOp / BudgetBlotter inherit this too.
        io.github.mpstudios56.cifra.utils.SafeFastScroll.attach(getListView());

        // Room at the foot of the list for the phone's own buttons. Inside an
        // account nothing had made it, so the oldest movement - the one at the
        // bottom - was there and could not be read.
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(getListView(),
                (v, windowInsets) -> {
                    int under = windowInsets.getInsets(
                            androidx.core.view.WindowInsetsCompat.Type.systemBars()).bottom;
                    if (v.getPaddingBottom() != under) {
                        v.setPadding(v.getPaddingLeft(), v.getPaddingTop(),
                                v.getPaddingRight(), under);
                        ((android.view.ViewGroup) v).setClipToPadding(false);
                    }
                    return windowInsets;
                });

        attachSwipe();

        // A toolbar belongs to a screen of its own. Inside the main screen the tab
        // says which list this is, and a title bar on top of the button bar only
        // makes that tab taller than every other.
        if (!mainBlotter && !(getActivity() instanceof MainActivity)) {
            // non-main blotter is contained in BlotterActivity, with fragment container layout
            // having a toolbar
            var toolbar = (Toolbar) view.findViewById(R.id.toolbar);
            if (toolbar != null) {
                toolbar.setVisibility(View.VISIBLE);
                ((AppCompatActivity) getActivity()).setSupportActionBar(toolbar);
                // The bar used to grow by a status bar's worth and pad itself by
                // as much again, from the days when the screen was drawn behind
                // the status bar. The window now sits below it on its own, so all
                // that did was leave a band of nothing above the account name.
            }
        }

        View vi = view.findViewById(R.id.bottom_bar);
        if (vi != null) {
            ViewCompat.setOnApplyWindowInsetsListener(vi, (v, windowInsets) -> {
                Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                        | WindowInsetsCompat.Type.statusBars()
                        | WindowInsetsCompat.Type.captionBar()
                        | WindowInsetsCompat.Type.ime());
                Log.d(TAG, format("insets.bottom: %s", insets.bottom));
                v.setPadding(0, insets.top, 0, 0);
                return WindowInsetsCompat.CONSUMED;
            });
        }

        integrityCheck();

        backCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                FrameLayout searchLayout = view.findViewById(R.id.search_text_frame);
                if (searchLayout != null && searchLayout.getVisibility() == View.VISIBLE) {
                    searchLayout.setVisibility(View.GONE);
                    this.setEnabled(false);
                }
            }
        };

        getActivity().getOnBackPressedDispatcher().addCallback(backCallback);

        showAllBlotterButtons = !MyPreferences.isCollapseBlotterButtons();

        isQuickMenuEnabledForTransaction = MyPreferences.isQuickMenuEnabledForTransaction();
        isQuickMenuShowAdditionalTransactionStatus = MyPreferences.isQuickMenuShowAdditionalTransactionStatus();
        isQuickMenuShowDuplicateKeepTime = MyPreferences.isQuickMenuShowDuplicateKeepTime();
        isQuickMenuShowDuplicateKeepDateTime = MyPreferences.isQuickMenuShowDuplicateKeepDateTime();

        // Always offered, collapsed buttons or not: the point of quick entry is that
        // it takes one tap to reach, and a menu would put it behind two.
        ImageButton bQuick = view.findViewById(R.id.bQuick);
        if (bQuick != null) {
            bQuick.setOnClickListener(arg0 -> {
                Intent quick = new Intent(getContext(), QuickTransactionActivity.class);
                long account = accountBeingLookedAt();
                if (account > 0) {
                    quick.putExtra(QuickTransactionActivity.ACCOUNT_ID_EXTRA, account);
                }
                startActivityForResult(quick, NEW_TRANSACTION_REQUEST);
            });
        }

        if (showAllBlotterButtons) {
            bTransfer = view.findViewById(R.id.bTransfer);
            if (bTransfer != null) {
                bTransfer.setVisibility(View.VISIBLE);
                bTransfer.setOnClickListener(arg0 -> addItem(NEW_TRANSFER_REQUEST, TransferActivity.class));
            }

            bTemplate = view.findViewById(R.id.bTemplate);
            if (bTemplate != null) {
                // Hidden when templates have a tab of their own, so the same thing is
                // not offered from two places at once.
                boolean asTab = MyPreferences.isTemplatesAsTab(getContext());
                bTemplate.setVisibility(asTab ? View.GONE : View.VISIBLE);
                bTemplate.setOnClickListener(v -> createFromTemplate());
            }
        }

        bFilter = view.findViewById(R.id.bFilter);
        if (bFilter != null) {
            bFilter.setOnClickListener(v -> {
                Intent intent = new Intent(getContext(), BlotterFilterActivity.class);
                blotterFilter.toIntent(intent);
                intent.putExtra(BlotterFilterActivity.IS_ACCOUNT_FILTER, isAccountBlotter && blotterFilter.getAccountId() > 0);
                startActivityForResult(intent, FILTER_REQUEST);
            });
        }

        totalText = view.findViewById(R.id.total);
        if (totalText != null) {
            totalText.setOnClickListener((v) -> {
                if (MyPreferences.isBlurBalances()) {
                    if (totalText.getPaint().getMaskFilter() != null) {
                        totalText.getPaint().setMaskFilter(null);
                        totalText.invalidate();
                    } else {
                        Utils.applyBlur(getView().findViewById(R.id.total));
                    }
                }
            });
            totalText.setOnLongClickListener((v) -> {
                showTotals();
                return true;
            });
        }

        emptyText = view.findViewById(android.R.id.empty);
        period = view.findViewById(R.id.period);
        progressBar = view.findViewById(android.R.id.progress);

        // What is still to come, next to what has already happened: the planner
        // was a line in the menu, three screens away from the list it belongs
        // beside.
        ImageButton bPlanner = view.findViewById(R.id.bPlanner);
        if (bPlanner != null) {
            bPlanner.setOnClickListener(v ->
                    startActivity(new Intent(getContext(), PlannerActivity.class)));
        }

        bSearch = view.findViewById(R.id.bSearch);
        if (bSearch != null) {
            bSearch.setOnClickListener(method -> {
                EditText searchText = view.findViewById(R.id.search_text);
                FrameLayout searchLayout = view.findViewById(R.id.search_text_frame);
                ImageButton searchTextClearButton = view.findViewById(R.id.search_text_clear);
                InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);

                searchText.setOnFocusChangeListener((v, b) -> {
                    if (!v.hasFocus()) {
                        imm.hideSoftInputFromWindow(searchLayout.getWindowToken(), 0);
                    }
                });

                searchTextClearButton.setOnClickListener(v -> {
                    searchText.setText("");
                });

                if (searchLayout.getVisibility() == View.VISIBLE) {
                    imm.hideSoftInputFromWindow(searchLayout.getWindowToken(), 0);
                    searchLayout.setVisibility(View.GONE);
                    backCallback.setEnabled(false);
                    return;
                }

                searchLayout.setVisibility(View.VISIBLE);
                searchText.requestFocusFromTouch();
                imm.showSoftInput(searchText, InputMethodManager.SHOW_IMPLICIT);
                backCallback.setEnabled(true);

                searchText.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                    }

                    @Override
                    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                    }

                    @Override
                    public void afterTextChanged(Editable editable) {
                        ImageButton clearButton = view.findViewById(R.id.search_text_clear);
                        String text = editable.toString();
                        blotterFilter.remove(BlotterFilter.NOTE);
                        while (blotterFilter.remove(BlotterFilter.FROM_AMOUNT) != null);
                        while (blotterFilter.remove(BlotterFilter.ORIGINAL_FROM_AMOUNT) != null);

                        if (!text.isEmpty()) {
                            Criterion amount = null;
                            Matcher m = amountSearchPattern.matcher(text);
                            if (m.matches()) {
                                if (m.group(1) == null && m.group(3) == null) {
                                    // 123.45
                                    String val = Double.toString(Math.floor(Double.parseDouble(m.group(2)) * 100));
                                    amount = Criterion.or(
                                            Criterion.eq(BlotterFilter.FROM_AMOUNT, val),
                                            Criterion.eq(BlotterFilter.FROM_AMOUNT, "-" + val),
                                            Criterion.eq(BlotterFilter.ORIGINAL_FROM_AMOUNT, val),
                                            Criterion.eq(BlotterFilter.ORIGINAL_FROM_AMOUNT, "-" + val));
                                }
                                else if (m.group(3) == null) {
                                    // >123.45, <123.45
                                    String val = Double.toString(Math.floor(Double.parseDouble(m.group(2)) * 100));
                                    if (m.group(1).equals("<")) {
                                        amount = Criterion.or(
                                                Criterion.and(
                                                        Criterion.lt(BlotterFilter.FROM_AMOUNT, val),
                                                        Criterion.gt(BlotterFilter.FROM_AMOUNT, "0")),
                                                Criterion.and(
                                                        Criterion.gt(BlotterFilter.FROM_AMOUNT, "-" + val),
                                                        Criterion.lt(BlotterFilter.FROM_AMOUNT, "0")),
                                                Criterion.and(
                                                        Criterion.lt(BlotterFilter.ORIGINAL_FROM_AMOUNT, val),
                                                        Criterion.gt(BlotterFilter.ORIGINAL_FROM_AMOUNT, "0")),
                                                Criterion.and(
                                                        Criterion.gt(BlotterFilter.ORIGINAL_FROM_AMOUNT, "-" + val),
                                                        Criterion.lt(BlotterFilter.ORIGINAL_FROM_AMOUNT, "0")));
                                    }
                                    else if (m.group(1).equals(">")) {
                                        amount = Criterion.or(
                                                Criterion.gt(BlotterFilter.FROM_AMOUNT, val),
                                                Criterion.lt(BlotterFilter.FROM_AMOUNT, "-" + val),
                                                Criterion.gt(BlotterFilter.ORIGINAL_FROM_AMOUNT, val),
                                                Criterion.lt(BlotterFilter.ORIGINAL_FROM_AMOUNT, "-" + val));
                                    }
                                }
                                else if (m.group(1) == null) {
                                    // 100~900
                                    String val2 = Double.toString(Math.floor(Double.parseDouble(m.group(2)) * 100));
                                    String val3 = Double.toString(Math.floor(Double.parseDouble(m.group(3)) * 100));
                                    amount = Criterion.or(
                                            Criterion.btw(BlotterFilter.FROM_AMOUNT, val2, val3),
                                            Criterion.btw(BlotterFilter.FROM_AMOUNT, "-" + val3, "-" + val2),
                                            Criterion.btw(BlotterFilter.ORIGINAL_FROM_AMOUNT, val2, val3),
                                            Criterion.btw(BlotterFilter.ORIGINAL_FROM_AMOUNT, "-" + val3, "-" + val2));
                                }
                            }
                            String likePattern = format("%%%s%%", text);
                            // Everything the entry carries a name for. Searching only
                            // note, payee and category left out project, location and
                            // account, which is where a half-remembered word usually
                            // is: "Amsterdam" is a project or a place, rarely a note.
                            Criterion byName = Criterion.or(
                                    Criterion.like(BlotterFilter.NOTE, likePattern),
                                    Criterion.like(BlotterFilter.PAYEE, likePattern),
                                    Criterion.like(BlotterFilter.CATEGORY_NAME, likePattern),
                                    Criterion.like(BlotterFilter.PROJECT_NAME, likePattern),
                                    Criterion.like(BlotterFilter.LOCATION_NAME, likePattern),
                                    Criterion.like(BlotterFilter.ACCOUNT_NAME, likePattern)
                            );
                            if (amount == null) {
                                blotterFilter.eq(byName);
                            }
                            else {
                                blotterFilter.eq(Criterion.or(amount, byName));
                            }


                            clearButton.setVisibility(View.VISIBLE);
                        } else {
                            clearButton.setVisibility(View.GONE);
                        }

                        recreateCursor();
                        applyFilter();
                        saveFilter();
                    }
                });

                if (blotterFilter.get(BlotterFilter.NOTE) != null) {
                    String searchFilterText = blotterFilter.get(BlotterFilter.NOTE).getStringValue();
                    if (!searchFilterText.isEmpty()) {
                        searchFilterText = searchFilterText.substring(1, searchFilterText.length() - 1);
                        searchText.setText(searchFilterText);
                    }
                }
            });
        }

        // The button in the bar is gone: today floats above the eye now, on
        // both the screens where today means something. The view is still
        // found, because screens built on this one reach for it.
        bGoToToday = view.findViewById(R.id.bToday);
        if (bGoToToday != null) {
            bGoToToday.setVisibility(View.GONE);
        }

        applyFilter();
        prepareTransactionActionGrid();
        prepareAddButtonActionGrid();

        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                if (isAccountBlotter) {
                    long accountId = blotterFilter.getAccountId();
                    if (accountId != -1) {
                        // get account type
                        Account account = db.getAccount(accountId);
                        AccountType type = AccountType.valueOf(account.type);
                        if (type.isCreditCard) {
                            // Show menu for Credit Cards - bill
                            menuInflater.inflate(R.menu.ccard_blotter_menu, menu);
                        } else {
                            // Show menu for other accounts - monthly view
                            menuInflater.inflate(R.menu.blotter_menu, menu);
                        }
                    }
                }
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                long accountId = blotterFilter.getAccountId();
                Intent intent = new Intent(getContext(), MonthlyViewActivity.class);
                intent.putExtra(MonthlyViewActivity.ACCOUNT_EXTRA, accountId);

                switch (menuItem.getItemId()) {
                    case R.id.opt_menu_month:
                        // call credit card bill activity sending account id
                        intent.putExtra(MonthlyViewActivity.BILL_PREVIEW_EXTRA, false);
                        startActivityForResult(intent, MONTHLY_VIEW_REQUEST);
                        return true;

                    case R.id.opt_menu_bill:
                        if (accountId != -1) {
                            Account account = db.getAccount(accountId);

                            // call credit card bill activity sending account id
                            if (account.paymentDay > 0 && account.closingDay > 0) {
                                intent.putExtra(MonthlyViewActivity.BILL_PREVIEW_EXTRA, true);
                                startActivityForResult(intent, BILL_PREVIEW_REQUEST);
                            } else {
                                // display message: need payment and closing day
                                AlertDialog.Builder dlgAlert = new AlertDialog.Builder(getContext());
                                dlgAlert.setMessage(R.string.statement_error);
                                dlgAlert.setTitle(R.string.ccard_statement);
                                dlgAlert.setPositiveButton(R.string.ok, null);
                                dlgAlert.setCancelable(true);
                                dlgAlert.create().show();
                            }
                        }
                        return true;
                }
                return false;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    protected void showTotals() {
        // Only where it has something to say. With one currency the screen listed
        // the figure already on the button and nothing else.
        if (!TotalsPopup.severalCurrencies(db)) {
            return;
        }
        Intent intent = new Intent(getContext(), BlotterTotalsDetailsActivity.class);
        blotterFilter.toIntent(intent);
        startActivityForResult(intent, SHOW_TOTALS_REQUEST);
    }

    /** Whether the row being acted on is a transfer, asked once when it opens. */
    private boolean selectedIsTransfer = false;

    protected void prepareTransactionActionGrid() {
        boolean plainMovement = !blotterFilter.isTemplate() && !blotterFilter.isSchedule();
        selectedIsTransfer = false;
        if (plainMovement && selectedId > 0) {
            try {
                Transaction t = db.getTransaction(selectedId);
                selectedIsTransfer = t != null && t.isTransfer();
            } catch (Exception ignored) {
            }
        }
        transactionActionGrid = new QuickActionGrid(getContext());
        transactionActionGrid.addQuickAction(new MyQuickAction(getContext(), R.drawable.ic_menu_info, R.string.info));
        transactionActionGrid.addQuickAction(new MyQuickAction(getContext(), R.drawable.ic_menu_edit, R.string.edit));
        transactionActionGrid.addQuickAction(new MyQuickAction(getContext(), R.drawable.ic_menu_delete, R.string.delete));
        // One entry each, and the choice is made in a second ring rather than
        // in the settings. Five states and three ways of copying used to be
        // laid out flat here, or hidden behind switches nobody had reason to
        // find: which state, and which kind of copy, are questions about the
        // movement in hand, not about the app.
        transactionActionGrid.addQuickAction(new MyQuickAction(getContext(), R.drawable.ic_duplicate, R.string.duplicate));
        transactionActionGrid.addQuickAction(new MyQuickAction(getContext(), R.drawable.ic_action_state, MyQuickAction.NO_FILTER, R.string.transaction_change_status));
        if (plainMovement) {
            transactionActionGrid.addQuickAction(new MyQuickAction(getContext(), R.drawable.ic_tab_templates, R.string.save_as_template));
            transactionActionGrid.addQuickAction(new MyQuickAction(getContext(), R.drawable.ic_tab_accounts, R.string.transaction_show_in_account_blotter));
            if (selectedIsTransfer) {
                transactionActionGrid.addQuickAction(new MyQuickAction(getContext(), R.drawable.ic_row_transaction, R.string.change_to_transaction));
            } else {
                transactionActionGrid.addQuickAction(new MyQuickAction(getContext(), R.drawable.ic_row_transfer, R.string.change_to_transfer));
            }
        }
        transactionActionGrid.setOnQuickActionClickListener(transactionActionListener);
    }

    private QuickActionWidget.OnQuickActionClickListener transactionActionListener = (widget, position, action) -> {
        int titleId = ((MyQuickAction) action).titleId;

        if (titleId == R.string.info) {
            showTransactionInfo(selectedId);
        }
        else if (titleId == R.string.edit) {
            editTransaction(selectedId);
        }
        else if (titleId == R.string.delete) {
            deleteTransaction(selectedId);
        }
        else if (titleId == R.string.transaction_status_restored) {
            restoreTransaction(selectedId);
        }
        else if (titleId == R.string.transaction_status_pending) {
            pendingTransaction(selectedId);
        }
        else if (titleId == R.string.transaction_status_unreconciled) {
            unreconcileTransaction(selectedId);
        }
        else if (titleId == R.string.duplicate) {
            askHowToDuplicate(anchorFor(widget));
        }
        else if (titleId == R.string.transaction_change_status) {
            askWhichStatus(anchorFor(widget));
        }
        else if (titleId == R.string.clear) {
            clearTransaction(selectedId);
        }
        else if (titleId == R.string.reconcile) {
            reconcileTransaction(selectedId);
        }
        else if (titleId == R.string.duplicate_keep_time) {
            duplicateTransactionKeepTime(selectedId);
        }
        else if (titleId == R.string.duplicate_keep_date_time) {
            duplicateTransactionKeepDateTime(selectedId);
        }
        // The four that used to live in the list menu, handled by the same code
        // that handled them there rather than by a copy of it.
        else if (titleId == R.string.save_as_template) {
            onPopupItemSelected(MENU_SAVE_AS_TEMPLATE, null, 0, selectedId);
        }
        else if (titleId == R.string.transaction_show_in_account_blotter) {
            onPopupItemSelected(MENU_SHOW_IN_ACCOUNT_BLOTTER, null, 0, selectedId);
        }
        else if (titleId == R.string.change_to_transaction) {
            onPopupItemSelected(MENU_CHANGE_TO_TRANSACTION, null, 0, selectedId);
        }
        else if (titleId == R.string.change_to_transfer) {
            onPopupItemSelected(MENU_CHANGE_TO_TRANSFER, null, 0, selectedId);
        }
    };

    /** The row the first ring opened on, so the second opens in the same place. */
    private View rowTouched;

    /** Where a second ring can hang itself: the row the finger was on. */
    private View anchorFor(QuickActionWidget widget) {
        if (rowTouched != null && rowTouched.isShown()) {
            return rowTouched;
        }
        View list = getListView();
        return list != null ? list : getView();
    }

    /**
     * The five states, each in its own colour, chosen for the movement in hand.
     * <p>
     * The three uncommon ones were behind a switch in the settings, so somebody
     * who needed one of them once had to go and find the switch, turn it on,
     * come back, and leave it on for ever after.
     */
    private void askWhichStatus(View anchor) {
        QuickActionGrid states = new QuickActionGrid(getContext());
        states.addQuickAction(MyQuickAction.big(getContext(), R.drawable.status_restored, R.string.transaction_status_restored));
        states.addQuickAction(MyQuickAction.big(getContext(), R.drawable.status_pending, R.string.transaction_status_pending));
        states.addQuickAction(MyQuickAction.big(getContext(), R.drawable.status_unreconciled, R.string.transaction_status_unreconciled));
        states.addQuickAction(MyQuickAction.big(getContext(), R.drawable.status_cleared, R.string.transaction_status_cleared));
        states.addQuickAction(MyQuickAction.big(getContext(), R.drawable.status_reconciled, R.string.transaction_status_reconciled));
        states.setOnQuickActionClickListener((widget, position, action) -> {
            int titleId = ((MyQuickAction) action).titleId;
            if (titleId == R.string.transaction_status_restored) {
                restoreTransaction(selectedId);
            } else if (titleId == R.string.transaction_status_pending) {
                pendingTransaction(selectedId);
            } else if (titleId == R.string.transaction_status_unreconciled) {
                unreconcileTransaction(selectedId);
            } else if (titleId == R.string.transaction_status_cleared) {
                clearTransaction(selectedId);
            } else {
                reconcileTransaction(selectedId);
            }
        });
        states.show(anchor);
    }

    /**
     * How the copy should be dated: today, today at the same hour, or the same
     * day and hour as the original.
     */
    private void askHowToDuplicate(View anchor) {
        final long which = selectedId;
        // A list, not a ring. Three ways of copying carry three sentences -
        // "mantieni data e ora" is not a word - and under a symbol in a ring
        // there is room for a word.
        java.util.List<MenuItemInfo> ways = new LinkedList<>();
        ways.add(new MenuItemInfo(1, R.string.duplicate_today, R.drawable.ic_action_copy));
        ways.add(new MenuItemInfo(2, R.string.duplicate_keep_time, R.drawable.ic_action_copy_keep_time));
        ways.add(new MenuItemInfo(3, R.string.duplicate_keep_date_time, R.drawable.ic_action_copy_keep_time));
        RowMenu.show(getActivity(), anchor != null ? anchor : getView(), ways, menuId -> {
            if (menuId == 1) {
                duplicateTransaction(which, 1);
            } else if (menuId == 2) {
                duplicateTransactionKeepTime(which);
            } else {
                duplicateTransactionKeepDateTime(which);
            }
        });
    }

    private void prepareAddButtonActionGrid() {
        addButtonActionGrid = new QuickActionGrid(getContext());
        // The same three marks the widget uses, drawn for this app: the ring
        // under the plus was the last place still showing pictures from 2010.
        addButtonActionGrid.addQuickAction(new MyQuickAction(getContext(), R.drawable.ic_widget_transaction, R.string.transaction));
        addButtonActionGrid.addQuickAction(new MyQuickAction(getContext(), R.drawable.ic_widget_transfer, R.string.transfer));
        if (addTemplateToAddButton()) {
            addButtonActionGrid.addQuickAction(new MyQuickAction(getContext(), R.drawable.ic_widget_templates, R.string.template));
        } else {
            addButtonActionGrid.setNumColumns(2);
        }
        addButtonActionGrid.setOnQuickActionClickListener(addButtonActionListener);
    }

    protected boolean addTemplateToAddButton() {
        return true;
    }

    private QuickActionWidget.OnQuickActionClickListener addButtonActionListener = (widget, position, action) -> {
        switch (position) {
            case 0:
                addItem(NEW_TRANSACTION_REQUEST, TransactionActivity.class);
                break;
            case 1:
                addItem(NEW_TRANSFER_REQUEST, TransferActivity.class);
                break;
            case 2:
                // With the tab on, this is the way to it; with the tab off the
                // templates are still there and this is the only way to them,
                // which is what used to make the button come and go.
                if (MyPreferences.isTemplatesAsTab(getContext())
                        && getActivity() instanceof MainActivity) {
                    io.github.mpstudios56.cifra.bus.GreenRobotBus_.getInstance_(getActivity())
                            .post(new io.github.mpstudios56.cifra.bus.SwitchToTab("templates"));
                } else {
                    createFromTemplate();
                }
                break;
        }
    };

    private void restoreTransaction(long selectedId) {
        new BlotterOperations(getContext(), this, db, selectedId).restoreTransaction();
        recreateCursor();
    }

    private void pendingTransaction(long selectedId) {
        new BlotterOperations(getContext(), this, db, selectedId).pendingTransaction();
        recreateCursor();
    }

    private void unreconcileTransaction(long selectedId) {
        new BlotterOperations(getContext(), this, db, selectedId).unreconcileTransaction();
        recreateCursor();
    }

    private void clearTransaction(long selectedId) {
        new BlotterOperations(getContext(), this, db, selectedId).clearTransaction();
        recreateCursor();
        saidSo(R.string.transaction_status_cleared);
    }

    private void reconcileTransaction(long selectedId) {
        new BlotterOperations(getContext(), this, db, selectedId).reconcileTransaction();
        recreateCursor();
        saidSo(R.string.transaction_status_reconciled);
    }

    /**
     * Says out loud what the state has become.
     * <p>
     * The state is otherwise shown by the colour of a strip eight pixels wide at
     * the left edge of the row, and two of the five states are two shades of the
     * same green. Somebody who taps the command and looks at the row cannot tell
     * whether anything happened at all.
     */
    private void saidSo(int statusId) {
        Toast.makeText(getContext(),
                getString(R.string.transaction_status_now, getString(statusId)),
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        blotterFilter.toBundle(outState);
        outState.putBoolean(MAIN_BLOTTER, mainBlotter);
    }

    /**
     * Left here by the widget when the templates have no tab of their own: the
     * movements screen picks it up the moment it is on show. A message on the
     * bus would need this screen to be listening, and it is not.
     */
    public static volatile boolean templatesWanted = false;

    private void openTemplatesIfAsked() {
        if (templatesWanted && mainBlotter && isAdded()) {
            templatesWanted = false;
            createFromTemplate();
        }
    }

    protected void createFromTemplate() {
        Intent intent = new Intent(getContext(), SelectTemplateActivity.class);
        startActivityForResult(intent, NEW_TRANSACTION_FROM_TEMPLATE_REQUEST);
    }

    @Override
    protected List<MenuItemInfo> createContextMenus(long id) {
        if (blotterFilter.isTemplate() || blotterFilter.isSchedule()) {
            return super.createContextMenus(id);
        } else {
            List<MenuItemInfo> menus = super.createContextMenus(id);
            menus.add(new MenuItemInfo(MENU_DUPLICATE, R.string.duplicate, R.drawable.ic_row_copy));
            menus.add(new MenuItemInfo(MENU_SAVE_AS_TEMPLATE, R.string.save_as_template, R.drawable.ic_tab_templates));
            menus.add(new MenuItemInfo(MENU_SHOW_IN_ACCOUNT_BLOTTER, R.string.transaction_show_in_account_blotter, R.drawable.ic_tab_accounts));
            Transaction t = db.getTransaction(id);
            if (t.isTransfer()) {
                menus.add(new MenuItemInfo(MENU_CHANGE_TO_TRANSACTION, R.string.change_to_transaction, R.drawable.ic_row_transaction));
            }
            else {
                menus.add(new MenuItemInfo(MENU_CHANGE_TO_TRANSFER, R.string.change_to_transfer, R.drawable.ic_row_transfer));
            }
            return menus;
        }
    }

    @Override
    public boolean onPopupItemSelected(int itemId, View view, int position, long id) {
        Transaction t;
        Account fromAccount, toAccount = null;

        if (!super.onPopupItemSelected(itemId, view, position, id)) {
            switch (itemId) {
                case MENU_DUPLICATE:
                    duplicateTransaction(id, 1);
                    return true;
                case MENU_SAVE_AS_TEMPLATE:
                    new BlotterOperations(getContext(), this, db, id).duplicateAsTemplate();
                    Toast.makeText(getContext(), R.string.save_as_template_success, Toast.LENGTH_SHORT).show();
                    return true;
                case MENU_SHOW_IN_ACCOUNT_BLOTTER:
                    t = db.getTransaction(id);
                    fromAccount = db.getAccount(t.fromAccountId);
                    Intent intent = new Intent(getContext(), BlotterActivity.class);
                    Criterion.eq(BlotterFilter.FROM_ACCOUNT_ID, String.valueOf(fromAccount.id))
                            .toIntent(fromAccount.title, intent);
                    intent.putExtra(BlotterFilterActivity.IS_ACCOUNT_FILTER, true);
                    intent.putExtra(GO_TO_TRANSACTION, id);
                    startActivity(intent);
                    return true;
                case MENU_CHANGE_TO_TRANSACTION:
                case MENU_CHANGE_TO_TRANSFER:
                    // transfers in database is always stored as
                    // from_account_id (negative amount) -> to_account_id (positive amount)
                    // when the blotter is showing to_account_id, we want converted transaction
                    // stay at the same account
                    long blotterAccountId = blotterFilter.getAccountId();
                    t = db.getTransaction(id);
                    fromAccount = db.getAccount(t.fromAccountId);

                    var attrsMap = db.getAllAttributesForTransaction(id);
                    var attrs = new LinkedList<TransactionAttribute>();
                    for (Map.Entry<Long, String> attr : attrsMap.entrySet()) {
                        var ta = new TransactionAttribute();
                        ta.attributeId = attr.getKey();
                        ta.value = attr.getValue();
                        attrs.add(ta);
                    }

                    if (t.isTransfer()) {
                        // transfer to transaction
                        toAccount = db.getAccount(t.toAccountId);

                        // two side of transfer is not in the same currency, keep foreign currency value
                        if (fromAccount.currency.id != toAccount.currency.id) {
                            if (t.fromAccountId == blotterAccountId) {
                                t.originalFromAmount = -t.toAmount;
                                t.originalCurrencyId = toAccount.currency.id;
                            }
                            else { // (t.toAccountId == blotterAccountId)
                                t.originalFromAmount = t.fromAmount;
                                t.originalCurrencyId = fromAccount.currency.id;
                            }
                        }

                        if (t.toAccountId == blotterAccountId) {
                            t.fromAccountId = blotterAccountId;
                            t.fromAmount = t.toAmount;
                            t.originalFromAmount *= -1;
                        }
                        t.toAccountId = 0;
                        t.toAmount = 0;
                    }
                    else {
                        // transaction to transfer

                        // if the transaction's account had transfer to other account,
                        // use the last used transfer target
                        if (t.fromAmount < 0 && fromAccount.lastAccountId != 0) {
                            toAccount = db.getAccount(fromAccount.lastAccountId);
                            if (toAccount != null) {
                                t.toAccountId = fromAccount.lastAccountId;
                            }
                        }
                        // (positive amount) look for accounts transferred to this account
                        // or (+/- amount) try to get a different account with same currency
                        if (toAccount == null) {
                            try (Cursor c = db.getAllActiveAccounts()) {
                                // positive amount - look for accounts previously transfer to this account
                                if (t.fromAmount > 0) {
                                    while (c.moveToNext()) {
                                        toAccount = EntityManager.loadFromCursor(c, Account.class);
                                        if (toAccount.lastAccountId == fromAccount.id) {
                                            t.toAccountId = toAccount.id;
                                            break;
                                        }
                                    }
                                    c.moveToFirst();
                                }
                                // negative amount / earlier block didn't found a suitable account
                                if (toAccount == null) {
                                    while (c.moveToNext()) {
                                        toAccount = EntityManager.loadFromCursor(c, Account.class);
                                        if (toAccount.id != fromAccount.id && toAccount.currency.id == fromAccount.currency.id) {
                                            t.toAccountId = toAccount.id;
                                            break;
                                        }
                                    }
                                }
                            }
                            // give up
                            if (toAccount == null) {
                                Toast.makeText(getContext(), R.string.no_suitable_account_for_transfer,
                                        Toast.LENGTH_SHORT).show();
                                return true;
                            }
                        }

                        // original entered foreign currency is same as transfer target
                        // use the value directly
                        if (t.originalCurrencyId == toAccount.currency.id) {
                            t.toAmount = -t.originalFromAmount;
                        }
                        else {
                            var rateProvider = db.getLatestRates();
                            var rate = rateProvider.getRate(fromAccount.currency, toAccount.currency);
                            if (rate != ExchangeRate.NA) {
                                t.toAmount = -(long) (t.fromAmount * rate.rate);
                            } else {
                                t.toAmount = -t.fromAmount;
                            }

                            t.toAmount = Utils.roundAmount(toAccount.currency, t.toAmount);
                        }

                        t.originalFromAmount = 0;
                        t.originalCurrencyId = 0;

                        if (t.fromAmount > 0) {
                            var tempAccountId = t.fromAccountId;
                            t.fromAccountId = t.toAccountId;
                            t.toAccountId = tempAccountId;

                            var tempAmount = t.toAmount;
                            t.toAmount = t.fromAmount;
                            t.fromAmount = tempAmount;
                        }

                        if (!MyPreferences.isShowPayeeInTransfers()) {
                            t.payeeId = 0;
                        }
                        if (!MyPreferences.isShowCategoryInTransferScreen()) {
                            t.categoryId = 0;
                        }
                    }
                    // delete then insert to properly update running balance
                    db.deleteTransaction(t.id);
                    t.id = -1;
                    db.insertOrUpdate(t, attrs);
                    recreateCursor();
                    return true;
            }
        }
        return false;
    }

    protected long duplicateTransactionKeepTime(long id) {
        return duplicateTransaction(id, 1, KeepTime.KEEP_TIME);
    }

    protected long duplicateTransactionKeepDateTime(long id) {
        return duplicateTransaction(id, 1, KeepTime.KEEP_DATE_TIME);
    }

    protected long duplicateTransaction(long id, int multiplier) {
        return duplicateTransaction(id, multiplier, KeepTime.NONE);
    }

    enum KeepTime {
        NONE,
        KEEP_TIME,
        KEEP_DATE_TIME,
    }

    protected long duplicateTransaction(long id, int multiplier, KeepTime keepTime) {
        long newId;
        String toastText;
        if (keepTime == KeepTime.KEEP_TIME) {
            newId = new BlotterOperations(getContext(), this, db, id).duplicateTransactionKeepTime();
            toastText = getString(R.string.duplicate_success_keep_time);
        }
        else if (keepTime == KeepTime.KEEP_DATE_TIME) {
            newId = new BlotterOperations(getContext(), this, db, id).duplicateTransactionKeepDateTime();
            toastText = getString(R.string.duplicate_success_keep_date_time);
        }
        else {
            newId = new BlotterOperations(getContext(), this, db, id).duplicateTransaction(multiplier);
            toastText = getString(R.string.duplicate_success);
        }
        if (multiplier > 1) {
            toastText = getString(R.string.duplicate_success_with_multiplier, multiplier);
        }
        Toast.makeText(getContext(), toastText, Toast.LENGTH_LONG).show();
        duplicatedTransactionId = newId;
        recreateCursor();
        AccountWidget.updateWidgets(getContext());
        return newId;
    }

    @Override
    protected void addItem() {
        if (showAllBlotterButtons) {
            addItem(NEW_TRANSACTION_REQUEST, TransactionActivity.class);
        } else {
            addButtonActionGrid.show(bAdd);
        }
    }

    protected void addItem(int requestId, Class<? extends AbstractTransactionActivity> clazz) {
        Intent intent = new Intent(getContext(), clazz);
        long accountId = blotterFilter.getAccountId();
        if (accountId != -1) {
            intent.putExtra(TransactionActivity.ACCOUNT_ID_EXTRA, accountId);
        }
        else {
            long budgetId = blotterFilter.getBudgetId();
            if (budgetId != -1) {
                Budget budget = db.load(Budget.class, budgetId);
                if (budget.account != null) {
                    intent.putExtra(TransactionActivity.ACCOUNT_ID_EXTRA, budget.account.id);
                }
            }
        }

        intent.putExtra(TransactionActivity.TEMPLATE_EXTRA, blotterFilter.getIsTemplate());
        startActivityForResult(intent, requestId);
    }

    @Override
    protected Cursor loadInBackground() {
        Log.d(TAG, "loadInBackground start");
        long t1 = System.nanoTime();

        Cursor c;
        blotterFilter.recalculatePeriod();
        WhereFilter blotterFilterCopy = WhereFilter.copyOf(blotterFilter);
        // The totals are worked out from the question as it was asked, before
        // the folded years are taken out of it. Folding a year is a way of
        // reading the list, not a way of changing what is in it: the balance at
        // the foot must not move because somebody tidied the view.
        WhereFilter forTotals = WhereFilter.copyOf(blotterFilterCopy);

        new Handler(Looper.getMainLooper()).post(()-> {
            emptyText.setVisibility(View.GONE);
            progressBar.setVisibility(View.VISIBLE);
            calculateTotals(forTotals);
        });

        if (db == null) {
            db = new DatabaseAdapter(getActivity());
            db.open();
        }
        this.lastTxId = db.getLastTransactionId();
        this.lastDay = Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
        long t2 = System.nanoTime();
        Log.d(TAG, "getLastTransactionId() = " + lastTxId + ", " + format("%,d", (t2 - t1)) + " ns");
        leaveOutFoldedYears(blotterFilterCopy);
        long accountId = blotterFilterCopy.getAccountId();
        if (accountId != -1) {
            c = db.getBlotterForAccount(blotterFilterCopy);
        } else {
            c = db.getBlotter(blotterFilterCopy);
        }

        Log.d(TAG, "loadInBackground: " + format("%,d", (System.nanoTime() - t1)) + " ns");
        return c;
    }

    @Override
    protected ListAdapter createAdapter(Context context, Cursor cursor) {
        boolean isNewAdapter;
        long t1 = System.nanoTime();

        if (adapter == null) {
            isNewAdapter = true;
            long accountId = blotterFilter.getAccountId();
            if (accountId != -1) {
                adapter = new TransactionsListAdapter(context, db, cursor);
            } else {
                adapter = new BlotterListAdapter(context, db, cursor);
            }
            // Only where the list is a list of movements: a line saying
            // "2024" across the templates says nothing at all.
            if (!blotterFilter.isTemplate() && !blotterFilter.isSchedule()) {
                // Only where the rows are movements with a date behind them.
            boolean movements = !blotterFilter.isTemplate() && !blotterFilter.isSchedule();
            ((BlotterListAdapter) adapter).showYears(movements);
            if (movements) {
                wireYearFolding((BlotterListAdapter) adapter);
                checkFoldedYearsSurvived(cursor);
            }
            }
        }
        else {
            isNewAdapter = false;
        }

        Application.getExecutor().execute(() -> {
            var activity = getActivity();
            if (activity == null) return;

            // at 2026-07-13, my database has ~33500 transactions and this takes ~200 ms
            // first getCount() actually calculates record count, subsequent calls are cached
            var count = cursor.getCount();

            activity.runOnUiThread(() -> {
                if (count == 0) {
                    emptyText.setVisibility(View.VISIBLE);
                }
                progressBar.setVisibility(View.GONE);
                Log.d(TAG, "createAdapter: " + format("%,d", System.nanoTime() - t1) + " ns");

                updatePeriodDisplay();

                if (isNewAdapter) {
                    setListAdapterKeepScrollState(adapter);
                }
                else {
                    var a = (BlotterListAdapter) adapter;
                    Cursor old = a.swapCursor(cursor);
                    if (old != null && !old.isClosed()) {
                        Log.d(TAG, "createAdapter: closing old " + old);
                        old.close();
                    }
                }


                long txId = -1;
                Bundle args = getArguments();
                if (args != null) {
                    txId = args.getLong(GO_TO_TRANSACTION, -1);
                    args.remove(GO_TO_TRANSACTION);
                }

                if (duplicatedTransactionId != -1) {
                    Log.d(TAG, "get duplicatedTransactionId = " + duplicatedTransactionId);
                //    txId = duplicatedTransactionId;
                    duplicatedTransactionId = -1;
                }

                if (txId != -1) {
                    ((BlotterListAdapter) adapter).setHighlightTransactionId(txId);
                    int pos = 0;
                    cursor.moveToFirst();
                    while (cursor.getLong(0) != txId && !cursor.isAfterLast()) {
                        cursor.moveToNext();
                        pos += 1;
                    }
                    setSelection(pos);
                }
            });
        });

        return null;
    }

    protected void updatePeriodDisplay() {
        DateTimeCriterion c = blotterFilter.getDateTime();
        if (c != null) {
            period.setVisibility(View.VISIBLE);
            period.setText(DateUtils.formatDateRange(getContext(), c.getLongValue1(), c.getLongValue2(),
                    DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_ABBREV_MONTH));
        }
        else {
            period.setText(R.string.no_filter);
            period.setVisibility(View.GONE);
        }
    }

    @Override
    public void onLoaderReset(@NonNull Loader<Cursor> loader) {
        if (adapter instanceof CursorAdapter cursorAdapter) {
            Cursor oldCursor = cursorAdapter.swapCursor(null);
            if (oldCursor != null && !oldCursor.isClosed()) {
                Log.d(TAG, "onLoaderReset: closing " + oldCursor);
                oldCursor.close();
            }
        }
    }

    @Override
    protected void deleteItem(View v, int position, final long id) {
        deleteTransaction(id);
    }

    private void deleteTransaction(long id) {
        new BlotterOperations(getContext(), this, db, id).deleteTransaction();
    }

    public void afterDeletingTransaction(long id) {
        recreateCursor();
        AccountWidget.updateWidgets(getContext());
    }

    @Override
    public void editItem(View v, int position, long id) {
        editTransaction(id);
    }

    private void editTransaction(long id) {
        new BlotterOperations(getContext(), this, db, id).editTransaction();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(getClass().getSimpleName(), "onActivityResult requestCode=" + requestCode + " resultCode=" + resultCode);

        if (requestCode == FILTER_REQUEST) {
            if (resultCode == RESULT_FIRST_USER) {
                blotterFilter.clear();
            } else if (resultCode == RESULT_OK) {
                blotterFilter = WhereFilter.fromIntent(data);
            }
            saveFilter();
            applyFilter();
        } else if (resultCode == RESULT_OK && requestCode == NEW_TRANSACTION_FROM_TEMPLATE_REQUEST) {
            // do nothing - transaction is created in templacte list activity
        }
        if (resultCode == RESULT_OK || resultCode == RESULT_FIRST_USER) {
            Log.d(getClass().getSimpleName(), "RESULT_OK || RESULT_FIRST_USER");
        }
        recreateCursor();
    }

    private void saveFilter() {
        if (!mainBlotter) return;
        SharedPreferences preferences = getContext().getSharedPreferences(this.getClass().getName(), 0);
        blotterFilter.toSharedPreferences(preferences);
    }

    protected void applyFilter() {
        long accountId = blotterFilter.getAccountId();
        if (accountId != -1) {
            Account a = db.getAccount(accountId);
            bAdd.setVisibility(a != null && a.isActive ? View.VISIBLE : View.GONE);
            if (showAllBlotterButtons) {
                bTransfer.setVisibility(a != null && a.isActive ? View.VISIBLE : View.GONE);
            }
        }
        String title = blotterFilter.getTitle();
        if (title != null && !title.isEmpty()) {
            ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
            if (actionBar != null) {
                actionBar.setTitle(title);
                actionBar.setSubtitle(R.string.blotter);
            }
        }
        updateFilterImage();
    }

    protected void updateFilterImage() {
        if (!filterButtonFilters()) {
            return;
        }
        FilterState.updateFilterColor(getContext(), blotterFilter, bFilter);
    }

    /**
     * Whether the button at that place in the bar is a filter at all: the
     * templates and the scheduled movements borrow it to hold their order.
     */
    protected boolean filterButtonFilters() {
        return true;
    }

    /**
     * The account this list is showing, or zero when it is showing them all.
     * <p>
     * Read from the filter, which is what the list is actually drawn from, so
     * it cannot disagree with what is on screen.
     */
    private long accountBeingLookedAt() {
        try {
            return blotterFilter == null ? 0 : blotterFilter.getAccountId();
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    protected void onItemClick(View v, int position, long id) {
        // Straight to the card, the way tapping "Informazioni" always did.
        showTransactionInfo(id);
    }

    /**
     * Held down: the ring of symbols, carrying everything that used to be split
     * between it and the list menu - duplicate, save as template, show in the
     * account, change between movement and transfer - each of them once.
     */
    @Override
    protected boolean onItemLongClick(View v, int position, long id) {
        selectedId = id;
        rowTouched = v;
        prepareTransactionActionGrid();
        transactionActionGrid.show(v);
        return true;
    }

    @Override
    protected void viewItem(View v, int position, long id) {
        showTransactionInfo(id);
    }

    private void showTransactionInfo(long id) {
        TransactionInfoDialog transactionInfoView = new TransactionInfoDialog(getContext(), db, inflater);
        transactionInfoView.show(getContext(), this, id);
    }

    @Override
    public void integrityCheck() {
        Log.d(TAG, "integrityCheck");
        new IntegrityCheckTask(this).execute(new IntegrityCheckRunningBalance(getContext()));
    }

    public boolean onBackPressed()
    {
        FrameLayout searchLayout = getView().findViewById(R.id.search_text_frame);
        if (searchLayout != null && searchLayout.getVisibility() == View.VISIBLE) {
            searchLayout.setVisibility(View.GONE);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        readSwipeSettings();
        openTemplatesIfAsked();
        Log.d(TAG, "onResume");
        // Re-read on every return: this fragment is cached by the pager and is not
        // rebuilt when the templates tab is switched on or off in the preferences,
        // so the button would stay hidden after the tab was turned back off.
        if (bTemplate != null) {
            bTemplate.setVisibility(MyPreferences.isTemplatesAsTab(getContext())
                    ? View.GONE : View.VISIBLE);
        }
        if (lastTxId != BEFORE_INITIAL_LOAD) {
            Application.getExecutor().execute(() -> {
                long t1 = System.nanoTime();
                long currentLastTxId = db.getLastTransactionId();
                Log.d(TAG, "getLastTransactionId() = " + lastTxId + ", " + format("%,d", System.nanoTime() - t1) + " ns");
                long currentDay = Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
                if (currentLastTxId != lastTxId || currentDay != lastDay) {
                    Log.d(TAG, "lastTxId " + lastTxId + " != " + currentLastTxId +
                            " || lastDay " + lastDay + " != " + currentDay + ", recreating cursor");
                    new Handler(Looper.getMainLooper()).post(this::recreateCursor);
                }
            });
        }

        if (PinProtection.isUnlocked()) {
            Log.d(this.getClass().getSimpleName(), "onResume isUnlocked, show list");
            getListView().setVisibility(View.VISIBLE);
        }
        else {
            // still locked, don't show account list balances
            Log.d(this.getClass().getSimpleName(), "onResume NOT isUnlocked, hide list");
            getListView().setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void onDestroy() {
        if (calculationTask != null) {
            calculationTask.stop();
            calculationTask.cancel(true);
        }
        super.onDestroy();
    }

    // ------------------------------------------------------------- scorrimento

    /** What dragging a row to the right and to the left does, read once. */
    private io.github.mpstudios56.cifra.utils.SwipeAction swipeRight =
            io.github.mpstudios56.cifra.utils.SwipeAction.NONE;
    private io.github.mpstudios56.cifra.utils.SwipeAction swipeLeft =
            io.github.mpstudios56.cifra.utils.SwipeAction.NONE;

    /**
     * Lets a movement be dragged sideways, if either direction has been given
     * something to do.
     */
    private void attachSwipe() {
        readSwipeSettings();
        io.github.mpstudios56.cifra.view.SwipeOnRows.attach(getListView(),
                new io.github.mpstudios56.cifra.view.SwipeOnRows.Handler() {
                    @Override
                    public boolean canSwipe(int position, long id) {
                        return id > 0;
                    }

                    @Override
                    public int colourFor(boolean toTheRight) {
                        return (toTheRight ? swipeRight : swipeLeft).colour;
                    }

                    @Override
                    public int iconFor(boolean toTheRight) {
                        return (toTheRight ? swipeRight : swipeLeft).iconId;
                    }

                    @Override
                    public void swiped(int position, long id, boolean toTheRight) {
                        doSwipe(id, toTheRight ? swipeRight : swipeLeft);
                    }
                });
    }

    /**
     * Carries out what the direction was set to, and offers the way back.
     * <p>
     * Everything except opening the editor can be undone, because a swipe is a
     * gesture one makes by accident: without the way back it becomes something
     * to be careful of rather than something to use.
     */
    /**
     * Read on every return to the screen, not once when it was built.
     * <p>
     * Changing the setting and coming back does not build this screen again, so
     * read once meant the new choice took hold at some unguessable later moment
     * - whenever the screen happened to be thrown away and made afresh.
     */
    private void readSwipeSettings() {
        swipeRight = MyPreferences.getSwipeRight();
        swipeLeft = MyPreferences.getSwipeLeft();
    }

    private void doSwipe(long id, io.github.mpstudios56.cifra.utils.SwipeAction action) {
        switch (action) {
            case NONE:
                return;
            case EDIT:
                editTransaction(id);
                return;
            case DUPLICATE: {
                // The same question the menu asks, for the same reason: which
                // day the copy belongs to is not something to settle once in
                // the settings and never think about again.
                selectedId = id;
                askHowToDuplicate(anchorFor(null));
                return;
            }
            case CLEAR:
            case RECONCILE: {
                Transaction t = db.getTransaction(id);
                if (t == null) {
                    return;
                }
                final TransactionStatus before = t.status;
                db.updateTransactionStatus(id, action
                        == io.github.mpstudios56.cifra.utils.SwipeAction.CLEAR
                        ? TransactionStatus.CL : TransactionStatus.RC);
                recreateCursor();
                offerUndo(action, () -> {
                    db.updateTransactionStatus(id, before);
                    recreateCursor();
                });
                return;
            }
            case DELETE: {
                final Transaction t = db.getTransaction(id);
                if (t == null) {
                    return;
                }
                // Everything hanging off it is read before it goes, so that the
                // way back puts back the whole movement and not its skeleton.
                final List<TransactionAttribute> attributes = new LinkedList<>();
                for (Map.Entry<Long, String> e : db.getAllAttributesForTransaction(id).entrySet()) {
                    TransactionAttribute a = new TransactionAttribute();
                    a.attributeId = e.getKey();
                    a.value = e.getValue();
                    attributes.add(a);
                }
                final List<Transaction> splits = t.isSplitParent()
                        ? db.getSplitsForTransaction(id) : null;
                db.deleteTransaction(id);
                recreateCursor();
                offerUndo(action, () -> {
                    t.id = -1;
                    if (splits != null) {
                        for (Transaction split : splits) {
                            split.id = -1;
                        }
                        t.splits = splits;
                    }
                    db.insertOrUpdate(t, attributes);
                    recreateCursor();
                });
            }
        }
    }

    /** The line at the foot of the screen that gives a few seconds to think again. */
    private void offerUndo(io.github.mpstudios56.cifra.utils.SwipeAction action, Runnable back) {
        View view = getView();
        if (view == null) {
            return;
        }
        com.google.android.material.snackbar.Snackbar
                .make(view, getString(action.doneId),
                        com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                .setAction(R.string.undo, v -> back.run())
                .show();
    }


    /** The years folded away, for as long as this screen is open. */
    /**
     * Which years are closed.
     * <p>
     * Kept in writing rather than in memory: somebody closes twenty years,
     * opens the two they are working in, and expects to find exactly that when
     * they come back - not the whole ledger open again because they had walked
     * over to the accounts for a moment.
     */
    private final java.util.Set<Integer> foldedYears = new java.util.HashSet<>();

    /** Where the choice is written, one note per list. */
    private String foldedYearsKey() {
        return "folded_years_" + (mainBlotter ? "main" : String.valueOf(blotterFilter.getAccountId()));
    }

    private void rememberFoldedYears() {
        if (getContext() == null) {
            return;
        }
        // Year and marker together. Writing down the year alone was what broke
        // it: on the next opening the year was left out of the question with
        // nothing kept visible, so it vanished with no line to touch and no way
        // back.
        java.util.Set<String> scritti = new java.util.HashSet<>();
        for (Integer year : foldedYears) {
            Long segno = foldMarker.get(year);
            scritti.add(year + "|" + (segno == null ? 0L : segno));
        }
        getContext().getSharedPreferences(getClass().getName(), 0).edit()
                .putStringSet(foldedYearsKey(), scritti).apply();
    }

    private void recallFoldedYears() {
        if (getContext() == null || foldedYearsRecalled) {
            return;
        }
        foldedYearsRecalled = true;
        java.util.Set<String> scritti = getContext()
                .getSharedPreferences(getClass().getName(), 0)
                .getStringSet(foldedYearsKey(), null);
        if (scritti == null) {
            return;
        }
        for (String riga : scritti) {
            try {
                int taglio = riga.indexOf('|');
                if (taglio < 0) {
                    // Scritto dalla versione precedente, senza segnalibro: si
                    // lascia perdere, perche' senza segnalibro l'anno sparisce.
                    continue;
                }
                int year = Integer.parseInt(riga.substring(0, taglio));
                long segno = Long.parseLong(riga.substring(taglio + 1));
                if (segno == 0) {
                    continue;
                }
                foldedYears.add(year);
                foldMarker.put(year, segno);
            } catch (NumberFormatException storto) {
                // una riga scritta male non deve impedire di leggere le altre
            }
        }
    }

    private boolean foldedYearsRecalled = false;
    /**
     * The one movement of each folded year that stays in the list.
     * <p>
     * Folding used to leave every row where it was and merely hide it. The list
     * still had to measure a thousand hidden rows, which is why it took a
     * moment to answer, and it still drew a line between each pair of them,
     * which is the empty band that stayed behind. Now the year leaves the
     * question altogether except for its newest movement, which is the row the
     * year line is drawn on - without it there would be nothing left to touch
     * to get the year back.
     */
    private final java.util.Map<Integer, Long> foldMarker = new java.util.HashMap<>();

    private void wireYearFolding(BlotterListAdapter a) {
        recallFoldedYears();
        a.setYearFolding(foldedYears, year -> {
            if (foldedYears.remove(year)) {
                foldMarker.remove(year);
                rememberFoldedYears();
            } else {
                foldedYears.add(year);
                foldMarker.put(year, newestOf(year, a));
                rememberFoldedYears();
            }
            recreateCursor();
            if (getActivity() != null) {
                TodayButton.showYearsClosed(getActivity(), !foldedYears.isEmpty());
            }
        });
    }

    /** The newest movement of that year in the list as it stands. */
    private long newestOf(int year, BlotterListAdapter a) {
        Cursor c = a.getCursor();
        if (c == null) {
            return 0;
        }
        int was = c.getPosition();
        try {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            for (int i = 0; i < c.getCount(); i++) {
                if (!c.moveToPosition(i)) {
                    break;
                }
                long when = c.getLong(DatabaseHelper.BlotterColumns.datetime.ordinal());
                cal.setTimeInMillis(when);
                if (cal.get(java.util.Calendar.YEAR) == year) {
                    return when;
                }
            }
        } catch (Exception e) {
            return 0;
        } finally {
            c.moveToPosition(was);
        }
        return 0;
    }

    /**
     * Checks that every closed year still has its line on screen.
     * <p>
     * The marker is a movement, and a movement can be deleted or filtered out.
     * When that happens the year would be left out of the question with nothing
     * to touch, so it is opened again rather than disappearing.
     */
    private void checkFoldedYearsSurvived(Cursor c) {
        if (foldedYears.isEmpty() || c == null || c.isClosed()) {
            return;
        }
        java.util.Set<Integer> visti = new java.util.HashSet<>();
        int was = c.getPosition();
        java.util.Calendar cal = java.util.Calendar.getInstance();
        for (int i = 0; i < c.getCount(); i++) {
            if (!c.moveToPosition(i)) {
                break;
            }
            cal.setTimeInMillis(c.getLong(DatabaseHelper.BlotterColumns.datetime.ordinal()));
            visti.add(cal.get(java.util.Calendar.YEAR));
        }
        c.moveToPosition(was);
        boolean cambiato = false;
        for (java.util.Iterator<Integer> i = foldedYears.iterator(); i.hasNext(); ) {
            Integer year = i.next();
            if (!visti.contains(year)) {
                i.remove();
                foldMarker.remove(year);
                cambiato = true;
            }
        }
        if (cambiato) {
            rememberFoldedYears();
            recreateCursor();
        }
    }

    /** Adds to the question the years that are not to be answered. */
    private void leaveOutFoldedYears(WhereFilter filter) {
        for (Integer year : foldedYears) {
            java.util.Calendar from = java.util.Calendar.getInstance();
            from.clear();
            from.set(year, 0, 1);
            java.util.Calendar to = (java.util.Calendar) from.clone();
            to.add(java.util.Calendar.YEAR, 1);
            Long keep = foldMarker.get(year);
            filter.put(io.github.mpstudios56.cifra.filter.Criterion.raw(
                    "not (datetime >= " + from.getTimeInMillis()
                            + " and datetime < " + to.getTimeInMillis()
                            + (keep == null || keep == 0 ? "" : " and datetime <> " + keep)
                            + ")"));
        }
    }

    /**
     * To the first movement ever written down: the far end of the list, which
     * is the bottom or the top depending on which way it is sorted.
     */
    public void goToOldest() {
        if (adapter == null) {
            return;
        }
        int count = adapter.getCount();
        if (count == 0) {
            return;
        }
        boolean asc = blotterFilter.getSortOrder().contains(BlotterFilter.SORT_OLDER_TO_NEWER);
        setSelection(asc ? 0 : count - 1);
    }

    /**
     * Closes every year at once - or opens them all again when they are already
     * closed. On a ledger that goes back years, the short view is the one
     * somebody wants first.
     */
    public void foldEveryYear() {
        if (adapter == null) {
            return;
        }
        Cursor c = ((BlotterListAdapter) adapter).getCursor();
        if (c == null || c.getCount() == 0) {
            return;
        }
        java.util.Set<Integer> years = new java.util.HashSet<>();
        int was = c.getPosition();
        java.util.Calendar cal = java.util.Calendar.getInstance();
        for (int i = 0; i < c.getCount(); i++) {
            if (!c.moveToPosition(i)) {
                break;
            }
            cal.setTimeInMillis(c.getLong(DatabaseHelper.BlotterColumns.datetime.ordinal()));
            years.add(cal.get(java.util.Calendar.YEAR));
        }
        c.moveToPosition(was);
        if (!years.isEmpty() && foldedYears.containsAll(years)) {
            foldedYears.clear();
            foldMarker.clear();
            rememberFoldedYears();
        } else {
            for (Integer year : years) {
                if (!foldedYears.contains(year)) {
                    foldedYears.add(year);
                    foldMarker.put(year, newestOf(year, (BlotterListAdapter) adapter));
                }
            }
        }
        rememberFoldedYears();
        recreateCursor();
        if (getActivity() != null) {
            TodayButton.showYearsClosed(getActivity(), !foldedYears.isEmpty());
        }
    }

    /** Up to the top of the list: the newest movement of all. */
    public void goToTop() {
        if (adapter == null || adapter.getCount() == 0) {
            return;
        }
        boolean asc = blotterFilter.getSortOrder().contains(BlotterFilter.SORT_OLDER_TO_NEWER);
        setSelection(asc ? adapter.getCount() - 1 : 0);
    }

    /** Scrolls the list to today, or to the nearest movement before it. */
    public void goToToday() {
        if (adapter == null) return;
        var cursor = ((BlotterListAdapter) adapter).getCursor();
        if (cursor == null || cursor.getCount() == 0) return;

                Calendar today = Calendar.getInstance();
                today.set(Calendar.HOUR, 0);
                today.set(Calendar.MINUTE, 0);
                today.set(Calendar.SECOND, 0);
                today.set(Calendar.MILLISECOND, 0);
                long todayStart = today.getTimeInMillis();
                long todayEnd = todayStart + 86400000;
                boolean asc = (blotterFilter.getSortOrder().contains(BlotterFilter.SORT_OLDER_TO_NEWER));
                int pos;
                if (asc) {
                    cursor.moveToLast();
                    pos = cursor.getCount() - 1;
                }
                else {
                    cursor.moveToFirst();
                    pos = 0;
                }
                while (!cursor.isAfterLast() && !cursor.isBeforeFirst()) {
                    long txTime = cursor.getLong(DatabaseHelper.BlotterColumns.datetime.ordinal());
                    if (txTime < todayEnd) {
                        break;
                    }
                    if (asc) {
                        cursor.moveToPrevious();
                        pos -= 1;
                    }
                    else {
                        cursor.moveToNext();
                        pos += 1;
                    }
                }
                        setSelection(pos);
    }

}
