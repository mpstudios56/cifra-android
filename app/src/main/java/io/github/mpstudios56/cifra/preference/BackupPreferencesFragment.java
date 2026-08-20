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

    /**
     * Which half of this screen is wanted.
     * <p>
     * The settings and the buttons that use them are one screen, and that
     * screen is opened twice from the menu: once for the lock and the copy kept
     * on this phone, once for the two services a copy can be sent to. The
     * groups that do not belong to the half asked for are hidden rather than
     * written twice.
     */
    public static final String ONLY_EXTRA = "only";
    public static final String ONLY_LOCAL = "local";
    public static final String ONLY_ONLINE = "online";

    /**
     * Which half this screen is.
     * <p>
     * Said by the class itself rather than by a message handed to it: the
     * message travelled through an activity that keeps a single instance of
     * itself, and a second opening went on showing the first one’s answer.
     */
    protected boolean isOnline() {
        return false;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        title = isOnline() ? R.string.backup_online : R.string.menu_backup_security;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // The lock first, then the copies: two subjects that are one worry.
        setPreferencesFromResource(R.xml.pref_security, rootKey);
        addPreferencesFromResource(R.xml.pref_backup);

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

        wireActions();
        sayIfFingerprintUnavailable();
        showOnlyWhatWasAskedFor();
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
            case RESTORE_DATABASE:
                if (data != null && data.getData() != null) {
                    android.app.ProgressDialog d = android.app.ProgressDialog.show(context, null,
                            getString(R.string.restore_database_inprogress), true);
                    new io.github.mpstudios56.cifra.export.BackupImportTask(getActivity(), d)
                            .execute(data.getData());
                }
                break;

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
        // The PIN keypad is a dialog of our own, and without this the framework
        // opens an empty one in its place.
        if (preference instanceof PinPreference) {
            PinDialogFragment pin = PinDialogFragment.newInstance(preference.getKey());
            pin.setTargetFragment(this, 0);
            pin.show(getParentFragmentManager(), null);
            return;
        }
        // The nightly backup time is picked on a clock of our own.
        if (preference instanceof TimePreference) {
            TimeDialogFragment f = TimeDialogFragment.newInstance(preference.getKey());
            f.setTargetFragment(this, 0);
            f.show(getParentFragmentManager(), null);
            return;
        }
        super.onDisplayPreferenceDialog(preference);
    }

    /** The buttons that make a copy, bring one back, or send one away. */
    private void wireActions() {
        onClick("do_backup", () -> io.github.mpstudios56.cifra.activity.MenuListItem.backupNow(this));
        onClick("do_restore", this::chooseBackupToRestore);
        onClick("do_backup_to", () -> io.github.mpstudios56.cifra.activity.MenuListItem.backupTo(this));
        onClick("do_drive_backup", this::driveBackup);
        onClick("do_drive_restore", this::driveRestore);
        onClick("do_dropbox_backup", this::dropboxBackup);
        onClick("do_dropbox_restore", this::dropboxRestore);
    }

    private void onClick(String key, Runnable what) {
        Preference p = getPreferenceScreen().findPreference(key);
        if (p != null) {
            p.setOnPreferenceClickListener(clicked -> {
                what.run();
                return true;
            });
        }
    }

    /**
     * Offering a fingerprint on a phone that has none, or has none enrolled, is
     * offering a lock with no key: say why instead.
     */
    private void sayIfFingerprintUnavailable() {
        Preference fingerprint = getPreferenceScreen().findPreference("pin_protection_use_fingerprint");
        if (fingerprint != null
                && io.github.mpstudios56.cifra.utils.FingerprintUtils.fingerprintUnavailable(getContext())) {
            fingerprint.setSummary(getString(R.string.fingerprint_unavailable,
                    io.github.mpstudios56.cifra.utils.FingerprintUtils
                            .reasonWhyFingerprintUnavailable(getContext())));
            fingerprint.setEnabled(false);
        }
    }

    /** Hides the groups that belong to the other half of this screen. */
    private void showOnlyWhatWasAskedFor() {
        boolean online = isOnline();
        hide("cat_protection", online);
        hide("cat_backup_local", online);
        hide("cat_dropbox", !online);
        hide("cat_drive", !online);
    }

    private void hide(String key, boolean hidden) {
        Preference p = getPreferenceScreen().findPreference(key);
        if (p != null) {
            p.setVisible(!hidden);
        }
    }


    // -------------------------------------------- making one and bringing it back

    private static final int RESTORE_DATABASE = 103;

    private void chooseBackupToRestore() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI,
                Export.getBackupFolder(getContext()));
        startActivityForResult(intent, RESTORE_DATABASE);
    }

    private void driveBackup() {
        android.app.ProgressDialog d = android.app.ProgressDialog.show(getContext(), null,
                getString(R.string.backup_database_gdocs_inprogress), true);
        new io.github.mpstudios56.cifra.export.drive.GoogleDriveBackupTask(getActivity(), d).execute();
    }

    private void driveRestore() {
        android.app.ProgressDialog d = android.app.ProgressDialog.show(getContext(), null,
                getString(R.string.google_drive_loading_files), true);
        new io.github.mpstudios56.cifra.export.drive.GoogleDriveListFilesTask(getActivity(), d).execute();
    }

    private void dropboxBackup() {
        android.app.ProgressDialog d = android.app.ProgressDialog.show(getContext(), null,
                getString(R.string.backup_database_dropbox_inprogress), true);
        new io.github.mpstudios56.cifra.export.dropbox.DropboxBackupTask(getActivity(), d).execute();
    }

    private void dropboxRestore() {
        android.app.ProgressDialog d = android.app.ProgressDialog.show(getContext(), null,
                getString(R.string.dropbox_loading_files), true);
        new io.github.mpstudios56.cifra.export.dropbox.DropboxListFilesTask(getActivity(), d).execute();
    }

    // ---------------------------------------- listening for what comes back
    //
    // The tasks that talk to Drive and to Dropbox do not return an answer to
    // whoever started them: they announce it, and whoever is listening picks it
    // up. The listening used to be done by the menu screen, which is where the
    // buttons used to be. The buttons are here now, so the listening is here
    // too - without it the account was chosen, the task ran, and nothing at all
    // appeared to happen.

    private io.github.mpstudios56.cifra.bus.GreenRobotBus announcements;

    private io.github.mpstudios56.cifra.bus.GreenRobotBus announcements() {
        if (announcements == null) {
            announcements = io.github.mpstudios56.cifra.bus.GreenRobotBus_.getInstance_(getContext());
        }
        return announcements;
    }

    @Override
    public void onStart() {
        super.onStart();
        announcements().register(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        announcements().unregister(this);
    }

    /** The backups found on Drive, offered one to be brought back. */
    @org.greenrobot.eventbus.Subscribe(threadMode = org.greenrobot.eventbus.ThreadMode.MAIN)
    public void onGoogleDriveFileList(io.github.mpstudios56.cifra.export.drive.GoogleDriveFileList event) {
        final io.github.mpstudios56.cifra.export.drive.GoogleDriveFileInfo[] files = event.files;
        final String[] names = new String[files.length];
        for (int i = 0; i < files.length; i++) {
            names[i] = files[i].name;
        }
        final Context context = getContext();
        final io.github.mpstudios56.cifra.export.drive.GoogleDriveFileInfo[] chosen =
                new io.github.mpstudios56.cifra.export.drive.GoogleDriveFileInfo[1];
        new androidx.appcompat.app.AlertDialog.Builder(context)
                .setTitle(R.string.restore_database_online_google_drive)
                .setSingleChoiceItems(names, -1, (dialog, which) -> {
                    if (which >= 0 && which < names.length) {
                        chosen[0] = files[which];
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.restore, (dialog, which) -> {
                    if (chosen[0] != null) {
                        android.app.ProgressDialog d = android.app.ProgressDialog.show(context, null,
                                getString(R.string.google_drive_restore_in_progress), true);
                        new io.github.mpstudios56.cifra.export.drive.GoogleDriveRestoreTask(
                                getActivity(), d, chosen[0]).execute();
                    }
                })
                .show();
    }

    /** The same, on Dropbox. */
    @org.greenrobot.eventbus.Subscribe(threadMode = org.greenrobot.eventbus.ThreadMode.MAIN)
    public void onDropboxFileList(io.github.mpstudios56.cifra.export.dropbox.DropboxFileList event) {
        final String[] files = event.files;
        if (files == null) {
            return;
        }
        final String[] chosen = new String[1];
        new androidx.appcompat.app.AlertDialog.Builder(getContext())
                .setTitle(R.string.restore_database_online_dropbox)
                .setSingleChoiceItems(files, -1, (dialog, which) -> {
                    if (which >= 0 && which < files.length) {
                        chosen[0] = files[which];
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.restore, (dialog, which) -> {
                    if (chosen[0] != null) {
                        android.app.ProgressDialog d = android.app.ProgressDialog.show(getContext(), null,
                                getString(R.string.restore_database_inprogress_dropbox), true);
                        new io.github.mpstudios56.cifra.export.dropbox.DropboxRestoreTask(
                                getActivity(), d, chosen[0]).execute();
                    }
                })
                .show();
    }
}
