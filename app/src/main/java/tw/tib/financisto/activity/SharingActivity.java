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
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import tw.tib.financisto.R;
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
 * What is here today is who the two people are and the record of what each of
 * them changed. The exchange between the two phones is not built yet, and the
 * screen says so rather than implying otherwise - a screen that looks finished
 * and does nothing is worse than one that admits where it has got to.
 */
public class SharingActivity extends AppCompatActivity {

    private static final String TAG = "SharingActivity";
    private static final int PICK_FOLDER = 1;

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

        show();
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
