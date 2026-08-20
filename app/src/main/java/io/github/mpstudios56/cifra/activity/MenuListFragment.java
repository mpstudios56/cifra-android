package io.github.mpstudios56.cifra.activity;

import static android.app.Activity.RESULT_OK;
import static io.github.mpstudios56.cifra.service.DailyAutoBackupScheduler.scheduleNextAutoBackup;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.Toast;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.ListFragment;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Bean;
import org.androidannotations.annotations.EFragment;
import org.androidannotations.annotations.OnActivityResult;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import io.github.mpstudios56.cifra.R;
import io.github.mpstudios56.cifra.adapter.SummaryEntityListAdapter;
import io.github.mpstudios56.cifra.bus.GreenRobotBus;
import io.github.mpstudios56.cifra.export.BackupImportTask;
import io.github.mpstudios56.cifra.export.SettingsImportTask;
import io.github.mpstudios56.cifra.export.csv.CsvExportOptions;
import io.github.mpstudios56.cifra.export.csv.CsvImportOptions;
import io.github.mpstudios56.cifra.export.drive.GoogleDriveBackupTask;
import io.github.mpstudios56.cifra.export.drive.GoogleDriveFileInfo;
import io.github.mpstudios56.cifra.export.drive.GoogleDriveFileList;
import io.github.mpstudios56.cifra.export.drive.GoogleDriveListFilesTask;
import io.github.mpstudios56.cifra.export.drive.GoogleDriveRestoreTask;
import io.github.mpstudios56.cifra.export.dropbox.DropboxBackupTask;
import io.github.mpstudios56.cifra.export.dropbox.DropboxFileList;
import io.github.mpstudios56.cifra.export.dropbox.DropboxListFilesTask;
import io.github.mpstudios56.cifra.export.dropbox.DropboxRestoreTask;
import io.github.mpstudios56.cifra.export.qif.QifExportOptions;
import io.github.mpstudios56.cifra.export.qif.QifImportOptions;
import io.github.mpstudios56.cifra.utils.PinProtection;

@EFragment(R.layout.activity_menu_list)
public class MenuListFragment extends ListFragment {
    private static final int RESOLVE_CONNECTION_REQUEST_CODE = 1;

    @Bean
    GreenRobotBus bus;

    @AfterViews
    protected void init() {
        ViewCompat.setOnApplyWindowInsetsListener(getView().findViewById(android.R.id.list), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.statusBars()
                    | WindowInsetsCompat.Type.captionBar());
            // Nothing stands between this and the status bar since the strip moved
            // to the bottom, so it keeps clear of it itself.
            // Only when it actually differs: setting the same padding again asks
            // for another layout pass, and a layout pass in the middle of a touch
            // is what makes a list forget the tap it was in the middle of.
            if (v.getPaddingTop() != insets.top) {
                v.setPadding(0, insets.top, 0, 0);
            }
            ((ViewGroup) v).setClipToPadding(true);
            return WindowInsetsCompat.CONSUMED;
        });

        requireActivity().getOnBackPressedDispatcher()
                .addCallback(getViewLifecycleOwner(), goBack);
        showTopLevel();
    }

    /**
     * What is on show: the menu itself, or one of the lists opened from it.
     * <p>
     * Backup and import used to drop a box on top of the screen, which is the
     * only thing in the app that still did; they are pages now, sliding in from
     * the right over the menu and going back with the phone's own back gesture.
     */
    private Object[] subEntries;

    private void showTopLevel() {
        subEntries = null;
        goBack.setEnabled(false);
        SummaryEntityListAdapter adapter =
                new SummaryEntityListAdapter(getContext(), MenuListItem.values());
        adapter.setOnPicked(position -> MenuListItem.values()[position].call(this));
        setListAdapter(adapter);
        setTitleRow(0);
    }

    /** Asked by an entry of the menu that has a list of its own behind it. */
    public static void openSubMenu(androidx.fragment.app.Fragment from, int titleId, Object[] rows) {
        if (from instanceof MenuListFragment) {
            ((MenuListFragment) from).showSubMenu(titleId, rows);
        }
    }

    private void showSubMenu(int titleId, Object[] rows) {
        subEntries = rows;
        setListAdapter(new io.github.mpstudios56.cifra.adapter.SubMenuAdapter(
                getContext(), rows, this::run));
        setTitleRow(titleId);
        goBack.setEnabled(true);
    }

    /** Does what an entry of a page says to do. */
    @SuppressWarnings("unchecked")
    private void run(Object row) {
        if (row instanceof io.github.mpstudios56.cifra.utils.ExecutableEntityEnum) {
            ((io.github.mpstudios56.cifra.utils.ExecutableEntityEnum<androidx.fragment.app.Fragment>) row)
                    .execute(this);
        }
    }

    /** The name of the list one is standing in, with the way back beside it. */
    private void setTitleRow(int titleId) {
        View header = getView() == null ? null : getView().findViewById(R.id.sub_menu_header);
        if (header == null) {
            return;
        }
        if (titleId == 0) {
            header.setVisibility(View.GONE);
            return;
        }
        header.setVisibility(View.VISIBLE);
        ((android.widget.TextView) header.findViewById(R.id.sub_menu_title)).setText(titleId);
        header.findViewById(R.id.sub_menu_back).setOnClickListener(v -> showTopLevel());
    }

    /**
     * The way back out of a page opened over the menu.
     * <p>
     * Registered with the screen rather than taken over from the activity:
     * taking it over stopped the phone's own back working at all, which left
     * the app with no way out but being closed by hand.
     */
    private final androidx.activity.OnBackPressedCallback goBack =
            new androidx.activity.OnBackPressedCallback(false) {
                @Override
                public void handleOnBackPressed() {
                    showTopLevel();
                }
            };

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        if (subEntries != null) {
            run(subEntries[position]);
        } else {
            MenuListItem.values()[position].call(this);
        }
    }

    @OnActivityResult(MenuListItem.ACTIVITY_RESTORE_DATABASE)
    public void onRestoreDatabase(int resultCode, Intent data) {
        if (resultCode == RESULT_OK && data != null) {
            Uri backupFileUri = data.getData();
            Log.i("Financisto", "ACTIVITY_RESTORE_DATABASE uri: " + backupFileUri.toString());
            ProgressDialog d = ProgressDialog.show(getContext(), null, getString(R.string.restore_database_inprogress), true);
            new BackupImportTask(getActivity(), d).execute(backupFileUri);
        }
    }

    @OnActivityResult(MenuListItem.ACTIVITY_IMPORT_SETTINGS)
    public void onImportSettings(int resultCode, Intent data) {
        if (resultCode == RESULT_OK && data != null) {
            ProgressDialog d = ProgressDialog.show(getContext(), null, getString(R.string.import_settings_inprogress), true);
            new SettingsImportTask(getActivity(), d).execute(data.getData());
        }
    }

    @OnActivityResult(MenuListItem.ACTIVITY_CSV_EXPORT)
    public void onCsvExportResult(int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            CsvExportOptions options = CsvExportOptions.fromIntent(data);
            MenuListItem.doCsvExport(getActivity(), options);
        }
    }

    @OnActivityResult(MenuListItem.ACTIVITY_QIF_EXPORT)
    public void onQifExportResult(int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            QifExportOptions options = QifExportOptions.fromIntent(data);
            MenuListItem.doQifExport(getActivity(), options);
        }
    }

    @OnActivityResult(MenuListItem.ACTIVITY_CSV_IMPORT)
    public void onCsvImportResult(int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            CsvImportOptions options = CsvImportOptions.fromIntent(data);
            MenuListItem.doCsvImport(getActivity(), options);
        }
    }

    @OnActivityResult(MenuListItem.ACTIVITY_QIF_IMPORT)
    public void onQifImportResult(int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            QifImportOptions options = QifImportOptions.fromIntent(data);
            MenuListItem.doQifImport(getActivity(), options);
        }
    }

    @OnActivityResult(MenuListItem.ACTIVITY_CHANGE_PREFERENCES)
    public void onChangePreferences() {
        scheduleNextAutoBackup(getContext());
    }

    @Override
    public void onPause() {
        super.onPause();
        PinProtection.lock(getContext());
        bus.unregister(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        PinProtection.unlock(getContext());
        bus.register(this);
    }

    ProgressDialog progressDialog;

    private void dismissProgressDialog() {
        if (progressDialog != null) {
            progressDialog.dismiss();
            progressDialog = null;
        }
    }

    // google drive

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void doGoogleDriveBackup(StartDriveBackup e) {
        ProgressDialog d = ProgressDialog.show(getContext(), null, getString(R.string.backup_database_gdocs_inprogress), true);
        new GoogleDriveBackupTask(getActivity(), d).execute();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void doGoogleDriveRestore(StartDriveRestore e) {
        ProgressDialog d = ProgressDialog.show(getContext(), null, this.getString(R.string.google_drive_loading_files), true);
        new GoogleDriveListFilesTask(getActivity(), d).execute();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onGoogleDriveFileList(GoogleDriveFileList event) {
        dismissProgressDialog();
        final GoogleDriveFileInfo[] files = event.files;
        final String[] fileNames = getFileNames(files);
        final Context context = getContext();
        final GoogleDriveFileInfo[] selectedDriveFile = new GoogleDriveFileInfo[1];
        new AlertDialog.Builder(context)
                .setTitle(R.string.restore_database_online_google_drive)
                .setPositiveButton(R.string.restore, (dialog, which) -> {
                    if (selectedDriveFile[0] != null) {
                        ProgressDialog d = ProgressDialog.show(context, null, getString(R.string.google_drive_restore_in_progress), true);
                        new GoogleDriveRestoreTask(getActivity(), d, selectedDriveFile[0]).execute();
                    }
                })
                .setSingleChoiceItems(fileNames, -1, (dialog, which) -> {
                    if (which >= 0 && which < fileNames.length) {
                        selectedDriveFile[0] = files[which];
                    }
                })
                .show();
    }

    private String[] getFileNames(GoogleDriveFileInfo[] files) {
        String[] names = new String[files.length];
        for (int i = 0; i < files.length; i++) {
            names[i] = files[i].name;
        }
        return names;
    }

    @OnActivityResult(RESOLVE_CONNECTION_REQUEST_CODE)
    public void onConnectionRequest(int resultCode) {
        if (resultCode == RESULT_OK) {
            Toast.makeText(getContext(), R.string.google_drive_connection_resolved, Toast.LENGTH_LONG).show();
        }
    }

    // dropbox
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void doImportFromDropbox(DropboxFileList event) {
        final String[] backupFiles = event.files;
        if (backupFiles != null) {
            final String[] selectedDropboxFile = new String[1];
            new AlertDialog.Builder(getContext())
                    .setTitle(R.string.restore_database_online_dropbox)
                    .setPositiveButton(R.string.restore, (dialog, which) -> {
                        if (selectedDropboxFile[0] != null) {
                            ProgressDialog d = ProgressDialog.show(getContext(), null, getString(R.string.restore_database_inprogress_dropbox), true);
                            new DropboxRestoreTask(getActivity(), d, selectedDropboxFile[0]).execute();
                        }
                    })
                    .setSingleChoiceItems(backupFiles, -1, (dialog, which) -> {
                        if (which >= 0 && which < backupFiles.length) {
                            selectedDropboxFile[0] = backupFiles[which];
                        }
                    })
                    .show();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void doDropboxBackup(StartDropboxBackup e) {
        ProgressDialog d = ProgressDialog.show(getContext(), null, this.getString(R.string.backup_database_dropbox_inprogress), true);
        new DropboxBackupTask(getActivity(), d).execute();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void doDropboxRestore(StartDropboxRestore e) {
        ProgressDialog d = ProgressDialog.show(getContext(), null, this.getString(R.string.dropbox_loading_files), true);
        new DropboxListFilesTask(getActivity(), d).execute();
    }

    public static class StartDropboxBackup {
    }

    public static class StartDropboxRestore {
    }

    public static class StartDriveBackup {
    }

    public static class StartDriveRestore {
    }
}
