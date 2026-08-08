/*
 * This program is made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */
package tw.tib.financisto.activity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import tw.tib.financisto.R;
import tw.tib.financisto.export.csv.CsvColumnMapping;
import tw.tib.financisto.export.csv.CsvField;
import tw.tib.financisto.export.csv.CsvHeaderSniffer;
import tw.tib.financisto.export.csv.CsvRow;
import tw.tib.financisto.export.csv.CsvRowReader;
import tw.tib.financisto.utils.PinProtection;

/**
 * Says what Cifra thinks somebody else's file contains, and lets it be corrected.
 * <p>
 * Every judgement on this screen is a proposal with the reasoning visible under
 * it: the preview shows the first lines read the way the assignment above says
 * to read them, so a wrong guess about the decimal separator or the direction of
 * the money is seen here rather than discovered later in the balances.
 * <p>
 * It writes nothing. The import itself is the step after this one.
 */
public class CsvMappingActivity extends AppCompatActivity {

    private static final String TAG = "CsvMappingActivity";
    /** Enough lines to see a mistake, few enough to read at a glance. */
    private static final int PREVIEW_ROWS = 6;

    /**
     * The fields offered, in the order they are worth thinking about: when the
     * money moved, how much, out of which account, and only then the labels.
     */
    private static final CsvField[] FIELDS = {
            CsvField.DATE, CsvField.TIME,
            CsvField.AMOUNT, CsvField.INCOME, CsvField.EXPENSE, CsvField.TYPE,
            CsvField.ACCOUNT, CsvField.TRANSFER_ACCOUNT,
            CsvField.PARENT_CATEGORY, CsvField.CATEGORY,
            CsvField.PAYEE, CsvField.NOTE, CsvField.CURRENCY,
    };

    private Uri fileUri;
    private CsvHeaderSniffer.Guess guess;

    private Button continueButton;
    private Button fileButton;
    private View body;
    private LinearLayout fieldRows;
    private LinearLayout shapeRows;
    private TextView preview;

    private final Map<CsvField, Spinner> fieldSpinners = new LinkedHashMap<>();
    private Spinner signSpinner;
    private ActivityResultLauncher<String[]> pickFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.csv_mapping);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.csv_map_base), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, insets.top, 0, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        body = findViewById(R.id.csv_map_body);
        fieldRows = findViewById(R.id.csv_map_fields);
        shapeRows = findViewById(R.id.csv_map_shape);
        preview = findViewById(R.id.csv_map_preview);
        fileButton = findViewById(R.id.csv_map_file);
        continueButton = findViewById(R.id.csv_map_continue);

        pickFile = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) {
                open(uri);
            }
        });
        // Not "text/csv": several of the apps hand out their export with no type at
        // all, or with a type of their own, and a filter that hides the file the user
        // came here with is worse than no filter.
        fileButton.setOnClickListener(v -> pickFile.launch(new String[]{"*/*"}));
        continueButton.setOnClickListener(v -> describeWhatWouldHappen());

        Intent intent = getIntent();
        if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            open(intent.getData());
        }
    }

    // ---------------------------------------------------------------- the file

    private void open(Uri uri) {
        fileUri = uri;
        fileButton.setText(name(uri));
        fileButton.setEnabled(false);
        preview.setText(R.string.csv_map_reading);
        body.setVisibility(View.VISIBLE);

        // Reading is short - twenty lines - but it is still somebody else's storage
        // provider on the other end of it, and that can take its time.
        new Thread(() -> {
            CsvHeaderSniffer.Guess read = null;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in != null) {
                    read = CsvHeaderSniffer.sniff(new InputStreamReader(in, StandardCharsets.UTF_8));
                }
            } catch (Exception e) {
                Log.e(TAG, "could not read " + uri, e);
            }
            CsvHeaderSniffer.Guess result = read;
            new Handler(Looper.getMainLooper()).post(() -> {
                fileButton.setEnabled(true);
                if (result == null || result.headings.isEmpty()) {
                    body.setVisibility(View.GONE);
                    continueButton.setEnabled(false);
                    Toast.makeText(this, R.string.csv_map_unreadable, Toast.LENGTH_LONG).show();
                    return;
                }
                guess = result;
                buildShape();
                buildFields();
                refresh();
            });
        }).start();
    }

    private String name(Uri uri) {
        String path = uri.getPath();
        if (path == null) {
            return uri.toString();
        }
        return path.substring(path.lastIndexOf('/') + 1);
    }

    // ------------------------------------------------------------- what it is

    /** The four things about the file that are read off it and can be wrong. */
    private void buildShape() {
        shapeRows.removeAllViews();

        List<String> delimiters = new ArrayList<>();
        for (char c : CsvHeaderSniffer.DELIMITERS) {
            delimiters.add(c == '\t' ? getString(R.string.csv_map_tab) : " " + c + " ");
        }
        Spinner delimiter = row(shapeRows, getString(R.string.field_separator), delimiters,
                indexOf(CsvHeaderSniffer.DELIMITERS, guess.mapping.delimiter));
        delimiter.setOnItemSelectedListener(onChange(position -> {
            char chosen = CsvHeaderSniffer.DELIMITERS[position];
            if (chosen != guess.mapping.delimiter) {
                // Changing the separator changes what the columns even are, so the whole
                // file has to be looked at again rather than patched up.
                reread(chosen);
            }
        }));

        char[] decimals = {'.', ','};
        Spinner decimal = row(shapeRows, getString(R.string.decimal_separator),
                Arrays.asList(" . ", " , "),
                indexOf(decimals, guess.mapping.decimalSeparator));
        decimal.setOnItemSelectedListener(onChange(position -> {
            guess.mapping.decimalSeparator = decimals[position];
            refresh();
        }));

        List<String> formats = new ArrayList<>(Arrays.asList(CsvHeaderSniffer.DATE_FORMATS));
        if (!formats.contains(guess.mapping.dateFormat)) {
            formats.add(0, guess.mapping.dateFormat);
        }
        Spinner date = row(shapeRows, getString(R.string.date_format), formats,
                formats.indexOf(guess.mapping.dateFormat));
        date.setOnItemSelectedListener(onChange(position -> {
            guess.mapping.dateFormat = formats.get(position);
            refresh();
        }));

        CsvColumnMapping.Sign[] signs = CsvColumnMapping.Sign.values();
        signSpinner = row(shapeRows, getString(R.string.csv_map_sign), Arrays.asList(
                        getString(R.string.csv_map_sign_in_amount),
                        getString(R.string.csv_map_sign_in_type),
                        getString(R.string.csv_map_sign_two_columns)),
                Arrays.asList(signs).indexOf(guess.mapping.sign));
        signSpinner.setOnItemSelectedListener(onChange(position -> {
            guess.mapping.sign = signs[position];
            showTheFieldsThatApply();
            refresh();
        }));
    }

    /** Reads the file again after the separator was corrected by hand. */
    private void reread(char delimiter) {
        Uri uri = fileUri;
        new Thread(() -> {
            CsvHeaderSniffer.Guess read = null;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in != null) {
                    read = CsvHeaderSniffer.sniff(
                            new InputStreamReader(in, StandardCharsets.UTF_8), delimiter);
                }
            } catch (Exception e) {
                Log.e(TAG, "could not read " + uri + " again", e);
            }
            CsvHeaderSniffer.Guess result = read;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (result == null || result.headings.isEmpty()) {
                    return;
                }
                guess = result;
                buildShape();
                buildFields();
                refresh();
            });
        }).start();
    }

    // ------------------------------------------------------------ the columns

    private void buildFields() {
        fieldRows.removeAllViews();
        fieldSpinners.clear();

        List<String> choices = new ArrayList<>();
        choices.add(getString(R.string.csv_map_not_present));
        for (int i = 0; i < guess.headings.size(); i++) {
            String heading = guess.headings.get(i);
            choices.add(heading.isEmpty() ? getString(R.string.csv_map_column, i + 1) : heading);
        }

        for (CsvField field : FIELDS) {
            Spinner spinner = row(fieldRows, label(field), choices,
                    guess.mapping.get(field) + 1);
            spinner.setOnItemSelectedListener(onChange(position -> {
                guess.mapping.put(field, position - 1);
                refresh();
            }));
            fieldSpinners.put(field, spinner);
        }
        showTheFieldsThatApply();
    }

    /**
     * One amount column or two, never both on screen at once. Offering all three
     * at the same time is how somebody ends up assigning two of them and importing
     * every expense twice.
     */
    private void showTheFieldsThatApply() {
        boolean two = guess.mapping.sign == CsvColumnMapping.Sign.TWO_COLUMNS;
        show(CsvField.AMOUNT, !two);
        show(CsvField.INCOME, two);
        show(CsvField.EXPENSE, two);
    }

    private void show(CsvField field, boolean visible) {
        Spinner spinner = fieldSpinners.get(field);
        if (spinner != null) {
            ((View) spinner.getParent()).setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private String label(CsvField field) {
        switch (field) {
            case DATE: return getString(R.string.date);
            case TIME: return getString(R.string.time);
            case AMOUNT: return getString(R.string.amount);
            case INCOME: return getString(R.string.income);
            case EXPENSE: return getString(R.string.expense);
            case TYPE: return getString(R.string.type);
            case ACCOUNT: return getString(R.string.account);
            case TRANSFER_ACCOUNT: return getString(R.string.csv_map_transfer_account);
            case PARENT_CATEGORY: return getString(R.string.csv_map_parent_category);
            case CATEGORY: return getString(R.string.category);
            case PAYEE: return getString(R.string.payee);
            case NOTE: return getString(R.string.note);
            case CURRENCY: return getString(R.string.currency);
            default: return field.name();
        }
    }

    // --------------------------------------------------------------- the proof

    /**
     * The first lines, read the way the assignment above says to read them.
     * <p>
     * This is the part that makes the screen honest. A comma taken for a decimal
     * point, a date read the American way round, an expense turned into income:
     * all of them are invisible in the column assignment and obvious here.
     */
    private void refresh() {
        continueButton.setEnabled(guess != null && guess.mapping.isComplete());
        if (guess == null) {
            return;
        }
        if (!guess.mapping.isComplete()) {
            preview.setText(R.string.csv_map_incomplete);
            return;
        }

        CsvRowReader reader = new CsvRowReader(guess.mapping);
        SimpleDateFormat day = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        StringBuilder text = new StringBuilder();
        int shown = 0, unreadable = 0;
        for (String[] values : guess.sample) {
            CsvRow row = reader.read(values);
            if (row == null) {
                unreadable++;
                continue;
            }
            if (shown++ >= PREVIEW_ROWS) {
                continue;
            }
            text.append(day.format(row.date)).append("   ")
                    .append(String.format(Locale.getDefault(), "%,.2f", row.amount / 100.0));
            String rest = describe(row);
            if (!rest.isEmpty()) {
                text.append("   ").append(rest);
            }
            text.append('\n');
        }
        if (unreadable > 0) {
            text.append('\n').append(getString(R.string.csv_map_unreadable_lines, unreadable));
        }
        preview.setText(text.toString().trim());
    }

    private String describe(CsvRow row) {
        StringBuilder text = new StringBuilder();
        if (row.transfer) {
            text.append("→ ").append(row.transferAccount == null
                    ? getString(R.string.transfer) : row.transferAccount);
        }
        append(text, row.account);
        append(text, row.category);
        append(text, row.payee);
        return text.toString();
    }

    private void append(StringBuilder text, String value) {
        if (value != null && !value.isEmpty()) {
            if (text.length() > 0) {
                text.append("  ·  ");
            }
            text.append(value);
        }
    }

    // -------------------------------------------------------------- next step

    /**
     * What the import would do, said out loud. Until the import itself is written
     * this is the whole of it, and it is worth keeping afterwards as the last thing
     * seen before anything is written.
     */
    private void describeWhatWouldHappen() {
        CsvRowReader reader = new CsvRowReader(guess.mapping);
        int readable = 0, unreadable = 0;
        for (String[] values : guess.sample) {
            if (reader.read(values) == null) {
                unreadable++;
            } else {
                readable++;
            }
        }
        StringBuilder text = new StringBuilder();
        text.append(getString(R.string.csv_map_would_read, readable));
        if (unreadable > 0) {
            text.append('\n').append(getString(R.string.csv_map_unreadable_lines, unreadable));
        }
        text.append("\n\n").append(getString(R.string.csv_map_not_yet));

        new AlertDialog.Builder(this)
                .setTitle(R.string.csv_map_title)
                .setMessage(text.toString())
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    // ------------------------------------------------------------------ plumbing

    private Spinner row(LinearLayout parent, String label, List<String> choices, int selected) {
        View view = LayoutInflater.from(this).inflate(R.layout.csv_mapping_row, parent, false);
        ((TextView) view.findViewById(R.id.csv_map_row_label)).setText(label);
        Spinner spinner = view.findViewById(R.id.csv_map_row_spinner);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, choices);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        if (selected >= 0 && selected < choices.size()) {
            spinner.setSelection(selected);
        }
        parent.addView(view);
        return spinner;
    }

    /** A listener that ignores the selection Android makes when the spinner is built. */
    private AdapterView.OnItemSelectedListener onChange(Chosen chosen) {
        return new AdapterView.OnItemSelectedListener() {
            private boolean first = true;

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (first) {
                    first = false;
                    return;
                }
                chosen.at(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        };
    }

    private interface Chosen {
        void at(int position);
    }

    private static int indexOf(char[] values, char value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == value) {
                return i;
            }
        }
        return 0;
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
