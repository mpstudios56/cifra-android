/*
 * This program is made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */
package tw.tib.financisto.activity;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import tw.tib.financisto.R;
import tw.tib.financisto.db.DatabaseAdapter;
import tw.tib.financisto.sync.SyncEngine;
import tw.tib.financisto.utils.CategoryIcon;
import tw.tib.financisto.utils.Identity;
import tw.tib.financisto.utils.MyPreferences;
import tw.tib.financisto.utils.PinProtection;

/**
 * Keeping one ledger between two people.
 * <p>
 * A screen of its own rather than a group of settings. Buried among forty
 * switches nobody would find it, and it is not a preference in any case: it is
 * a thing the app does, and things the app does live in the menu.
 * <p>
 * Who the two people are, where their phones meet, and the record of what each
 * of them changed. Opening it runs a round of the exchange, because somebody
 * who came here to see whether the other person's spending has arrived should
 * not have to ask for it.
 */
public class SharingActivity extends AppCompatActivity {

    private static final String TAG = "SharingActivity";
    private static final int PICK_FOLDER = 1;

    private DatabaseAdapter db;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.sharing);
        setSupportActionBar(findViewById(R.id.toolbar));
        setTitle(R.string.sharing);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.sharing_base), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        findViewById(R.id.sharing_me).setOnClickListener(v ->
                IdentityDialog.show(this, Identity.MINE, this::show));
        findViewById(R.id.sharing_them).setOnClickListener(v ->
                IdentityDialog.show(this, Identity.THEIRS, this::show));
        findViewById(R.id.sharing_log).setOnClickListener(v ->
                startActivity(new Intent(this, ChangeLogActivity.class)));
        findViewById(R.id.sharing_folder).setOnClickListener(v -> pickFolder());
        findViewById(R.id.sharing_group).setOnClickListener(v -> askGroupCode());
        findViewById(R.id.sharing_people).setOnClickListener(v -> showPeople());
        findViewById(R.id.sharing_now).setOnClickListener(v -> sync(true));
        findViewById(R.id.sharing_duplicates).setOnClickListener(v ->
                startActivity(new Intent(this, DuplicatesActivity.class)));
        findViewById(R.id.sharing_merge).setOnClickListener(v ->
                startActivity(new Intent(this, MergeActivity.class)));
        findViewById(R.id.sharing_what).setOnClickListener(v ->
                startActivity(new Intent(this, SharePickerActivity.class)));

        db = new DatabaseAdapter(this);
        db.open();
        show();
        // A round on opening: somebody who came here to see whether the other
        // person's spending has arrived should not have to ask for it.
        sync(false);
    }

    /**
     * One round, off the main thread. The folder is somebody else's storage
     * provider with a cloud behind it, and it takes as long as it takes.
     */
    private void sync(boolean saySo) {
        Button button = findViewById(R.id.sharing_now);
        button.setEnabled(false);
        button.setText(R.string.sharing_running);
        new Thread(() -> {
            SyncEngine.Result result = SyncEngine.run(this, db);
            new Handler(Looper.getMainLooper()).post(() -> {
                button.setEnabled(true);
                button.setText(R.string.sharing_now);
                show();
                if (saySo || result.received > 0) {
                    Toast.makeText(this, message(result), Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private String message(SyncEngine.Result result) {
        if (!result.ran) {
            return getString(R.string.sharing_no_folder);
        }
        if (!result.skipped.isEmpty()) {
            return getString(R.string.sharing_skipped, result.skipped.get(0));
        }
        return result.received > 0
                ? getString(R.string.sharing_received, result.received)
                : getString(R.string.sharing_nothing_new);
    }

    /**
     * Picks the folder the two phones will meet in.
     * <p>
     * Any folder: the app has no idea whether it is on Drive, on Dropbox or on
     * the phone itself, and does not need one. What it needs is that whatever
     * keeps that folder in step is already installed and already working, which
     * is a thing the person choosing knows and the app cannot check.
     */
    private void pickFolder() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(intent, PICK_FOLDER);
        } catch (Exception e) {
            Toast.makeText(this, R.string.sharing_folder_failed, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_FOLDER || resultCode != RESULT_OK || data == null
                || data.getData() == null) {
            return;
        }
        Uri folder = data.getData();
        try {
            // Held on to across restarts: without this the address still reads
            // back but opening anything in it fails, weeks later, silently.
            getContentResolver().takePersistableUriPermission(folder,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (Exception e) {
            Log.e(TAG, "could not keep hold of " + folder, e);
        }
        MyPreferences.setSyncFolder(folder.toString());
        show();
    }

    /** The last part of the address, which is what somebody called the folder. */
    private String folderName(String uri) {
        try {
            String decoded = Uri.decode(uri);
            int cut = Math.max(decoded.lastIndexOf('/'), decoded.lastIndexOf(':'));
            return cut >= 0 && cut < decoded.length() - 1 ? decoded.substring(cut + 1) : decoded;
        } catch (Exception e) {
            return uri;
        }
    }

    private void show() {
        show(Identity.mine(this), R.id.sharing_me_name, R.id.sharing_me_dot,
                R.id.sharing_me_icon, R.string.sharing_me_empty);
        show(Identity.theirs(this), R.id.sharing_them_name, R.id.sharing_them_dot,
                R.id.sharing_them_icon, R.string.sharing_them_empty);

        String folder = MyPreferences.getSyncFolder();
        ((TextView) findViewById(R.id.sharing_folder_value)).setText(folder.isEmpty()
                ? getString(R.string.sharing_folder_empty)
                : getString(R.string.sharing_folder_chosen, folderName(folder)));

        int chosen = 0;
        for (String kind : tw.tib.financisto.sync.SharedThings.KINDS) {
            chosen += tw.tib.financisto.sync.SharedThings.shared(db.db(), kind).size();
        }
        ((TextView) findViewById(R.id.sharing_what_value)).setText(chosen == 0
                ? getString(R.string.share_what_none)
                : getString(R.string.share_what_some, chosen));

        int waiting = tw.tib.financisto.sync.Duplicates.waiting(db.db());
        ((TextView) findViewById(R.id.sharing_duplicates_value)).setText(waiting == 0
                ? getString(R.string.duplicates_none_short)
                : getString(R.string.duplicates_waiting, waiting));

        long last = MyPreferences.getSyncLastRun();
        ((TextView) findViewById(R.id.sharing_last)).setText(last == 0
                ? getString(R.string.sharing_never)
                : getString(R.string.sharing_last, android.text.format.DateFormat
                        .getTimeFormat(this).format(new java.util.Date(last))));
        java.util.List<tw.tib.financisto.sync.People.Person> people =
                tw.tib.financisto.sync.People.all(db.db());
        StringBuilder who = new StringBuilder();
        for (tw.tib.financisto.sync.People.Person p : people) {
            if (who.length() > 0) who.append(", ");
            who.append(p.label());
        }
        ((TextView) findViewById(R.id.sharing_people_value)).setText(people.isEmpty()
                ? getString(R.string.sharing_people_none_yet)
                : who.toString());

        String group = MyPreferences.getSyncGroupCode();
        ((TextView) findViewById(R.id.sharing_group_value)).setText(group.isEmpty()
                ? getString(R.string.sharing_group_empty)
                : group);

        TextView file = findViewById(R.id.sharing_file);
        file.setText(getString(R.string.sharing_file_is, tw.tib.financisto.sync.SyncFolder.nameFor(
                MyPreferences.getSyncAuthor(), MyPreferences.getSyncDeviceId())));

        findViewById(R.id.sharing_now).setVisibility(folder.isEmpty() ? View.GONE : View.VISIBLE);
        findViewById(R.id.sharing_last).setVisibility(folder.isEmpty() ? View.GONE : View.VISIBLE);
        file.setVisibility(folder.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /**
     * Who is in the group, and how they got there.
     * <p>
     * Nobody is typed in. Every phone writes a file called after its owner, so
     * one round of exchange is enough to learn who else is in the folder and
     * what to call them. This screen only shows the result - and says so, since
     * otherwise the obvious question is where the button to add somebody is.
     */
    private void showPeople() {
        java.util.List<tw.tib.financisto.sync.People.Person> people =
                tw.tib.financisto.sync.People.all(db.db());
        String[] rows = new String[people.size() + 1];
        for (int k = 0; k < people.size(); k++) {
            tw.tib.financisto.sync.People.Person p = people.get(k);
            int held = tw.tib.financisto.sync.People.accountsWith(db.db(), p.mark);
            rows[k] = p.label() + "  (" + p.mark + ")"
                    + System.lineSeparator()
                    + getResources().getQuantityString(R.plurals.sharing_person_accounts, held, held);
        }
        rows[people.size()] = getString(R.string.sharing_person_add);

        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.sharing_people)
                .setItems(rows, (d, which) -> {
                    if (which == people.size()) {
                        askPerson(null);
                    } else {
                        askPerson(people.get(which));
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * A person and the code shared with them.
     * <p>
     * The same code has to be written on their phone against this phone's
     * owner: that pair of entries is what makes the pair. Nothing checks it -
     * there is nothing to check it against - so if the exchange stays empty the
     * first thing to compare is the two codes.
     */
    private void askPerson(tw.tib.financisto.sync.People.Person existing) {
        final android.widget.EditText name = new android.widget.EditText(this);
        name.setSingleLine(true);
        name.setHint(R.string.sharing_person_name);
        final android.widget.EditText code = new android.widget.EditText(this);
        code.setSingleLine(true);
        code.setHint(R.string.sharing_person_code);
        if (existing != null) {
            name.setText(existing.name);
            code.setText(existing.mark);
        }
        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = Math.round(16 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(name);
        box.addView(code);

        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this)
                .setTitle(existing == null ? R.string.sharing_person_add : R.string.sharing_people)
                .setMessage(R.string.sharing_person_why)
                .setView(box)
                .setPositiveButton(R.string.ok, (d, w) -> {
                    if (existing != null) {
                        tw.tib.financisto.sync.People.forget(db.db(), existing.mark);
                    }
                    tw.tib.financisto.sync.People.seen(db.db(),
                            code.getText().toString(), name.getText().toString());
                    show();
                })
                .setNegativeButton(R.string.cancel, null);
        if (existing != null) {
            b.setNeutralButton(R.string.delete, (d, w) -> {
                tw.tib.financisto.sync.People.forget(db.db(), existing.mark);
                show();
            });
        }
        b.show();
    }

    /**
     * The code everybody in the group writes, the same on every phone.
     * <p>
     * Free text on purpose: it is not a password and it is not checked against
     * anything - it only has to be typed the same way by the people who mean to
     * be together. Changing it changes the name of the file this phone writes,
     * so the old one is left behind in the folder and should be deleted.
     */
    private void askGroupCode() {
        final android.widget.EditText field = new android.widget.EditText(this);
        field.setSingleLine(true);
        field.setText(MyPreferences.getSyncGroupCode());
        field.setHint(R.string.sharing_group_hint);
        int pad = Math.round(16 * getResources().getDisplayMetrics().density);
        android.widget.FrameLayout box = new android.widget.FrameLayout(this);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(field);

        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.sharing_group)
                .setMessage(R.string.sharing_group_why)
                .setView(box)
                .setPositiveButton(R.string.ok, (d, w) -> {
                    MyPreferences.setSyncGroupCode(field.getText().toString());
                    show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void show(Identity identity, int nameId, int dotId, int iconId, int emptyId) {
        TextView name = findViewById(nameId);
        name.setText(identity.name.isEmpty() ? getString(emptyId) : identity.name);
        name.setAlpha(identity.name.isEmpty() ? 0.6f : 1f);

        findViewById(dotId).getBackground().setTint(identity.colour);

        ImageView icon = findViewById(iconId);
        CategoryIcon chosen = CategoryIcon.parse(identity.icon);
        icon.setVisibility(chosen == null ? View.GONE : View.VISIBLE);
        if (chosen != null) {
            icon.setImageResource(chosen.iconId);
            icon.setImageTintList(ColorStateList.valueOf(identity.colour));
        }
    }

    @Override
    protected void onDestroy() {
        if (db != null) {
            db.close();
        }
        super.onDestroy();
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
        if (db != null) {
            // Coming back from the picker, the count has changed.
            show();
        }
    }
}
