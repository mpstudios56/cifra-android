package tw.tib.financisto.activity;

import static android.Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.DocumentsContract;
import android.util.Log;
import android.widget.ListAdapter;
import android.widget.Toast;

import tw.tib.financisto.R;

import tw.tib.financisto.bus.GreenRobotBus_;
import tw.tib.financisto.export.csv.CsvImportOptions;
import tw.tib.financisto.export.qif.QifExportOptions;
import tw.tib.financisto.export.qif.QifExportTask;
import tw.tib.financisto.export.qif.QifImportOptions;
import tw.tib.financisto.export.qif.QifImportTask;
import tw.tib.financisto.db.DatabaseAdapter;
import tw.tib.financisto.export.BackupExportTask;
import tw.tib.financisto.export.Export;
import tw.tib.financisto.export.csv.CsvExportOptions;
import tw.tib.financisto.export.csv.CsvExportTask;
import tw.tib.financisto.export.csv.CsvImportTask;
import tw.tib.financisto.export.SettingsExportTask;
import tw.tib.financisto.utils.DonatePrompt;
import tw.tib.financisto.utils.EntityEnum;
import tw.tib.financisto.utils.Feedback;
import tw.tib.financisto.utils.EnumUtils;

import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;

import tw.tib.financisto.utils.ExecutableEntityEnum;
import tw.tib.financisto.utils.IntegrityFix;
import tw.tib.financisto.utils.MyPreferences;
import tw.tib.financisto.utils.SummaryEntityEnum;

public enum MenuListItem implements SummaryEntityEnum {

    MENU_PREFERENCES(R.string.preferences, R.string.preferences_summary, R.drawable.drawer_action_preferences) {
        @Override
        public void call(Fragment fragment) {
            fragment.startActivityForResult(new Intent(fragment.getContext(), PreferencesActivity2.class), ACTIVITY_CHANGE_PREFERENCES);
        }
    },
    MENU_CATEGORIES(R.string.categories, R.string.menu_categories_summary, R.drawable.ic_action_category) {
        @Override
        public void call(Fragment fragment) {
            fragment.startActivity(new Intent(fragment.getContext(), CategoryListActivity2.class));
        }
    },
    MENU_CURRENCIES(R.string.menu_currencies, R.string.menu_currencies_summary, R.drawable.ic_action_money) {
        @Override
        public void call(Fragment fragment) {
            EnumUtils.showPickOneDialog(fragment.getContext(), R.string.menu_currencies,
                    CurrencyEntities.values(), fragment);
        }
    },
    MENU_ENTITIES(R.string.menu_records, R.string.menu_records_summary, R.drawable.drawer_action_entities) {
        @Override
        public void call(Fragment fragment) {
            final MenuEntities[] entities = MenuEntities.values();
            ListAdapter adapter = EnumUtils.createEntityEnumAdapter(fragment.getContext(), entities);
            final AlertDialog d = new AlertDialog.Builder(fragment.getContext())
                    .setAdapter(adapter, (dialog, which) -> {
                        dialog.dismiss();
                        MenuEntities e = entities[which];
                        if (e.getPermissions() == null
                                || !RequestPermission.isRequestingPermissions(fragment.getContext(), e.getPermissions())) {
                            fragment.startActivity(new Intent(fragment.getContext(), e.getActivityClass()));
                        }
                    })
                    .create();
            d.setTitle(R.string.menu_records);
            d.show();
        }
    },
    MENU_BACKUP_RESTORE(R.string.menu_backup_security, R.string.menu_backup_security_summary, R.drawable.actionbar_db_backup) {
        @Override
        public void call(Fragment fragment) {
            EnumUtils.showPickOneDialog(fragment.getContext(), R.string.menu_backup_security,
                    BackupRestoreEntities.values(), fragment);
        }
    },
    MENU_IMPORT_EXPORT(R.string.import_export, R.string.import_export_summary, R.drawable.actionbar_export) {
        @Override
        public void call(Fragment fragment) {
            EnumUtils.showPickOneDialog(fragment.getContext(), R.string.import_export, ImportExportEntities.values(), fragment);
        }
    },
    MENU_SHARING(R.string.sharing, R.string.sharing_summary, R.drawable.category_family) {
        @Override
        public void call(Fragment fragment) {
            fragment.startActivity(new Intent(fragment.getContext(), SharingActivity.class));
        }
    },
    MENU_TRASH(R.string.trash, R.string.trash_summary, R.drawable.ic_action_trash) {
        @Override
        public void call(Fragment fragment) {
            fragment.startActivity(new Intent(fragment.getContext(), TrashActivity.class));
        }
    },
    MENU_MASS_OP(R.string.mass_operations, R.string.mass_operations_summary, R.drawable.ic_menu_agenda) {
        @Override
        public void call(Fragment fragment) {
            fragment.startActivity(new Intent(fragment.getContext(), MassOpActivity.class));
        }
    },
    MENU_SCHEDULED_TRANSACTIONS(R.string.scheduled_transactions, R.string.scheduled_transactions_summary, R.drawable.actionbar_calendar) {
        @Override
        public void call(Fragment fragment) {
            fragment.startActivity(new Intent(fragment.getContext(), ScheduledListActivity.class));
        }
    },
    MENU_PLANNER(R.string.planner, R.string.planner_summary, R.drawable.actionbar_calendar) {
        @Override
        public void call(Fragment fragment) {
            fragment.startActivity(new Intent(fragment.getContext(), PlannerActivity.class));
        }
    },
    MENU_PERMISSIONS(R.string.permissions, R.string.permissions_summary, R.drawable.ic_tab_about) {
        @Override
        public void call(Fragment fragment) {
            RequestPermissionActivity_.intent(fragment.getContext()).start();
        }
    },
    MENU_INTEGRITY_FIX(R.string.integrity_fix, R.string.integrity_fix_summary, R.drawable.actionbar_flash) {
        @Override
        public void call(Fragment fragment) {
            new IntegrityFixTask(fragment.getContext()).execute();
        }
    },
    MENU_DONATE(R.string.donate, R.string.donate_summary, R.drawable.actionbar_donate) {
        @Override
        public void call(Fragment fragment) {
            // Straight to the payment page. The card that comes up on its own
            // is there to ask; somebody who has come looking for this row has
            // already been asked, and being asked a second time by the thing
            // one has just chosen reads as a wall rather than as an invitation.
            DonatePrompt.open(fragment.getContext());
        }
    },
    MENU_ABOUT(R.string.about, R.string.about_summary, R.drawable.ic_action_info) {
        @Override
        public void call(Fragment fragment) {
            fragment.startActivity(new Intent(fragment.getContext(), AboutActivity.class));
        }
    },
    MENU_FEEDBACK(R.string.feedback, R.string.feedback_summary, R.drawable.ic_action_feedback) {
        @Override
        public void call(Fragment fragment) {
            Feedback.send(fragment.getActivity());
        }
    };

    private static final String TAG = "MenuListItem";

    public final int titleId;
    public final int summaryId;
    public final int iconId;

    MenuListItem(int titleId, int summaryId, int iconId) {
        this.titleId = titleId;
        this.summaryId = summaryId;
        this.iconId = iconId;
    }

    @Override
    public int getTitleId() {
        return titleId;
    }

    @Override
    public int getSummaryId() {
        return summaryId;
    }

    @Override
    public int getIconId() {
        return iconId;
    }

    public static final int ACTIVITY_CSV_EXPORT = 2;
    public static final int ACTIVITY_QIF_EXPORT = 3;
    public static final int ACTIVITY_CSV_IMPORT = 4;
    public static final int ACTIVITY_QIF_IMPORT = 5;
    public static final int ACTIVITY_CHANGE_PREFERENCES = 6;
    public static final int ACTIVITY_RESTORE_DATABASE = 7;
    public static final int ACTIVITY_IMPORT_SETTINGS = 8;

    public abstract void call(Fragment fragment);

    /**
     * Backs up straight away. The accounts screen offers this from its own popup
     * without going through the menu, so it cannot live inside a menu row.
     */
    public static void backupNow(Fragment fragment) {
        if (!checkBackupFolderConfigured(fragment.getContext())) return;
        ProgressDialog d = ProgressDialog.show(fragment.getContext(), null,
                fragment.getContext().getString(R.string.backup_database_inprogress), true);
        new BackupExportTask(fragment.getContext(), d, true).execute();
    }

    private enum MenuEntities implements EntityEnum {

        SMS_TEMPLATES(R.string.sms_templates, R.drawable.ic_action_sms, SmsDragListActivity.class, BIND_NOTIFICATION_LISTENER_SERVICE),
        PAYEES(R.string.payees, R.drawable.ic_action_users, PayeeListActivity.class),
        PROJECTS(R.string.projects, R.drawable.ic_action_gear, ProjectListActivity.class),
        LOCATIONS(R.string.locations, R.drawable.ic_action_location_2, LocationsListActivity.class);

        private final int titleId;
        private final int iconId;
        private final Class<?> actitivyClass;
        private final String[] permissions;

        MenuEntities(int titleId, int iconId, Class<?> activityClass) {
            this(titleId, iconId, activityClass, (String[]) null);
        }

        MenuEntities(int titleId, int iconId, Class<?> activityClass, String... permissions) {
            this.titleId = titleId;
            this.iconId = iconId;
            this.actitivyClass = activityClass;
            this.permissions = permissions;
        }

        @Override
        public int getTitleId() {
            return titleId;
        }

        @Override
        public int getIconId() {
            return iconId;
        }

        public Class<?> getActivityClass() {
            return actitivyClass;
        }

        public String[] getPermissions() {
            return permissions;
        }
    }

    /** Currencies and the rates between them: one subject, two screens. */
    private enum CurrencyEntities implements ExecutableEntityEnum<Fragment> {

        CURRENCIES(R.string.currencies, R.drawable.ic_action_money) {
            @Override
            public void execute(Fragment fragment) {
                fragment.startActivity(new Intent(fragment.getContext(), CurrencyListActivity.class));
            }
        },
        EXCHANGE_RATES(R.string.exchange_rates, R.drawable.ic_action_line_chart) {
            @Override
            public void execute(Fragment fragment) {
                fragment.startActivity(new Intent(fragment.getContext(), ExchangeRatesListActivity.class));
            }
        };

        private final int titleId;
        private final int iconId;

        CurrencyEntities(int titleId, int iconId) {
            this.titleId = titleId;
            this.iconId = iconId;
        }

        @Override
        public int getTitleId() {
            return titleId;
        }

        @Override
        public int getIconId() {
            return iconId;
        }
    }

    /** Every way of getting the data out and back in, in one place. */
    private enum BackupRestoreEntities implements ExecutableEntityEnum<Fragment> {

        MENU_SECURITY(R.string.protection, R.drawable.ic_action_lock) {
            @Override
            public void execute(Fragment fragment) {
                Intent intent = new Intent(fragment.getContext(), PreferencesActivity2.class);
                intent.putExtra(PreferencesActivity2.SCREEN_EXTRA,
                        "tw.tib.financisto.preference.SecurityPreferencesFragment");
                fragment.startActivity(intent);
            }
        },
        MENU_BACKUP(R.string.backup_database, R.drawable.actionbar_db_backup) {
            @Override
            public void execute(Fragment fragment) {
                backupNow(fragment);
            }
    },
        MENU_RESTORE(R.string.restore_database, R.drawable.actionbar_db_restore) {
            @Override
            public void execute(Fragment fragment) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, Export.getBackupFolder(fragment.getContext()));
                fragment.startActivityForResult(intent, ACTIVITY_RESTORE_DATABASE);
            }
    },
        GOOGLE_DRIVE_BACKUP(R.string.backup_database_online_google_drive, R.drawable.actionbar_google_drive) {
            @Override
            public void execute(Fragment fragment) {
                if (!checkBackupFolderConfigured(fragment.getContext())) return;
                GreenRobotBus_.getInstance_(fragment.getContext()).post(new MenuListFragment.StartDriveBackup());
            }
    },
        GOOGLE_DRIVE_RESTORE(R.string.restore_database_online_google_drive, R.drawable.actionbar_google_drive) {
            @Override
            public void execute(Fragment fragment) {
                GreenRobotBus_.getInstance_(fragment.getContext()).post(new MenuListFragment.StartDriveRestore());
            }
    },
        DROPBOX_BACKUP(R.string.backup_database_online_dropbox, R.drawable.actionbar_dropbox) {
            @Override
            public void execute(Fragment fragment) {
                if (!checkBackupFolderConfigured(fragment.getContext())) return;
                GreenRobotBus_.getInstance_(fragment.getContext()).post(new MenuListFragment.StartDropboxBackup());
            }
    },
        DROPBOX_RESTORE(R.string.restore_database_online_dropbox, R.drawable.actionbar_dropbox) {
            @Override
            public void execute(Fragment fragment) {
                GreenRobotBus_.getInstance_(fragment.getContext()).post(new MenuListFragment.StartDropboxRestore());
            }
    },
        MENU_BACKUP_TO(R.string.backup_database_to, R.drawable.actionbar_share) {
            @Override
            public void execute(Fragment fragment) {
                if (!checkBackupFolderConfigured(fragment.getContext())) return;
                ProgressDialog d = ProgressDialog.show(fragment.getContext(), null, fragment.getString(R.string.backup_database_inprogress), true);
                final BackupExportTask t = new BackupExportTask(fragment.getContext(), d, false);
                t.setShowResultMessage(false);
                t.setListener(result -> {
                    Uri backupFileUri = t.backupFileUri;
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.putExtra(Intent.EXTRA_STREAM, backupFileUri);
                    intent.setType(Export.BACKUP_MIME_TYPE);
                    fragment.startActivity(Intent.createChooser(intent, fragment.getString(R.string.backup_database_to_title)));
                });
                t.execute((Uri[]) null);
            }
    },
        MENU_EXPORT_SETTINGS(R.string.export_settings, R.drawable.actionbar_settings_export) {
            @Override
            public void execute(Fragment fragment) {
                if (!checkBackupFolderConfigured(fragment.getContext())) return;
                ProgressDialog d = ProgressDialog.show(fragment.getContext(), null, fragment.getString(R.string.export_settings_inprogress), true);
                new SettingsExportTask(fragment.getContext(), d).execute();
            }
    },
        MENU_IMPORT_SETTINGS(R.string.import_settings, R.drawable.actionbar_settings_import) {
            @Override
            public void execute(Fragment fragment) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, Export.getBackupFolder(fragment.getContext()));
                fragment.startActivityForResult(intent, ACTIVITY_IMPORT_SETTINGS);
            }
    };

        private final int titleId;
        private final int iconId;

        BackupRestoreEntities(int titleId, int iconId) {
            this.titleId = titleId;
            this.iconId = iconId;
        }

        @Override
        public int getTitleId() {
            return titleId;
        }

        @Override
        public int getIconId() {
            return iconId;
        }
    }

    private enum ImportExportEntities implements ExecutableEntityEnum<Fragment> {

        /**
         * First, and on purpose: it is the one somebody needs on their first day,
         * when they still have their whole history in another app. The CSV import
         * below it reads Cifra's own files and is of no use to them.
         */
        CSV_IMPORT_OTHER_APP(R.string.csv_map_title, R.drawable.backup_csv) {
            @Override
            public void execute(Fragment fragment) {
                fragment.startActivity(new Intent(fragment.getContext(), CsvMappingActivity.class));
            }
        },
        CSV_EXPORT(R.string.csv_export, R.drawable.backup_csv) {
            @Override
            public void execute(Fragment fragment) {
                if (!checkBackupFolderConfigured(fragment.getContext())) return;

                Intent intent = new Intent(fragment.getContext(), CsvExportActivity.class);
                fragment.startActivityForResult(intent, ACTIVITY_CSV_EXPORT);
            }
        },
        CSV_IMPORT(R.string.csv_import, R.drawable.backup_csv) {
            @Override
            public void execute(Fragment fragment) {
                Intent intent = new Intent(fragment.getContext(), CsvImportActivity.class);
                fragment.startActivityForResult(intent, ACTIVITY_CSV_IMPORT);
            }
        },
        QIF_EXPORT(R.string.qif_export, R.drawable.backup_qif) {
            @Override
            public void execute(Fragment fragment) {
                if (!checkBackupFolderConfigured(fragment.getContext())) return;

                Intent intent = new Intent(fragment.getContext(), QifExportActivity.class);
                fragment.startActivityForResult(intent, ACTIVITY_QIF_EXPORT);
            }
        },
        QIF_IMPORT(R.string.qif_import, R.drawable.backup_qif) {
            @Override
            public void execute(Fragment fragment) {
                Intent intent = new Intent(fragment.getContext(), QifImportActivity.class);
                fragment.startActivityForResult(intent, ACTIVITY_QIF_IMPORT);
            }
        };

        private final int titleId;
        private final int iconId;

        ImportExportEntities(int titleId, int iconId) {
            this.titleId = titleId;
            this.iconId = iconId;
        }

        @Override
        public int getTitleId() {
            return titleId;
        }

        @Override
        public int getIconId() {
            return iconId;
        }

    }

    public static void doCsvExport(Activity activity, CsvExportOptions options) {
        ProgressDialog progressDialog = ProgressDialog.show(activity, null, activity.getString(R.string.csv_export_inprogress), true);
        new CsvExportTask(activity, progressDialog, options).execute();
    }

    public static void doCsvImport(Activity activity, CsvImportOptions options) {
        ProgressDialog progressDialog = ProgressDialog.show(activity, null, activity.getString(R.string.csv_import_inprogress), true);
        new CsvImportTask(activity, progressDialog, options).execute();
    }

    public static void doQifExport(Activity activity, QifExportOptions options) {
        ProgressDialog progressDialog = ProgressDialog.show(activity, null, activity.getString(R.string.qif_export_inprogress), true);
        new QifExportTask(activity, progressDialog, options).execute();
    }

    public static void doQifImport(Activity activity, QifImportOptions options) {
        ProgressDialog progressDialog = ProgressDialog.show(activity, null, activity.getString(R.string.qif_import_inprogress), true);
        new QifImportTask(activity, progressDialog, options).execute();
    }

    private static boolean checkBackupFolderConfigured(Context context) {
        try {
            Uri backupFolderUri = Uri.parse(MyPreferences.getDatabaseBackupFolder());
            Log.i(TAG, "backupFolderUri: " + backupFolderUri);
            String backupFolderId = DocumentsContract.getTreeDocumentId(backupFolderUri);
            Log.i(TAG, "backupFolderId: " + backupFolderId);
            Uri dirUri = DocumentsContract.buildDocumentUriUsingTree(backupFolderUri, backupFolderId);
            Log.i(TAG, "dirUri: " + dirUri);
            DocumentFile file = DocumentFile.fromTreeUri(context, dirUri);
            if (file.canWrite()) {
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "check backup folder writable fail", e);
        }

        new AlertDialog.Builder(context)
                .setTitle(R.string.fail)
                .setMessage(context.getString(R.string.backup_folder_not_configured))
                .setPositiveButton(R.string.ok, null)
                .show();
        return false;
    }

    private static class IntegrityFixTask extends AsyncTask<Void, Void, Void> {

        private final Context context;
        private ProgressDialog progressDialog;

        IntegrityFixTask(Context context) {
            this.context = context;
        }

        @Override
        protected void onPreExecute() {
            progressDialog = ProgressDialog.show(context, null, context.getString(R.string.integrity_fix_in_progress), true);
            progressDialog.show();
        }

        @Override
        protected void onPostExecute(Void o) {
            if (context instanceof MainActivity) {
                ((MainActivity) context).refreshCurrentTab();
            }
            progressDialog.dismiss();
        }

        @Override
        protected Void doInBackground(Void... objects) {
            DatabaseAdapter db = new DatabaseAdapter(context);
            new IntegrityFix(db).fix();
            return null;
        }
    }

}