package io.github.mpstudios56.cifra.activity;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.LightingColorFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MenuInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import greendroid.widget.QuickActionGrid;
import greendroid.widget.QuickActionWidget;
import io.github.mpstudios56.cifra.R;
import io.github.mpstudios56.cifra.adapter.AccountRecyclerAdapter;
import io.github.mpstudios56.cifra.blotter.BlotterFilter;
import io.github.mpstudios56.cifra.blotter.TotalCalculationTask;
import io.github.mpstudios56.cifra.bus.GreenRobotBus_;
import io.github.mpstudios56.cifra.bus.SwitchToMenuTabEvent;
import io.github.mpstudios56.cifra.db.DatabaseAdapter;
import io.github.mpstudios56.cifra.dialog.AccountInfoDialog;
import io.github.mpstudios56.cifra.filter.Criterion;
import io.github.mpstudios56.cifra.model.Account;
import io.github.mpstudios56.cifra.model.AccountSeparator;
import io.github.mpstudios56.cifra.model.Total;
import io.github.mpstudios56.cifra.utils.IntegrityCheckAutobackup;
import io.github.mpstudios56.cifra.utils.MenuItemInfo;
import io.github.mpstudios56.cifra.utils.MyPreferences;
import io.github.mpstudios56.cifra.utils.TotalsPopup;
import io.github.mpstudios56.cifra.utils.PinProtection;
import io.github.mpstudios56.cifra.utils.Utils;
import io.github.mpstudios56.cifra.view.NodeInflater;

public class AccountRecyclerFragment extends AbstractRecyclerViewFragment
        implements RefreshSupportedActivity
{
    private static final String TAG = "AccountRecyclerFragment";

    private static final String FILTER_PERF = "filter";

    private static final int NEW_ACCOUNT_REQUEST = 1;

    public static final int EDIT_ACCOUNT_REQUEST = 2;
    private static final int VIEW_ACCOUNT_REQUEST = 3;
    private static final int PURGE_ACCOUNT_REQUEST = 4;
    private static final int SHOW_TOTALS_REQUEST = 5;

    private QuickActionWidget accountActionGrid;
    private TextView emptyText;
    private ProgressBar progressBar;
    private ImageButton bSearch;
    private ImageButton bShowSortOrder;
    private String filter;
    private boolean showSortOrder;

    private long selectedId = -1;

    public AccountRecyclerFragment() {
        super(R.layout.account_recyclerview);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ViewCompat.setOnApplyWindowInsetsListener(view.findViewById(R.id.bottom_bar), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.statusBars()
                    | WindowInsetsCompat.Type.captionBar()
                    | WindowInsetsCompat.Type.ime());
            v.setPadding(0, insets.top, 0, 0);
            return WindowInsetsCompat.CONSUMED;
        });

        setupUi(view);
        setupMenuButton();
        calculateTotals();
        integrityCheck();
    }

    private void setupUi(View view) {
        EditText searchText = view.findViewById(R.id.search_text);
        FrameLayout searchLayout = view.findViewById(R.id.search_text_frame);
        ImageButton clearButton = view.findViewById(R.id.search_text_clear);

        view.findViewById(R.id.integrity_error).setOnClickListener(v -> v.setVisibility(View.GONE));

        emptyText = view.findViewById(android.R.id.empty);
        progressBar = view.findViewById(android.R.id.progress);

        bSearch = view.findViewById(R.id.bSearch);
        // Taken out of this screen. The accounts of a household fit on one
        // page: there is nothing here to look for, and the box it opened was
        // the last one still fighting with the keyboard.
        if (bSearch != null) {
            bSearch.setVisibility(View.GONE);
        }
        loadFilter();
        if (false && bSearch != null) {

            if (!filter.isEmpty()) {
                searchLayout.setVisibility(View.VISIBLE);
                clearButton.setVisibility(View.VISIBLE);
                searchText.setText(filter);
                bSearch.setColorFilter(new LightingColorFilter(Color.BLACK, getResources().getColor(R.color.holo_blue_dark)));
            }

            bSearch.setOnClickListener(method -> {
                InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);

                searchText.setOnFocusChangeListener((v, b) -> {
                    if (!v.hasFocus()) {
                        imm.hideSoftInputFromWindow(searchLayout.getWindowToken(), 0);
                    }
                });

                clearButton.setOnClickListener(v -> {
                    searchText.setText("");
                });

                if (searchLayout.getVisibility() == View.VISIBLE) {
                    imm.hideSoftInputFromWindow(searchLayout.getWindowToken(), 0);
                    searchLayout.setVisibility(View.GONE);
                    return;
                }

                searchLayout.setVisibility(View.VISIBLE);
                searchText.requestFocusFromTouch();
                imm.showSoftInput(searchText, InputMethodManager.SHOW_IMPLICIT);

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
                        filter = text;

                        if (!text.isEmpty()) {
                            clearButton.setVisibility(View.VISIBLE);
                            bSearch.setColorFilter(new LightingColorFilter(Color.BLACK, getResources().getColor(R.color.holo_blue_dark)));
                        } else {
                            clearButton.setVisibility(View.GONE);
                            bSearch.setColorFilter(null);
                        }

                        recreateCursor();
                        saveFilter();
                    }
                });
            });
        }

        setupMainAccountsButton(view);
        setupSeparatorButtons(view);

        // The order of the accounts is asked from the navigation bar now, where
        // the other buttons that change how a list is read already live.
        bShowSortOrder = view.findViewById(R.id.bShowSortOrder);
        if (bShowSortOrder != null) {
            bShowSortOrder.setVisibility(View.GONE);
        }
    }

    /** Shows or hides the place each account holds in the order. */
    public void toggleSortOrder() {
        showSortOrder = !showSortOrder;
        if (getActivity() != null) {
            TodayButton.showSortOn(getActivity(), showSortOrder);
        }
        recreateCursor();
    }

    /**
     * Which accounts money moves through every day.
     * <p>
     * Asked once, from the mark above the list, and answered by ticking them
     * off: from then on those accounts stand at the top of every list a
     * movement offers, under a heading of their own.
     */
    private void setupMainAccountsButton(View view) {
        ImageButton button = view.findViewById(R.id.bMainAccounts);
        if (button == null) {
            return;
        }
        showMainMark(button);
        button.setOnClickListener(v -> askWhichAreMain(button));
    }

    private void showMainMark(ImageButton button) {
        boolean any = false;
        for (Account a : db.getAllAccountsList()) {
            if (a.isMain) {
                any = true;
                break;
            }
        }
        button.setImageResource(any
                ? R.drawable.ic_main_accounts_on : R.drawable.ic_main_accounts);
    }

    private void askWhichAreMain(ImageButton button) {
        final List<Account> accounts = db.getAllAccountsList();
        if (accounts.isEmpty()) {
            return;
        }
        final String[] titles = new String[accounts.size()];
        final boolean[] chosen = new boolean[accounts.size()];
        for (int i = 0; i < accounts.size(); i++) {
            titles[i] = accounts.get(i).title;
            chosen[i] = accounts.get(i).isMain;
        }
        DialogAnswers.show(new androidx.appcompat.app.AlertDialog.Builder(
                getContext(), R.style.CifraChoiceDialog)
                .setTitle(R.string.main_accounts)
                .setMultiChoiceItems(titles, chosen, (dialog, which, isChecked) -> chosen[which] = isChecked)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    for (int i = 0; i < accounts.size(); i++) {
                        Account a = accounts.get(i);
                        if (a.isMain != chosen[i]) {
                            a.isMain = chosen[i];
                            db.saveAccount(a);
                        }
                    }
                    showMainMark(button);
                    recreateCursor();
                })
                .setNegativeButton(R.string.cancel, null)
                .create());
    }

    /**
     * The button that writes a heading into the list.
     * <p>
     * A heading is fastened to an account and stands above it, so making one is
     * two answers: what it is called, and where it begins. Asked in that order,
     * because the name is the part somebody has already decided.
     */
    private void setupSeparatorButtons(View view) {
        ImageButton add = view.findViewById(R.id.bAddSeparator);
        if (add != null) {
            add.setOnClickListener(v -> askForSeparatorName(null));
        }
    }

    /**
     * Folds every group away, or opens them all.
     * <p>
     * One touch does whichever is not already the case: with anything open it
     * closes the lot, and with everything closed it opens them.
     */
    public void foldEveryGroup() {
        boolean fold = anySeparatorOpen();
        db.setAllAccountSeparatorsFolded(fold);
        if (getActivity() != null) {
            TodayButton.showGroupsFolded(getActivity(), fold);
        }
        recreateCursor();
    }

    private boolean anySeparatorOpen() {
        for (AccountSeparator s : db.getAccountSeparators().values()) {
            if (!s.folded) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param existing the heading being renamed, or null when making a new one
     */
    private void askForSeparatorName(final AccountSeparator existing) {
        final android.widget.EditText field = new android.widget.EditText(getContext());
        field.setSingleLine(true);
        field.setHint(R.string.account_separator_name_hint);
        if (existing != null) {
            field.setText(existing.title);
            field.setSelection(field.getText().length());
        }
        int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        android.widget.FrameLayout box = new android.widget.FrameLayout(getContext());
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(field);

        DialogAnswers.show(new androidx.appcompat.app.AlertDialog.Builder(
                getContext(), R.style.CifraChoiceDialog)
                .setTitle(existing == null
                        ? R.string.account_separator_add : R.string.account_separator_rename)
                .setView(box)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    String name = field.getText().toString().trim();
                    if (name.isEmpty()) {
                        return;
                    }
                    if (existing != null) {
                        existing.title = name;
                        db.saveAccountSeparator(existing);
                        recreateCursor();
                    } else {
                        AccountSeparator made = new AccountSeparator(name);
                        db.saveAccountSeparator(made);
                        // Straight on to which accounts it gathers: a heading
                        // with nothing under it is a word on its own.
                        askWhichAccountsAreUnder(made);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .create());
    }

    /**
     * Which accounts the heading gathers.
     * <p>
     * Ticked off a list of them all, so a group can hold accounts that sit
     * nowhere near each other - the current account, a card and a wallet, with
     * others in between that belong elsewhere. An account ticked here leaves
     * whatever group it was in: it belongs to one at a time.
     */
    private void askWhichAccountsAreUnder(final AccountSeparator separator) {
        final List<Account> accounts = db.getAllAccountsList();
        if (accounts.isEmpty()) {
            return;
        }
        final java.util.Set<Long> already = db.getAccountsUnderSeparator(separator.id);
        final String[] titles = new String[accounts.size()];
        final boolean[] chosen = new boolean[accounts.size()];
        for (int i = 0; i < accounts.size(); i++) {
            titles[i] = accounts.get(i).title;
            chosen[i] = already.contains(accounts.get(i).id);
        }
        DialogAnswers.show(new androidx.appcompat.app.AlertDialog.Builder(
                getContext(), R.style.CifraChoiceDialog)
                .setTitle(separator.title)
                .setMultiChoiceItems(titles, chosen, (dialog, which, isChecked) -> chosen[which] = isChecked)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    java.util.Set<Long> wanted = new java.util.LinkedHashSet<>();
                    for (int i = 0; i < accounts.size(); i++) {
                        if (chosen[i]) {
                            wanted.add(accounts.get(i).id);
                        }
                    }
                    db.setAccountsUnderSeparator(separator.id, wanted);
                    recreateCursor();
                })
                .setNegativeButton(R.string.cancel, null)
                .create());
    }

    /**
     * Where the heading is drawn.
     * <p>
     * By itself it follows its own accounts, appearing above the first of them.
     * This is for saying otherwise: above a particular account, wherever that
     * account ends up. The first choice puts it back to following its own.
     */
    private void askWhereTheSeparatorGoes(final AccountSeparator separator) {
        final List<Account> accounts = db.getAllAccountsList();
        final String[] choices = new String[accounts.size() + 1];
        choices[0] = getString(R.string.account_separator_where_first);
        for (int i = 0; i < accounts.size(); i++) {
            choices[i + 1] = getString(R.string.account_separator_where_above,
                    accounts.get(i).title);
        }
        DialogAnswers.show(new androidx.appcompat.app.AlertDialog.Builder(
                getContext(), R.style.CifraChoiceDialog)
                .setTitle(R.string.account_separator_where)
                .setItems(choices, (dialog, which) -> {
                    separator.beforeAccountId = which == 0 ? 0 : accounts.get(which - 1).id;
                    db.saveAccountSeparator(separator);
                    recreateCursor();
                })
                .setNegativeButton(R.string.cancel, null)
                .create());
    }

    /** Held down: rename it, or take it away. */
    private void askWhatToDoWithSeparator(final AccountSeparator separator) {
        DialogAnswers.show(new androidx.appcompat.app.AlertDialog.Builder(
                getContext(), R.style.CifraChoiceDialog)
                .setTitle(separator.title)
                .setItems(new String[]{
                        getString(R.string.account_separator_accounts),
                        getString(R.string.account_separator_where),
                        getString(R.string.account_separator_rename),
                        getString(R.string.account_separator_delete)}, (dialog, which) -> {
                    if (which == 0) {
                        askWhichAccountsAreUnder(separator);
                    } else if (which == 1) {
                        askWhereTheSeparatorGoes(separator);
                    } else if (which == 2) {
                        askForSeparatorName(separator);
                    } else {
                        db.deleteAccountSeparator(separator.id);
                        recreateCursor();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .create());
    }

    private void setupMenuButton() {
        final ImageButton bMenu = getView().findViewById(R.id.bMenu);
        if (bMenu == null) {
            return;
        }
        // Always there. It was governed by a switch from the days when it opened
        // a menu; now it makes a backup, and a backup button that can be hidden
        // by a setting nobody remembers is a backup button one day missing.
        bMenu.setVisibility(View.VISIBLE);
        bMenu.setOnClickListener(v -> MenuListItem.backupNow(this));
    }

    private void handlePopupMenu(int id) {
        switch (id) {
            case R.id.backup:
                MenuListItem.backupNow(this);
                break;

        }
    }

    protected void prepareAccountActionGrid() {
        Account a = db.getAccount(selectedId);
        accountActionGrid = new QuickActionGrid(getContext());
        accountActionGrid.addQuickAction(new MyQuickAction(getContext(), R.drawable.ic_menu_info, R.string.info));
        accountActionGrid.addQuickAction(new MyQuickAction(getContext(), R.drawable.ic_action_list, R.string.blotter));
        accountActionGrid.addQuickAction(new MyQuickAction(getContext(), R.drawable.ic_menu_edit, R.string.edit));
        accountActionGrid.addQuickAction(new MyQuickAction(getContext(), R.drawable.ic_action_add, R.string.transaction));
        accountActionGrid.addQuickAction(new MyQuickAction(getContext(), R.drawable.ic_action_transfer, R.string.transfer));
        accountActionGrid.addQuickAction(new MyQuickAction(getContext(), R.drawable.ic_action_tick, R.string.balance));
        accountActionGrid.addQuickAction(new MyQuickAction(getContext(), R.drawable.ic_action_flash, R.string.delete_old_transactions));
        if (a.isActive) {
            accountActionGrid.addQuickAction(new MyQuickAction(getContext(), R.drawable.ic_action_lock_closed, R.string.close_account));
        } else {
            accountActionGrid.addQuickAction(new MyQuickAction(getContext(), R.drawable.ic_action_lock_open, R.string.reopen_account));
        }
        // Deleting goes last, where a hand does not land by accident.
        if (MyPreferences.isShowTransferCurrentBalance()) {
            accountActionGrid.addQuickAction(new MyQuickAction(getContext(), R.drawable.share_windows_32dp, R.string.transfer_current_balance));
        }
        accountActionGrid.addQuickAction(new MyQuickAction(getContext(), R.drawable.ic_menu_delete, R.string.delete_account));
        accountActionGrid.setOnQuickActionClickListener(accountActionListener);
    }

    private QuickActionWidget.OnQuickActionClickListener accountActionListener = (widget, position, action) -> {
        switch (position) {
            case 0:
                showAccountInfo(selectedId);
                break;
            case 1:
                showAccountTransactions(selectedId);
                break;
            case 2:
                editAccount(selectedId);
                break;
            case 3:
                addTransaction(selectedId, TransactionActivity.class);
                break;
            case 4:
                addTransaction(selectedId, TransferActivity.class);
                break;
            case 5:
                updateAccountBalance(selectedId);
                break;
            case 6:
                purgeAccount();
                break;
            case 7:
                closeOrOpenAccount();
                break;
            case 8:
                transferCurrentBalance(selectedId);
                break;
            case 9:
                deleteAccount();
                break;
        }
    };

    private void addTransaction(long accountId, Class<? extends AbstractTransactionActivity> clazz) {
        Intent intent = new Intent(getContext(), clazz);
        intent.putExtra(TransactionActivity.ACCOUNT_ID_EXTRA, accountId);
        startActivityForResult(intent, VIEW_ACCOUNT_REQUEST);
    }

    private void transferCurrentBalance(long accountId) {
        Account a = db.getAccount(accountId);
        if (a != null) {
            Intent intent = new Intent(getContext(), TransferActivity.class);
            intent.putExtra(TransactionActivity.ACCOUNT_ID_EXTRA, accountId);
            intent.putExtra(TransferActivity.AMOUNT_EXTRA, a.totalAmount);
            startActivityForResult(intent, VIEW_ACCOUNT_REQUEST);
        }
    }

    @Override
    public void recreateCursor() {
        Log.d(TAG, "recreateCursor");
        super.recreateCursor();
        calculateTotals();
    }

    private AccountTotalsCalculationTask totalCalculationTask;

    private void calculateTotals() {
        if (totalCalculationTask != null) {
            totalCalculationTask.stop();
            totalCalculationTask.cancel(true);
        }
        TextView totalText = getView().findViewById(R.id.total);
        totalText.setOnClickListener((view) -> {
            if (MyPreferences.isBlurBalances()) {
                if (totalText.getPaint().getMaskFilter() != null) {
                    totalText.getPaint().setMaskFilter(null);
                    totalText.invalidate();
                }
                else {
                    Utils.applyBlur(getView().findViewById(R.id.total));
                }
            }
        });
        totalText.setOnLongClickListener((view) -> {
            showTotals();
            return true;
        });
        totalCalculationTask = new AccountTotalsCalculationTask(getContext().getApplicationContext(), db, totalText, filter);
        totalCalculationTask.execute();
    }

    private void showTotals() {
        // Only where it has something to say. With one currency the screen listed
        // the figure already on the button and nothing else.
        if (!TotalsPopup.severalCurrencies(db)) {
            return;
        }
        Intent intent = new Intent(getContext(), AccountListTotalsDetailsActivity.class);
        intent.putExtra(AccountListTotalsDetailsActivity.FILTER, filter);
        startActivityForResult(intent, SHOW_TOTALS_REQUEST);
    }

    public static class AccountTotalsCalculationTask extends TotalCalculationTask {

        private final DatabaseAdapter db;
        private String filter;

        AccountTotalsCalculationTask(Context context, DatabaseAdapter db, TextView totalText, String filter) {
            super(context, db, totalText);
            this.db = db;
            this.filter = filter;
        }

        @Override
        public Total[] getTotals() {
            return db.getAccountsTotalWithFilter(filter);
        }

    }

    @Override
    protected AccountRecyclerAdapter createAdapter(Context context, Cursor cursor) {
        long t1 = System.nanoTime();
        var shared = io.github.mpstudios56.cifra.sync.SharedThings.sharedAccountIds(db.db());
        var a = new AccountRecyclerAdapter(context, cursor, showSortOrder, clickedView -> {
            // A tap opens the account. It used to open the ring of symbols
            // instead, which put the movements - the thing anybody opens an
            // account for - one tap further away than everything else, and gave
            // the short tap and the long one the same job.
            selectedId = (long) clickedView.getTag(R.id.account);
            showAccountTransactions(selectedId);
        }, longClickedView -> {
            selectedId = (long) longClickedView.getTag(R.id.account);
            prepareAccountActionGrid();
            accountActionGrid.show(longClickedView);
            return true;
        });
        java.util.Map<Long, io.github.mpstudios56.cifra.model.AccountSeparator> headings =
                db.getAccountSeparators();
        a.setSeparators(headings, db.getSeparatorByAccount());
        if (getActivity() instanceof MainActivity) {
            // The button belongs on this screen only while there is something
            // to fold: with no headings written there are no groups.
            ((MainActivity) getActivity()).setAccountsHaveGroups(!headings.isEmpty());
            TodayButton.showSeparatorFold(getActivity(), !headings.isEmpty());
            TodayButton.showGroupsFolded(getActivity(), !anySeparatorOpen());
        }
        a.setOnSeparatorAction(new io.github.mpstudios56.cifra.adapter.AccountRecyclerAdapter.OnSeparatorAction() {
            @Override
            public void onSeparatorClicked(AccountSeparator separator) {
                // A tap folds the group away, or brings it back.
                separator.folded = !separator.folded;
                db.setAccountSeparatorFolded(separator.id, separator.folded);
                recreateCursor();
            }

            @Override
            public void onSeparatorHeldDown(AccountSeparator separator) {
                askWhatToDoWithSeparator(separator);
            }
        });
        if (cursor.getCount() == 0) {
            emptyText.setVisibility(View.VISIBLE);
        }
        progressBar.setVisibility(View.GONE);

//        ItemTouchHelper.Callback callback = new SimpleItemTouchHelperCallback(a);
//        ItemTouchHelper mItemTouchHelper = new ItemTouchHelper(callback);
//        mItemTouchHelper.attachToRecyclerView(recyclerView);

        Log.d(TAG, "createAdapter: " + (System.nanoTime() - t1) / 1000 + " us");
        a.setSharedAccounts(shared);
        a.setSharedColours(io.github.mpstudios56.cifra.sync.SharedWith.coloursByAccount(db.db()));
        return a;
    }

    @Override
    protected Cursor createCursor() {
        Cursor c;

        new Handler(Looper.getMainLooper()).post(()-> {
            emptyText.setVisibility(View.GONE);
            progressBar.setVisibility(View.VISIBLE);
        });
        Log.d(TAG, "createCursor start");
        long t1 = System.nanoTime();
        if (MyPreferences.isHideClosedAccounts()) {
            c = db.getAllActiveAccountsWithFilter(filter);
        } else {
            c = db.getAllAccountsWithFilter(filter);
        }
        c.getCount();
        Log.d(TAG, "createCursor: " + (System.nanoTime() - t1) / 1000 + " us");
        return c;
    }

    protected List<MenuItemInfo> createContextMenus(long id) {
        return new ArrayList<>();
    }

    @Override
    public boolean onPopupItemSelected(int itemId, View view, int position, long id) {
        // do nothing
        return true;
    }

    private boolean updateAccountBalance(long id) {
        Account a = db.getAccount(id);
        if (a != null) {
            Intent intent = new Intent(getContext(), TransactionActivity.class);
            intent.putExtra(TransactionActivity.ACCOUNT_ID_EXTRA, a.id);
            intent.putExtra(TransactionActivity.CURRENT_BALANCE_EXTRA, a.totalAmount);
            startActivityForResult(intent, 0);
            return true;
        }
        return false;
    }

    @Override
    protected void addItem() {
        Intent intent = new Intent(getContext(), AccountActivity.class);
        startActivityForResult(intent, NEW_ACCOUNT_REQUEST);
    }

    @Override
    protected void deleteItem(View v, int position, final long id) {
        new AlertDialog.Builder(getContext())
                .setMessage(R.string.delete_account_confirm)
                .setPositiveButton(R.string.yes, (arg0, arg1) -> {
                    db.deleteAccount(id);
                    recreateCursor();
                })
                .setNegativeButton(R.string.no, null)
                .show();
    }

    @Override
    public void editItem(View v, int position, long id) {
        editAccount(id);
    }

    private void editAccount(long id) {
        Intent intent = new Intent(getContext(), AccountActivity.class);
        intent.putExtra(AccountActivity.ACCOUNT_ID_EXTRA, id);
        startActivityForResult(intent, EDIT_ACCOUNT_REQUEST);
    }

    private void showAccountInfo(long id) {
        NodeInflater nodeInflater = new NodeInflater(inflater);
        AccountInfoDialog accountInfoDialog = new AccountInfoDialog(getContext(), id, db, nodeInflater);
        accountInfoDialog.show();
    }


    @Override
    protected void viewItem(View v, int position, long id) {
        showAccountTransactions(id);
    }

    private void showAccountTransactions(long id) {
        Account account = db.getAccount(id);
        if (account != null) {
            Intent intent = new Intent(getContext(), BlotterActivity.class);
            Criterion.eq(BlotterFilter.FROM_ACCOUNT_ID, String.valueOf(id))
                    .toIntent(account.title, intent);
            intent.putExtra(BlotterFilterActivity.IS_ACCOUNT_FILTER, true);
            startActivityForResult(intent, VIEW_ACCOUNT_REQUEST);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VIEW_ACCOUNT_REQUEST || requestCode == PURGE_ACCOUNT_REQUEST) {
            recreateCursor();
        }
    }

    private void purgeAccount() {
        Intent intent = new Intent(getContext(), PurgeAccountActivity.class);
        intent.putExtra(PurgeAccountActivity.ACCOUNT_ID, selectedId);
        startActivityForResult(intent, PURGE_ACCOUNT_REQUEST);
    }

    private void closeOrOpenAccount() {
        Account a = db.getAccount(selectedId);
        if (a.isActive) {
            new AlertDialog.Builder(getContext())
                    .setMessage(R.string.close_account_confirm)
                    .setPositiveButton(R.string.yes, (arg0, arg1) -> flipAccountActive(a))
                    .setNegativeButton(R.string.no, null)
                    .show();
        } else {
            flipAccountActive(a);
        }
    }

    private void flipAccountActive(Account a) {
        a.isActive = !a.isActive;
        db.saveAccount(a);
        recreateCursor();
    }

    private void deleteAccount() {
        new AlertDialog.Builder(getContext())
                .setMessage(R.string.delete_account_confirm)
                .setPositiveButton(R.string.yes, (arg0, arg1) -> {
                    db.deleteAccount(selectedId);
                    recreateCursor();
                })
                .setNegativeButton(R.string.no, null)
                .show();
    }

    @Override
    public void integrityCheck() {
        new IntegrityCheckTask(this).execute(new IntegrityCheckAutobackup(getContext(), TimeUnit.DAYS.toMillis(7)));
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");

        if (PinProtection.isUnlocked()) {
            Log.d(TAG, "onResume isUnlocked, show list");
            getView().findViewById(android.R.id.list).setVisibility(View.VISIBLE);
        }
        else {
            // still locked, don't show account list balances
            Log.d(TAG, "onResume NOT isUnlocked, hide list");
            getView().findViewById(android.R.id.list).setVisibility(View.INVISIBLE);
        }

        // Read again on every visit. The list is a handful of rows and its
        // figures are the ones people trust most; leaving them as they were
        // loaded meant a balance could be a round of sharing out of date, and
        // there is no telling a stale figure from a wrong one.
        recreateCursor();
    }

    @Override
    public void onDestroy() {
        if (totalCalculationTask != null) {
            totalCalculationTask.stop();
            totalCalculationTask.cancel(true);
        }
        super.onDestroy();
    }

    private void loadFilter() {
        SharedPreferences preferences = getContext().getSharedPreferences(TAG, 0);
        filter = preferences.getString(FILTER_PERF, "");
    }

    private void saveFilter() {
        SharedPreferences preferences = getContext().getSharedPreferences(TAG, 0);
        SharedPreferences.Editor e = preferences.edit();
        e.putString(FILTER_PERF, filter);
        e.apply();
    }

}
