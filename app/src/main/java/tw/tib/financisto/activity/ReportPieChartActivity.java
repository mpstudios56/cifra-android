/*
 * This program is made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */
package tw.tib.financisto.activity;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import tw.tib.financisto.R;
import tw.tib.financisto.utils.PinProtection;

/**
 * The report as a pie.
 * <p>
 * A pie is only worth drawing while the slices can be told apart. Forty
 * categories in one ring is forty slivers, forty labels written over each
 * other, and a legend tall enough to cover the chart it explains - which is
 * how this screen used to look.
 * <p>
 * So: income and spending are separate pies rather than one ring holding both
 * (they do not add up to anything together), everything below a few per cent
 * is gathered into a single slice, and the naming is done by a list underneath
 * with the figures in it rather than by labels squeezed onto the slivers.
 */
public class ReportPieChartActivity extends AppCompatActivity {

    public static final String PIE_CHART_DATA = "pie_chart_data";
    public static final String PIE_CHART_AMOUNTS = "pie_chart_amounts";

    /** Below this share of the total a slice is a sliver, and joins "other". */
    private static final float SMALLEST = 3f;
    /** However big they are, this many named slices is all a ring can carry. */
    private static final int MOST_SLICES = 9;
    /** A label written on the slice itself needs the slice to have room for it. */
    private static final float LABEL_NEEDS = 6f;

    /**
     * Chosen to be told apart from each other rather than to be pretty: no two
     * neighbours share a hue, and none of them is the red the app uses to mean
     * "less than nothing".
     */
    private static final int[] SLICES = {
            0xFF5B8DEF, 0xFF3FA96F, 0xFFE9A742, 0xFFC9709A, 0xFF6ECBC0,
            0xFF9B7EDE, 0xFFD98E5A, 0xFF7FB069, 0xFF5FA8D3, 0xFFB08BBB,
    };
    private static final int OTHER = 0xFF8A8A8A;

    private PieChart chart;
    private LinearLayout list;
    private TextView empty;
    private Button outButton;
    private Button inButton;

    private final List<Slice> spending = new ArrayList<>();
    private final List<Slice> income = new ArrayList<>();
    private boolean showingIncome = false;

    /** One line of the report: what it is called, how much, and how much of it. */
    private static class Slice {
        final String name;
        final float value;
        final String amount;

        Slice(String name, float value, String amount) {
            this.name = name;
            this.value = value;
            this.amount = amount;
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.report_piechart);
        setSupportActionBar(findViewById(R.id.toolbar));
        setTitle(R.string.pie_chart);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.pie_base), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        chart = findViewById(R.id.chart);
        list = findViewById(R.id.pie_list);
        empty = findViewById(R.id.pie_empty);
        outButton = findViewById(R.id.pie_out);
        inButton = findViewById(R.id.pie_in);

        setUpChart();
        read();

        outButton.setOnClickListener(v -> show(false));
        inButton.setOnClickListener(v -> show(true));
        // Spending first: it is the question people open a report to ask.
        show(spending.isEmpty() && !income.isEmpty());
    }

    private void setUpChart() {
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setDrawEntryLabels(false);
        chart.setUsePercentValues(true);
        chart.setRotationEnabled(true);
        chart.setHighlightPerTapEnabled(false);
        chart.setHoleColor(Color.TRANSPARENT);
        chart.setHoleRadius(52f);
        chart.setTransparentCircleRadius(56f);
        chart.setTransparentCircleColor(Color.WHITE);
        chart.setTransparentCircleAlpha(20);
        chart.setCenterTextColor(0xFFF4EFE4);
        chart.setCenterTextSize(13f);
        chart.setExtraOffsets(8f, 8f, 8f, 8f);
    }

    // ------------------------------------------------------------------ data

    private void read() {
        Bundle args = getIntent().getExtras();
        if (args == null) {
            return;
        }
        String json = args.getString(PIE_CHART_DATA);
        String[] amounts = args.getStringArray(PIE_CHART_AMOUNTS);
        if (json == null) {
            return;
        }
        ArrayList<PieEntry> entries =
                new Gson().fromJson(json, new TypeToken<ArrayList<PieEntry>>() {}.getType());
        if (entries == null) {
            return;
        }
        for (int i = 0; i < entries.size(); i++) {
            PieEntry e = entries.get(i);
            String label = e.getLabel() == null ? "" : e.getLabel();
            // The caller marks which side of the ledger a line is on by writing
            // a sign in front of its name.
            boolean isIncome = label.startsWith("+");
            String name = label.isEmpty() ? label : label.substring(1);
            String amount = amounts != null && i < amounts.length ? amounts[i] : "";
            (isIncome ? income : spending).add(new Slice(name, Math.abs(e.getValue()), amount));
        }
        Collections.sort(spending, (a, b) -> Float.compare(b.value, a.value));
        Collections.sort(income, (a, b) -> Float.compare(b.value, a.value));

        // With nothing on one side there is nothing to switch between.
        boolean both = !spending.isEmpty() && !income.isEmpty();
        findViewById(R.id.pie_switch).setVisibility(both ? View.VISIBLE : View.GONE);
    }

    private void show(boolean wantIncome) {
        showingIncome = wantIncome;
        outButton.setSelected(!wantIncome);
        inButton.setSelected(wantIncome);

        List<Slice> all = wantIncome ? income : spending;
        float total = 0;
        for (Slice s : all) {
            total += s.value;
        }

        // Everything too small to see, and everything past the ninth, in one
        // slice. A ring of slivers says less than a ring of nine and an "other".
        List<Slice> shown = new ArrayList<>();
        float rest = 0;
        for (Slice s : all) {
            float share = total > 0 ? 100f * s.value / total : 0;
            if (shown.size() < MOST_SLICES && share >= SMALLEST) {
                shown.add(s);
            } else {
                rest += s.value;
            }
        }
        boolean hasOther = rest > 0;

        List<PieEntry> entries = new ArrayList<>();
        List<Integer> colours = new ArrayList<>();
        for (int i = 0; i < shown.size(); i++) {
            entries.add(new PieEntry(shown.get(i).value, shown.get(i).name));
            colours.add(SLICES[i % SLICES.length]);
        }
        if (hasOther) {
            entries.add(new PieEntry(rest, getString(R.string.pie_other)));
            colours.add(OTHER);
        }

        PieDataSet set = new PieDataSet(entries, "");
        set.setColors(colours);
        set.setSliceSpace(2f);
        set.setValueTextColor(Color.WHITE);
        set.setValueTextSize(12f);
        PieData data = new PieData(set);
        data.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                // Written on the slice only where the slice can hold it; the
                // rest are named in the list underneath.
                return value >= LABEL_NEEDS ? Math.round(value) + "%" : "";
            }
        });
        chart.setData(data);
        chart.setCenterText(getString(showingIncome
                ? R.string.pie_centre_in : R.string.pie_centre_out));
        chart.highlightValues(null);
        chart.invalidate();

        fillList(all, total);

        boolean nothing = all.isEmpty();
        chart.setVisibility(nothing ? View.GONE : View.VISIBLE);
        empty.setVisibility(nothing ? View.VISIBLE : View.GONE);
    }

    /**
     * Every line, including the ones gathered into "other" - the ring says
     * which are the big ones, and this says what they all were.
     */
    private void fillList(List<Slice> all, float total) {
        list.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        for (int i = 0; i < all.size(); i++) {
            Slice s = all.get(i);
            float share = total > 0 ? 100f * s.value / total : 0;
            boolean named = i < MOST_SLICES && share >= SMALLEST;

            View row = getLayoutInflater().inflate(R.layout.report_pie_row, list, false);
            View dot = row.findViewById(R.id.pie_row_dot);
            dot.getBackground().setTint(named ? SLICES[i % SLICES.length] : OTHER);
            ((TextView) row.findViewById(R.id.pie_row_name)).setText(s.name);
            ((TextView) row.findViewById(R.id.pie_row_share))
                    .setText(Math.round(share) + "%");
            ((TextView) row.findViewById(R.id.pie_row_amount)).setText(s.amount);
            list.addView(row);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        PinProtection.lock(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        PinProtection.unlock(this);
    }
}
