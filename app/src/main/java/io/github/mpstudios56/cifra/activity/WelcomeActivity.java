/*
 * This program is made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */
package io.github.mpstudios56.cifra.activity;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.documentfile.provider.DocumentFile;

import io.github.mpstudios56.cifra.R;
import io.github.mpstudios56.cifra.backup.DatabaseImport;
import io.github.mpstudios56.cifra.db.DatabaseAdapter;
import io.github.mpstudios56.cifra.export.Export;
import io.github.mpstudios56.cifra.service.DailyAutoBackupScheduler;
import io.github.mpstudios56.cifra.utils.LocalCurrency;
import io.github.mpstudios56.cifra.utils.MyPreferences;

/**
 * The first screen, shown once and never again.
 * <p>
 * It asks two things, both worth more on the first day than on any later one: a
 * backup that was never switched on protects nothing, and an app with no data in
 * it explains nothing about what it is for. Everything else it could ask can
 * wait until there is a reason to care.
 */
public class WelcomeActivity extends AppCompatActivity {

    private static final String TAG = "WelcomeActivity";
    private static final String PREFS = "welcome";
    private static final String KEY_DONE = "done";
    private static final String DEMO_ASSET = "demo.backup";

    private CheckBox backupBox;
    private Button currencyButton;
    private String currencyCode;
    private CheckBox demoBox;
    private Button folderButton;
    private ActivityResultLauncher<Uri> pickFolder;

    /**
     * True only for an app that has never been through this screen and has nothing
     * in it yet.
     * <p>
     * The second half matters more than the first: someone upgrading from an older
     * version has no mark either, and offering them sample data would replace
     * everything they had. An app with accounts in it is past being welcomed.
     */
    public static boolean isPending(Context context) {
        if (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DONE, false)) {
            return false;
        }
        if (hasData(context)) {
            markDone(context);
            return false;
        }
        return true;
    }

    private static boolean hasData(Context context) {
        DatabaseAdapter db = new DatabaseAdapter(context);
        db.open();
        try (Cursor c = db.db().rawQuery("select 1 from account limit 1", null)) {
            return c.moveToFirst();
        } catch (Exception e) {
            // A database that cannot be read is not one to start pouring data into.
            Log.e(TAG, "could not check whether the app already has data", e);
            return true;
        } finally {
            db.close();
        }
    }

    private static void markDone(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_DONE, true).apply();
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(MyPreferences.switchLocale(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (MyPreferences.isSecureWindow()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
        setContentView(R.layout.welcome);
        setTitle(R.string.welcome_title);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.welcome_base), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            // Left and right as well: held sideways the navigation bar is down
            // one side of the screen, and padding only the top and bottom slid
            // the content under it.
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        backupBox = findViewById(R.id.welcome_backup);
        demoBox = findViewById(R.id.welcome_demo);
        folderButton = findViewById(R.id.welcome_folder);

        pickFolder = registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), uri -> {
            if (uri == null) {
                return;
            }
            getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            MyPreferences.setDatabaseBackupFolder(uri.toString());
            showFolder();
        });

        currencyButton = findViewById(R.id.welcome_currency);
        currencyCode = LocalCurrency.guessCode();
        showCurrency();
        currencyButton.setOnClickListener(v -> pickCurrency());

        folderButton.setOnClickListener(v -> pickFolder.launch(null));
        backupBox.setOnCheckedChangeListener((b, checked) -> showFolder());
        findViewById(R.id.welcome_start).setOnClickListener(v -> start());
        showFolder();
    }

    private void showCurrency() {
        currencyButton.setText(LocalCurrency.describe(currencyCode));
    }

    /**
     * The codes worth offering without a search box. Whatever the phone says comes
     * first, so the common case is one tap or none.
     */
    private void pickCurrency() {
        List<String> codes = new ArrayList<>();
        codes.add(currencyCode);
        for (String code : new String[]{"EUR", "USD", "GBP", "CHF", "JPY", "CAD",
                "AUD", "SEK", "NOK", "DKK", "PLN", "CZK", "HUF", "RON", "BGN",
                "BRL", "MXN", "INR", "CNY", "ZAR", "TRY"}) {
            if (!codes.contains(code)) {
                codes.add(code);
            }
        }
        String[] labels = new String[codes.size()];
        for (int i = 0; i < codes.size(); i++) {
            labels[i] = LocalCurrency.describe(codes.get(i));
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.welcome_currency)
                .setItems(labels, (d, which) -> {
                    currencyCode = codes.get(which);
                    showCurrency();
                })
                .show();
    }

    private void showFolder() {
        folderButton.setEnabled(backupBox.isChecked());
        folderButton.setAlpha(backupBox.isChecked() ? 1f : 0.4f);
        String folder = MyPreferences.getDatabaseBackupFolder();
        DocumentFile chosen = folder != null && folder.startsWith("content:")
                ? DocumentFile.fromTreeUri(this, Uri.parse(folder)) : null;
        folderButton.setText(chosen != null && chosen.getName() != null
                ? getString(R.string.welcome_folder_chosen, chosen.getName())
                : getString(R.string.welcome_choose_folder));
    }

    private void start() {
        if (backupBox.isChecked()) {
            // Asking here rather than sending them to the settings later is the whole
            // point: a backup nobody switched on is not a backup.
            String folder = MyPreferences.getDatabaseBackupFolder();
            if (folder == null || !folder.startsWith("content:")) {
                Toast.makeText(this, R.string.welcome_folder_needed, Toast.LENGTH_LONG).show();
                pickFolder.launch(null);
                return;
            }
            MyPreferences.setAutoBackupEnabled(true);
            DailyAutoBackupScheduler.scheduleNextAutoBackup(this);
        }

        // Before anything else: an app with no currency cannot hold a transaction,
        // and inventing one by hand is a poor first task.
        DatabaseAdapter db = new DatabaseAdapter(this);
        db.open();
        try {
            LocalCurrency.createIfMissing(this, db, currencyCode);
        } finally {
            db.close();
        }

        if (demoBox.isChecked()) {
            loadSampleData();
        } else {
            finishSetup();
        }
    }

    private void loadSampleData() {
        ProgressDialog progress = ProgressDialog.show(this, null,
                getString(R.string.welcome_demo_loading), true);
        new Thread(() -> {
            String error = null;
            DatabaseAdapter db = new DatabaseAdapter(this);
            db.open();
            try {
                DatabaseImport.createFromAsset(this, db, DEMO_ASSET).importDatabase();
            } catch (Exception e) {
                Log.e(TAG, "could not load the sample data", e);
                error = e.getMessage();
            } finally {
                db.close();
            }
            final String failed = error;
            new Handler(Looper.getMainLooper()).post(() -> {
                progress.dismiss();
                if (failed != null) {
                    Toast.makeText(this, R.string.welcome_demo_failed, Toast.LENGTH_LONG).show();
                }
                finishSetup();
            });
        }).start();
    }

    private void finishSetup() {
        markDone(this);
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
