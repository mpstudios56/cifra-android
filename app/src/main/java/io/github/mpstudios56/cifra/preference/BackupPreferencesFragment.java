package io.github.mpstudios56.cifra.preference;

import static android.app.Activity.RESULT_OK;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;

import io.github.mpstudios56.cifra.R;
import io.github.mpstudios56.cifra.export.Export;
import io.github.mpstudios56.cifra.export.drive.GoogleDriveAuthorizeFolderTask;
import io.github.mpstudios56.cifra.export.dropbox.Dropbox;
import io.github.mpstudios56.cifra.utils.MyPreferences;

/**
 * Where the copies go: the folder, the nightly copy, Dropbox and Google Drive.
 * <p>
 * Reached from the backup menu, next to the buttons that make and restore a
 * copy. It used to be at the bottom of the general settings, so setting a
 * backup up meant working in two screens at once.
 */
public class BackupPreferencesFragment extends PreferenceFragmentBase {

    private static final int SELECT_DATABASE_FOLDER = 100;
    private static final int CHOOSE_ACCOUNT = 101;
    private static final int REQUEST_AUTHORIZATION = 102;

    private Dropbox dropbox;

    private Preference pGoogleDriveSignIn;
    private Preference pGoogleDriveSignOut;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        title = R.string.database_backup;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.pref_backup, rootKey);

        Context context = getContext();
        PreferenceScreen screen = getPreferenceScreen();
        dropbox = new Dropbox(context);

        screen.findPreference("database_backup_folder").setOnPreferenceClickListener(p -> {
            selectDatabaseBackupFolder();
            return true;
        });
        screen.findPreference("dropbox_authorize").setOnPreferenceClickListener(p -> {
            dropbox.startAuth();
            return true;
        });
        screen.findPreference("dropbox_unlink").setOnPreferenceClickListener(p -> {
            dropbox.deAuth();
            linkToDropbox();
            return true;
        });

        pGoogleDriveSignIn = screen.findPreference("google_drive_backup_account");
        pGoogleDriveSignIn.setOnPreferenceClickListener(p -> {
            chooseAccount();
            return true;
        });
        pGoogleDriveSignOut = screen.findPreference("google_drive_sign_out");
        pGoogleDriveSignOut.setOnPreferenceClickListener(p -> {
            signOutGoogleAccount();
            return true;
        });
        screen.findPreference("google_drive_backup_folder")
                .setOnPreferenceChangeListener((preference, newValue) -> {
                    new GoogleDriveAuthorizeFolderTask(getActivity(),
                            (String) newValue, REQUEST_AUTHORIZATION).execute();
                    return true;
                });

        updateGoogleDriveSignIn(GoogleSignIn.getLastSignedInAccount(context));
        linkToDropbox();
        setCurrentDatabaseBackupFolder();
        selectAccount();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Dropbox hands control back through the browser: whatever came of that
        // has to be picked up here, when the screen returns to the front.
        dropbox.completeAuth();
        linkToDropbox();
    }

    private void updateGoogleDriveSignIn(GoogleSignInAccount account) {
        if (account == null) {
            pGoogleDriveSignIn.setEnabled(true);
            pGoogleDriveSignIn.setSummary(R.string.google_drive_backup_account_summary);
            pGoogleDriveSignOut.setEnabled(false);
        } else {
            pGoogleDriveSignIn.setEnabled(false);
            pGoogleDriveSignIn.setSummary(getString(R.string.google_drive_signed_in_as,
                    account.getEmail()));
            pGoogleDriveSignOut.setEnabled(true);
        }
    }

    private void chooseAccount() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build();
        startActivityForResult(GoogleSignIn.getClient(getContext(), gso).getSignInIntent(), CHOOSE_ACCOUNT);
    }

    private void signOutGoogleAccount() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build();
        GoogleSignInClient client = GoogleSignIn.getClient(getContext(), gso);
        client.signOut().addOnCompleteListener(getActivity(), task -> {
            Toast.makeText(getContext(), R.string.google_drive_signed_out, Toast.LENGTH_LONG).show();
            pGoogleDriveSignOut.setEnabled(false);
            pGoogleDriveSignIn.setEnabled(true);
            pGoogleDriveSignIn.setSummary(R.string.google_drive_backup_account_summary);
        });
    }

    private Account getSelectedAccount() {
        String accountName = MyPreferences.getGoogleDriveAccount();
        if (accountName != null) {
            AccountManager accountManager = AccountManager.get(getContext());
            for (Account account : accountManager.getAccountsByType("com.google")) {
                if (accountName.equals(account.name)) {
                    return account;
                }
            }
        }
        return null;
    }

    private void selectAccount() {
        Account account = getSelectedAccount();
        if (account != null) {
            pGoogleDriveSignIn.setSummary(account.name);
        }
    }

    /** The three Dropbox switches mean nothing until the account is linked. */
    private void linkToDropbox() {
        boolean authorized = MyPreferences.isDropboxAuthorized();
        PreferenceScreen screen = getPreferenceScreen();
        screen.findPreference("dropbox_authorize").setEnabled(!authorized);
        screen.findPreference("dropbox_unlink").setEnabled(authorized);
        screen.findPreference("dropbox_upload_backup").setEnabled(authorized);
        screen.findPreference("dropbox_upload_autobackup").setEnabled(authorized);
    }

    private void selectDatabaseBackupFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, getDatabaseBackupFolder());
        startActivityForResult(intent, SELECT_DATABASE_FOLDER);
    }

    private String getDatabaseBackupFolder() {
        return Export.getBackupFolder(getContext());
    }

    private void setCurrentDatabaseBackupFolder() {
        Preference p = getPreferenceScreen().findPreference("database_backup_folder");
        p.setSummary(getString(R.string.database_backup_folder_summary,
                Uri.parse(getDatabaseBackupFolder()).getLastPathSegment()));
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;
        Context context = getContext();
        switch (requestCode) {
            case SELECT_DATABASE_FOLDER:
                if (data != null) {
                    Uri folder = data.getData();
                    context.getContentResolver().takePersistableUriPermission(folder,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    MyPreferences.setDatabaseBackupFolder(folder.toString());
                    setCurrentDatabaseBackupFolder();
                } else {
                    Log.e("Cifra", "select database folder data is null");
                }
                break;

            case CHOOSE_ACCOUNT:
                GoogleSignInAccount account = GoogleSignIn.getSignedInAccountFromIntent(data).getResult();
                new GoogleDriveAuthorizeFolderTask(getActivity(),
                        MyPreferences.getGoogleDriveBackupFolder(), REQUEST_AUTHORIZATION).execute();
                Toast.makeText(context, getString(R.string.google_drive_signed_in_as, account.getEmail()),
                        Toast.LENGTH_LONG).show();
                updateGoogleDriveSignIn(account);
                break;

            case REQUEST_AUTHORIZATION:
                Toast.makeText(context, R.string.google_drive_authorized, Toast.LENGTH_LONG).show();
                break;
        }
    }

    @Override
    public void onDisplayPreferenceDialog(@NonNull Preference preference) {
        // The nightly backup time is picked on a clock of our own.
        if (preference instanceof TimePreference) {
            TimeDialogFragment f = TimeDialogFragment.newInstance(preference.getKey());
            f.setTargetFragment(this, 0);
            f.show(getParentFragmentManager(), null);
            return;
        }
        super.onDisplayPreferenceDialog(preference);
    }
}
