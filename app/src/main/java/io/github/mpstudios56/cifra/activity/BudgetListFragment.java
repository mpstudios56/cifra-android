package io.github.mpstudios56.cifra.activity;

import static android.app.Activity.RESULT_FIRST_USER;
import static android.app.Activity.RESULT_OK;
import static android.content.Context.MODE_PRIVATE;

import static java.lang.String.format;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ListAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.FragmentActivity;

import java.util.ArrayList;

import io.github.mpstudios56.cifra.R;
import io.github.mpstudios56.cifra.adapter.BudgetListAdapter;
import io.github.mpstudios56.cifra.blotter.BlotterFilter;
import io.github.mpstudios56.cifra.datetime.PeriodType;
import io.github.mpstudios56.cifra.db.MyEntityManager;
import io.github.mpstudios56.cifra.filter.Criterion;
import io.github.mpstudios56.cifra.filter.DateTimeCriterion;
import io.github.mpstudios56.cifra.filter.WhereFilter;
import io.github.mpstudios56.cifra.db.BudgetsTotalCalculator;
import io.github.mpstudios56.cifra.model.Budget;
import io.github.mpstudios56.cifra.model.Total;
import io.github.mpstudios56.cifra.utils.RecurUtils;
import io.github.mpstudios56.cifra.utils.Utils;

public class BudgetListFragment extends AbstractListFragment<ArrayList<Budget>> {
    private static final String TAG = "BudgetListFragment";

    private static final int NEW_BUDGET_REQUEST = 1;
    private static final int EDIT_BUDGET_REQUEST = 2;
    private static final int VIEW_BUDGET_REQUEST = 3;
    private static final int FILTER_BUDGET_REQUEST = 4;

    private static final String PREF_SORT_ORDER = "sort_order";

    private ImageButton bFilter;
    private ImageButton bSortOrder;

    private WhereFilter filter = WhereFilter.empty();

    public BudgetListFragment() {
        super(R.layout.budget_list);
    }

    private Handler handler;

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ViewCompat.setOnApplyWindowInsetsListener(view.findViewById(R.id.bottom_bar), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.statusBars()
                    | WindowInsetsCompat.Type.captionBar());
            Log.d(TAG, format("insets.bottom: %s", insets.bottom));
            v.setPadding(0, insets.top, 0, 0);
            return WindowInsetsCompat.CONSUMED;
        });

        TextView totalText = view.findViewById(R.id.total);
        totalText.setOnClickListener(v -> showTotals());

        bFilter = view.findViewById(R.id.bFilter);
        bFilter.setOnClickListener(v -> showPeriodPicker());

        bSortOrder = view.findViewById(R.id.bSortOrder);
        bSortOrder.setOnClickListener(v -> {
            new AlertDialog.Builder(getContext())
                    .setSingleChoiceItems(
                            new ArrayAdapter<>(getContext(),
                                    R.layout.dialog_choice_row,
                                    android.R.id.text1,
                                    getResources().getStringArray(R.array.budget_sort_order)),
                            getActivity().getSharedPreferences(TAG, MODE_PRIVATE).getInt(PREF_SORT_ORDER, 0),
                            (dialog, which) -> {
                                dialog.cancel();
                                getActivity().getSharedPreferences(TAG, MODE_PRIVATE).edit().putInt(PREF_SORT_ORDER, which).apply();
                                recreateCursor();
                            })
                    .setTitle(getString(R.string.sort_order))
                    .show();
        });

        if (filter.isEmpty()) {
            filter = WhereFilter.fromSharedPreferences(getContext().getSharedPreferences(this.getClass().getName(), 0));
        }
        if (filter.isEmpty()) {
            filter.put(new DateTimeCriterion(PeriodType.THIS_MONTH));
        }

        handler = new Handler();

        applyFilter();
    }

    /**
     * Which stretch of time the budgets are shown for.
     * <p>
     * The periods are laid out in the open rather than folded into a dropdown,
     * and the foot of it carries the same three the movements filter does: set
     * with the green button, cleared with the funnel beside it - all budgets,
     * whatever their date - or left alone with the way out.
     */
    private void showPeriodPicker() {
        final PeriodType[] periods = PeriodType.allRegular();

        android.view.ContextThemeWrapper themed = new android.view.ContextThemeWrapper(
                getContext(), R.style.CifraChoiceDialog);
        final View view = android.view.LayoutInflater.from(themed)
                .inflate(R.layout.budget_period, null);
        final android.widget.RadioGroup group = view.findViewById(R.id.period_group);
        final View customPeriod = view.findViewById(R.id.custom_period);
        final android.widget.Button bFrom = view.findViewById(R.id.bPeriodFrom);
        final android.widget.Button bTo = view.findViewById(R.id.bPeriodTo);

        // The two dates a custom period is made of, filled in as they are picked.
        final java.util.Calendar cFrom = java.util.Calendar.getInstance();
        final java.util.Calendar cTo = java.util.Calendar.getInstance();
        final java.text.DateFormat df =
                io.github.mpstudios56.cifra.datetime.DateUtils.getShortDateFormat(getContext());

        // What is set now, so the list opens on it.
        DateTimeCriterion current = (DateTimeCriterion) filter.get(BlotterFilter.DATETIME);
        PeriodType chosen = PeriodType.THIS_MONTH;
        if (current != null && current.getPeriod() != null) {
            chosen = current.getPeriod().type;
        } else if (current != null) {
            chosen = PeriodType.CUSTOM;
        }
        if (chosen == PeriodType.CUSTOM && current != null) {
            cFrom.setTimeInMillis(current.getLongValue1());
            cTo.setTimeInMillis(current.getLongValue2());
        }

        // Fifteen periods read as a wall. Gathered into the four families they
        // actually belong to - what is running, what has finished, the two
        // taken together, and one chosen by hand - the eye lands on the right
        // handful straight away.
        final int density = Math.round(getResources().getDisplayMetrics().density);
        int lastFamily = -1;
        for (int i = 0; i < periods.length; i++) {
            int family = familyOf(periods[i]);
            boolean newFamily = family != lastFamily;
            if (newFamily && familyTitle(family) != 0) {
                TextView heading = new TextView(themed);
                heading.setText(getString(familyTitle(family)));
                heading.setTextColor(0xFF4CAF7D);
                heading.setTextSize(13);
                heading.setPadding(0, (i == 0 ? 4 : 14) * density, 0, 2 * density);
                group.addView(heading);
            }
            lastFamily = family;
            android.widget.RadioButton row = new android.widget.RadioButton(themed);
            row.setId(i);
            row.setText(getString(periods[i].getTitleId()));
            row.setTextColor(0xFFF4EFE4);
            // A family with no heading of its own still needs to be told apart
            // from the one above it: the one chosen by hand was reading as a
            // fifth entry of the group before it.
            int above = (newFamily && familyTitle(family) == 0 && i > 0 ? 14 : 5) * density;
            row.setPadding(row.getPaddingLeft(), above, row.getPaddingRight(), 5 * density);
            group.addView(row);
            if (periods[i] == chosen) {
                group.check(i);
            }
        }

        bFrom.setText(df.format(cFrom.getTime()));
        bTo.setText(df.format(cTo.getTime()));
        customPeriod.setVisibility(chosen == PeriodType.CUSTOM ? View.VISIBLE : View.GONE);
        group.setOnCheckedChangeListener((g, id) -> customPeriod.setVisibility(
                id >= 0 && periods[id] == PeriodType.CUSTOM ? View.VISIBLE : View.GONE));

        bFrom.setOnClickListener(v -> new android.app.DatePickerDialog(getContext(),
                (picker, y, m, d) -> {
                    cFrom.set(y, m, d);
                    io.github.mpstudios56.cifra.datetime.DateUtils.startOfDay(cFrom);
                    bFrom.setText(df.format(cFrom.getTime()));
                }, cFrom.get(java.util.Calendar.YEAR), cFrom.get(java.util.Calendar.MONTH),
                cFrom.get(java.util.Calendar.DAY_OF_MONTH)).show());
        bTo.setOnClickListener(v -> new android.app.DatePickerDialog(getContext(),
                (picker, y, m, d) -> {
                    cTo.set(y, m, d);
                    io.github.mpstudios56.cifra.datetime.DateUtils.endOfDay(cTo);
                    bTo.setText(df.format(cTo.getTime()));
                }, cTo.get(java.util.Calendar.YEAR), cTo.get(java.util.Calendar.MONTH),
                cTo.get(java.util.Calendar.DAY_OF_MONTH)).show());

        final androidx.appcompat.app.AlertDialog dialog =
                new androidx.appcompat.app.AlertDialog.Builder(getContext(), R.style.CifraChoiceDialog)
                        .setTitle(R.string.period)
                        .setView(view)
                        .create();

        view.findViewById(R.id.bOK).setOnClickListener(v -> {
            int id = group.getCheckedRadioButtonId();
            PeriodType period = id >= 0 && id < periods.length ? periods[id] : PeriodType.THIS_MONTH;
            if (period == PeriodType.CUSTOM) {
                filter.put(new DateTimeCriterion(cFrom.getTimeInMillis(), cTo.getTimeInMillis()));
            } else {
                filter.put(new DateTimeCriterion(period));
            }
            saveFilter();
            recreateCursor();
            dialog.dismiss();
        });
        // The funnel with the cross: no period at all, every budget shown.
        view.findViewById(R.id.bNoFilter).setOnClickListener(v -> {
            filter.clear();
            saveFilter();
            recreateCursor();
            dialog.dismiss();
        });
        view.findViewById(R.id.bCancel).setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    /** Which of the four families a period belongs to. */
    private static int familyOf(PeriodType period) {
        switch (period) {
            case LAST_WEEK:
            case LAST_MONTH:
            case LAST_YEAR:
            case LAST_FISCAL_YEAR:
                return 1;
            case THIS_AND_LAST_WEEK:
            case THIS_AND_LAST_MONTH:
            case THIS_AND_LAST_YEAR:
            case THIS_AND_LAST_FISCAL_YEAR:
                return 2;
            case CUSTOM:
                return 3;
            default:
                return 0;
        }
    }

    /**
     * The words over a family, or nothing where none are wanted.
     * <p>
     * The first handful and the last need no announcing: what is running is
     * what the list opens on, and a period chosen by hand is the one entry that
     * says what it is. Two headings in the middle are enough to break the wall
     * without turning a short list into an index.
     */
    private static int familyTitle(int family) {
        switch (family) {
            case 1:
                return R.string.period_family_past;
            case 2:
                return R.string.period_family_together;
            default:
                return 0;
        }
    }

    private void showTotals() {
        Intent intent = new Intent(getContext(), BudgetListTotalsDetailsActivity.class);
        filter.toIntent(intent);
        startActivityForResult(intent, -1);
    }

    private void saveFilter() {
        SharedPreferences preferences = getContext().getSharedPreferences(this.getClass().getName(), 0);
        filter.toSharedPreferences(preferences);
        applyFilter();
        recreateCursor();
    }

    /**
     * The calendar keeps its face and only changes colour.
     * <p>
     * It used to be handed to the same code that draws the funnel on the
     * movements, which sets the picture as well as the colour - so the calendar
     * was replaced by a funnel every time the list was refreshed, however it
     * had been drawn in the layout.
     */
    private void applyFilter() {
        if (bFilter == null) {
            return;
        }
        bFilter.setImageResource(R.drawable.actionbar_calendar);
        bFilter.setColorFilter(filter.isEmpty() ? null
                : new android.graphics.PorterDuffColorFilter(0xFF4CAF7D,
                        android.graphics.PorterDuff.Mode.SRC_IN));
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILTER_BUDGET_REQUEST) {
            if (resultCode == RESULT_FIRST_USER) {
                filter.clear();
            } else if (resultCode == RESULT_OK) {
                String periodType = data.getStringExtra(DateFilterActivity.EXTRA_FILTER_PERIOD_TYPE);
                PeriodType p = PeriodType.valueOf(periodType);
                if (PeriodType.CUSTOM == p) {
                    long periodFrom = data.getLongExtra(DateFilterActivity.EXTRA_FILTER_PERIOD_FROM, 0);
                    long periodTo = data.getLongExtra(DateFilterActivity.EXTRA_FILTER_PERIOD_TO, 0);
                    filter.put(new DateTimeCriterion(periodFrom, periodTo));
                } else {
                    filter.put(new DateTimeCriterion(p));
                }
            }
            saveFilter();
        }
        recreateCursor();
    }

    @Override
    protected ListAdapter createAdapter(Context context, ArrayList<Budget> budgets) {
        calculateTotals(budgets);
        return new BudgetListAdapter(context, budgets);
    }

    @Override
    protected ArrayList<Budget> loadInBackground() {
        int sortOrder = 0;
        FragmentActivity activity = getActivity();
        if (activity != null) {
            sortOrder = activity.getSharedPreferences(TAG, MODE_PRIVATE).getInt(PREF_SORT_ORDER, 0);
        }
        filter.recalculatePeriod();
        return db.getAllBudgets(filter, MyEntityManager.BudgetSortOrder.values()[sortOrder]);
    }

    private BudgetListFragment.BudgetTotalsCalculationTask totalCalculationTask;

    private void calculateTotals(ArrayList<Budget> budgets) {
        if (totalCalculationTask != null) {
            totalCalculationTask.stop();
            totalCalculationTask.cancel(true);
        }
        View view = getView();
        if (view != null) {
            TextView totalText = view.findViewById(R.id.total);
            totalCalculationTask = new BudgetListFragment.BudgetTotalsCalculationTask(totalText, budgets);
            totalCalculationTask.execute((Void[]) null);
        }
    }

    @Override
    protected void addItem() {
        Intent intent = new Intent(getContext(), BudgetActivity.class);
        startActivityForResult(intent, NEW_BUDGET_REQUEST);
    }

    @Override
    protected void deleteItem(View v, int position, final long id) {
        final Budget b = db.load(Budget.class, id);
        if (b.parentBudgetId > 0) {
            new AlertDialog.Builder(getContext())
                    .setMessage(R.string.delete_budget_recurring_select)
                    .setPositiveButton(R.string.delete_budget_one_entry, (arg0, arg1) -> {
                        db.deleteBudgetOneEntry(id);
                        recreateCursor();
                    })
                    .setNeutralButton(R.string.delete_budget_all_entries, (arg0, arg1) -> {
                        db.deleteBudget(b.parentBudgetId);
                        recreateCursor();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        } else {
            RecurUtils.Recur recur = RecurUtils.createFromExtraString(b.recur);
            new AlertDialog.Builder(getContext())
                    .setMessage(recur.interval == RecurUtils.RecurInterval.NO_RECUR ? R.string.delete_budget_confirm : R.string.delete_budget_recurring_confirm)
                    .setPositiveButton(R.string.yes, (arg0, arg1) -> {
                        db.deleteBudget(id);
                        recreateCursor();
                    })
                    .setNegativeButton(R.string.no, null)
                    .show();
        }
    }

    @Override
    public void editItem(View v, int position, long id) {
        Budget b = db.load(Budget.class, id);
        RecurUtils.Recur recur = b.getRecur();
        if (recur.interval != RecurUtils.RecurInterval.NO_RECUR) {
            Toast t = Toast.makeText(getContext(), R.string.edit_recurring_budget, Toast.LENGTH_LONG);
            t.show();
        }
        Intent intent = new Intent(getContext(), BudgetActivity.class);
        intent.putExtra(BudgetActivity.BUDGET_ID_EXTRA, b.parentBudgetId > 0 ? b.parentBudgetId : id);
        startActivityForResult(intent, EDIT_BUDGET_REQUEST);
    }

    @Override
    protected void viewItem(View v, int position, long id) {
        Budget b = db.load(Budget.class, id);
        Intent intent = new Intent(getContext(), BudgetBlotterActivity.class);
        Criterion.eq(BlotterFilter.BUDGET_ID, String.valueOf(id))
                .toIntent(b.title, intent);
        startActivityForResult(intent, VIEW_BUDGET_REQUEST);
    }

    public class BudgetTotalsCalculationTask extends AsyncTask<Void, Total, Total> {

        private volatile boolean isRunning = true;

        private final TextView totalText;
        private ArrayList<Budget> budgets;

        public BudgetTotalsCalculationTask(TextView totalText, ArrayList<Budget> budgets) {
            this.budgets = budgets;
            this.totalText = totalText;
        }

        @Override
        protected Total doInBackground(Void... params) {
            try {
                BudgetsTotalCalculator c = new BudgetsTotalCalculator(db, budgets);
                c.updateBudgets(handler);
                return c.calculateTotalInHomeCurrency();
            } catch (Exception ex) {
                Log.e("BudgetTotals", "Unexpected error", ex);
                return Total.ZERO;
            }

        }

        @Override
        protected void onPostExecute(Total result) {
            if (isRunning && adapter != null) {
                Utils u = new Utils(getActivity());
                u.setTotal(totalText, result);
                ((BudgetListAdapter) adapter).notifyDataSetChanged();
            }
        }

        public void stop() {
            isRunning = false;
        }

    }
}
