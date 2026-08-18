/*******************************************************************************
 * Copyright (c) 2010 Denis Solonenko.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * <p/>
 * Contributors:
 * Denis Solonenko - initial API and implementation
 * Rodrigo Sousa - google docs backup
 * Abdsandryk Souza - report preferences
 ******************************************************************************/
package tw.tib.financisto.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.Log;

import androidx.annotation.StyleRes;
import androidx.preference.PreferenceManager;

import java.lang.reflect.Method;
import java.util.Calendar;
import java.util.Collection;
import java.util.Locale;

import tw.tib.financisto.Application;
import tw.tib.financisto.R;
import tw.tib.financisto.export.ImportExportException;
import tw.tib.financisto.export.Export;
import tw.tib.financisto.model.Currency;
import tw.tib.financisto.model.TransactionStatus;
import tw.tib.financisto.rates.ExchangeRateProvider;
import tw.tib.financisto.rates.ExchangeRateProviderFactory;

public class MyPreferences {
	private static final String TAG = "MyPreferences";

	private static final String DROPBOX_AUTH_TOKEN = "dropbox_auth_token";
	private static final String DROPBOX_AUTHORIZE = "dropbox_authorize";

	public enum AccountSortOrder {
		SORT_ORDER_ASC("sortOrder", true),
		SORT_ORDER_DESC("sortOrder", false),
		NAME("title", true),
		LAST_TRANSACTION_ASC("lastTransactionDate", true),
		LAST_TRANSACTION_DESC("lastTransactionDate", false);

		public final String property;
		public final boolean asc;

		AccountSortOrder(String property, boolean asc) {
			this.property = property;
			this.asc = asc;
		}
	}

	public enum LocationsSortOrder {
		FREQUENCY("count", false),
		TITLE("title", true);

		public final String property;
		public final boolean asc;

		LocationsSortOrder(String property, boolean asc) {
			this.property = property;
			this.asc = asc;
		}
	}

	public enum TemplatesSortOrder {
		DATE("datetime", false),
		NAME("template_name", true),
		ACCOUNT("from_account", true),
		// Added when the choice moved out of the settings and onto the screen:
		// "by name" meant the name of the template, which nobody had written,
		// so it read as a list in no order at all.
		CATEGORY("category_title", true),
		PROJECT("project", true);

		public final String property;
		public final boolean asc;

		TemplatesSortOrder(String property, boolean asc) {
			this.property = property;
			this.asc = asc;
		}
	}

	public enum StartupScreen {
		SUMMARY("summary"),
		ACCOUNTS("accounts"),
		BLOTTER("blotter"),
		BUDGETS("budgets"),
		REPORTS("reports");

		public final String tag;

		StartupScreen(String tag) {
			this.tag = tag;
		}
	}

	public enum AccountListDateType {
		LAST_TX("LAST_TX"),
		ACCOUNT_CREATION("ACCOUNT_CREATION"),
		ACCOUNT_UPDATE("ACCOUNT_UPDATE"),
		HIDDEN("HgeIDDEN");

		public final String tag;

		AccountListDateType(String tag) { this.tag = tag; }
	}

	public enum Theme {
		DARK("DARK"),
		LIGHT("LIGHT");

		public final String tag;

		Theme(String tag) { this.tag = tag; }
	}

	public enum FirstDayOfWeek {
		SYSTEM_DEFAULT("SYSTEM_DEFAULT"),
		SUNDAY("SUNDAY"),
		MONDAY("MONDAY");

		public final String tag;

		FirstDayOfWeek(String tag) { this.tag = tag; }
	}

	public enum EntitySelectorType {
		DROPDOWN("DROPDOWN"),
		SEARCH("SEARCH");

		public final String tag;

		EntitySelectorType(String tag) { this.tag = tag; }
	}

	public enum ReportAggregateUnit {
		WEEK("WEEK"),
		MONTH("MONTH"),
		YEAR("YEAR"),
		FISCAL_YEAR("FISCAL_YEAR");

		public final String tag;

		ReportAggregateUnit(String tag) { this.tag = tag; }
	}

	private static Method hasSystemFeatureMethod;

	static {
		// hack for 1.5/1.6 devices
		try {
			hasSystemFeatureMethod = PackageManager.class.getMethod("hasSystemFeature", String.class);
		} catch (NoSuchMethodException ex) {
			hasSystemFeatureMethod = null;
		}

	}

	/**
	 * How many days the bin keeps what was deleted. Thirty by default: long
	 * enough to notice a mistake at the end of a month, short enough that the
	 * bin does not quietly become a second copy of everything.
	 */
	public static int getTrashDays() {
		try {
			return Integer.parseInt(getString("trash_days", "30"));
		} catch (NumberFormatException e) {
			return 30;
		}
	}

	public static boolean isSecureWindow() {
		return getBoolean("secure_window", false);
	}

	public static boolean isPinProtected() {
		return getBoolean("pin_protection", false);
	}

	public static boolean isPinProtectedNewTransaction() {
		return getBoolean("pin_protection_lock_transaction", true);
	}

	public static boolean isPinLockEnabled() {
		return isPinProtected() && getBoolean("pin_protection_lock", true);
	}

	public static boolean isPinLockUseFingerprint() {
		return isPinProtected() && getBoolean("pin_protection_use_fingerprint", false);
	}

	public static boolean isUseFingerprintFallbackToPinEnabled() {
		return isPinProtected() && getBoolean("pin_protection_use_fingerprint_fallback_to_pin", true);
	}

	public static int getLockTimeSeconds() {
		return isPinLockEnabled() ? 60 * Integer.parseInt(getString("pin_protection_lock_time", "5")) : 0;
	}

	public static String getPin() {
		return getString("pin", null);
	}

	public static AccountSortOrder getAccountSortOrder() {
		String sortOrder = getString("sort_accounts", AccountSortOrder.SORT_ORDER_ASC.name());
		return AccountSortOrder.valueOf(sortOrder);
	}

	/** Whether the figures are currently switched off. See {@link Privacy}. */
	public static boolean isAmountsHidden() {
		return getBoolean("amounts_hidden", false);
	}

	public static void setAmountsHidden(boolean value) {
		edit().putBoolean("amounts_hidden", value).apply();
	}

	public static boolean isBlurBalances() {
		return false /* L'occhio nasconde gli importi meglio di una sfocatura. */;
	}

	public static LocationsSortOrder getLocationsSortOrder() {
		String sortOrder = getString("sort_locations", LocationsSortOrder.TITLE.name());
		try {
			return LocationsSortOrder.valueOf(sortOrder);
		} catch (IllegalArgumentException e) {
			return LocationsSortOrder.TITLE;
		}
	}

	public static TemplatesSortOrder getTemplatessSortOrder() {
		String sortOrder = getString("sort_templates", TemplatesSortOrder.DATE.name());
		return TemplatesSortOrder.valueOf(sortOrder);
	}

	public static long getLastAccount() {
		return getLong("last_account_id", -1);
	}

	public static void setLastAccount(long accountId) {
		edit().putLong("last_account_id", accountId).apply();
	}

	public static boolean isRememberAccount() {
		return getBoolean("remember_last_account", true);
	}

	public static boolean isRememberCategory() {
		return getBoolean("remember_last_category", false);
	}

	public static boolean isRememberLocation() {
		return getBoolean("remember_last_location", false);
	}

	public static boolean isRememberProject() {
		return getBoolean("remember_last_project", false);
	}

	private static EntitySelectorType getEntitySelectorType(String key) {
		// Dropdown by default: search opens an empty box in place of the current
		// value, which reads as the list being empty and the selection lost.
		String selectorType = getString(key, EntitySelectorType.DROPDOWN.name());
		try {
			return EntitySelectorType.valueOf(selectorType);
		} catch (IllegalArgumentException e) {
			return EntitySelectorType.DROPDOWN;
		}
	}

	public static boolean isShowAccountBalanceOnSelector() {
		return getBoolean("ntsl_show_account_balance_on_selector", false);
	}

	public static EntitySelectorType getPayeeSelectorType() {
		return getEntitySelectorType("payee_selector_type");
	}

	public static EntitySelectorType getProjectSelectorType() {
		return getEntitySelectorType("project_selector_type");
	}

	public static EntitySelectorType getLocationSelectorType() {
		return getEntitySelectorType("location_selector_type");
	}

	public static boolean isShowTakePicture() {
		return true /* La foto dello scontrino si puo' sempre allegare. */;
	}

	public static boolean isShowCategoryInTransferScreen() {
		return getBoolean("ntsl_show_category_in_transfer", true);
	}

	public static boolean isShowPayee() {
		return true /* Il beneficiario si vede. */;
	}

	public static boolean isShowPayeeInTransfers() {
		return getBoolean("ntsl_show_payee_in_transfers", false);
	}

	public static boolean isShowCurrency() {
		return true /* La valuta si vede: su un conto in valuta e' l'unica cosa che dice quale. */;
	}

	public static boolean isEnterCurrencyDecimalPlaces() {
		return getBoolean("ntsl_enter_currency_decimal_places", true);
	}

	public static boolean isRoundUpAmount() {
		return getBoolean("ntsl_round_up_amount", true);
	}

	public static boolean isShowLocation() {
		return isLocationSupported() && true /* La localita' si vede. */;
	}

	public static int getLocationOrder() {
		return Integer.parseInt(getString("ntsl_show_location_order", "1"));
	}

	public static boolean isShowIsCCardPayment() {
		return true /* "Non includere nel totale" resta a disposizione: e' l'unico modo di segnare un girofondo. */;
	}

	public static boolean isOpenCalculatorForTemplates() {
		return getBoolean("ntsl_open_calculator_for_template_transactions", true);
	}

	public static boolean isSetFocusOnAmountField() {
		return true /* Si parte dall'importo, che e' il primo dato che si ha in mano. */;
	}

	/**
	 * Get Google Drive backup folder registered on preferences
	 */
	public static String getGoogleDriveBackupFolder() {
		return getString("google_drive_backup_folder", null);
	}

	public static ReportAggregateUnit getReportAggregateUnit() {
		try {
			return ReportAggregateUnit.valueOf(getString("report_aggregate_unit", ReportAggregateUnit.MONTH.name()));
		}
		catch (Exception e) {
			return ReportAggregateUnit.MONTH;
		}
	}

	/**
	 * Gets the string representing reference currency registered on preferences to display chart reports.
	 *
	 * @return The string representing the currency registered as a reference to display chart reports or null if not configured yet.
	 */
	public static String getReferenceCurrencyTitle() {
		return getString("report_reference_currency", "");
	}

	/**
	 * Gets the reference currency registered on preferences to display chart reports.
	 *
	 * @return The currency registered as a reference to display chart reports or null if not configured yet.
	 */
	public static Currency getReferenceCurrency() {
		Collection<Currency> currencies = CurrencyCache.getAllCurrencies();
		Currency cur = null;
		try {
			String refCurrency = getString("report_reference_currency", null);
			if (currencies != null && !currencies.isEmpty()) {
				for (Currency currency : currencies) {
					if (currency.title.equals(refCurrency)) cur = currency;
				}
			}
		} catch (Exception e) {
			return null;
		}
		return cur;
	}

	/**
	 * Gets the period of reference (number of Months to display the 2D report) registered on preferences.
	 *
	 * @return The number of months registered as a period of reference to display chart reports or 0 if not configured yet.
	 */
	public static int getPeriodOfReference() {
		return Integer.parseInt(getString("report_reference_period", "0"));
	}

	/**
	 * Gets the reference month.
	 *
	 * @return The reference month that represents the end of the report period.
	 */
	public static int getReferenceMonth() {
		return Integer.parseInt(getString("report_reference_month", "0"));
	}

	/**
	 * Gets the flag that indicates if the sub categories will be available individually in 2D report or not.
	 *
	 * @return True if the sub categories shall be displayed in the Report 2D list of categories, false otherwise.
	 */
	public static boolean includeSubCategoriesInReport() {
		return getBoolean("report_include_sub_categories", true);
	}

	/**
	 * Gets the flag that indicates if the list of filter ids will include No Filter (no category, no project or current location) or not.
	 *
	 * @return True if no category, no project and current location shall be displayed in 2D Reports, false otherwise.
	 */
	public static boolean includeNoFilterInReport() {
		return getBoolean("report_include_no_filter", true);
	}

	/**
	 * Get the flag that indicates if the category monthly result will consider the result of its sub categories or not.
	 *
	 * @return True if the category result shall include the result of its categories, false otherwise.
	 */
	public static boolean addSubCategoriesToSum() {
		return getBoolean("report_add_sub_categories_result", false);
	}

	/**
	 * Gets the flag that indicates if the statistics calculation will consider null values or not.
	 *
	 * @return True if the null values shall impact the statistics, false otherwise.
	 */
	public static boolean considerNullResultsInReport() {
		return getBoolean("report_consider_null_results", true);
	}

	public static boolean isShowNote() {
		return true /* La nota si vede. */;
	}

	public static int getNoteOrder() {
		return Integer.parseInt(getString("ntsl_show_note_order", "3"));
	}

	public static boolean isShowProject() {
		return true /* Il progetto si vede. */;
	}

	public static int getProjectOrder() {
		return Integer.parseInt(getString("ntsl_show_project_order", "4"));
	}

	public static boolean isUseTwinDatePicker() {
		return getBoolean("ntsl_use_twin_date_picker", false);

	}

	public static boolean isUseFixedLayout() {
		return getBoolean("ntsl_use_fixed_layout", true);
	}

	public static boolean isWidgetEnabled() {
		// A widget somebody has just put on their home screen has been enabled
		// by the act of putting it there. Asking them to go and find a switch
		// in the settings, while the widget sits there looking broken, was a
		// question with only one sensible answer.
		return true;
	}

	public static boolean isTreatTransferToCCardAsPayment() {
		return getBoolean("treat_transfer_to_ccard_as_payment", true);
	}

	public static boolean isRestoreMissedScheduledTransactions() {
		return getBoolean("restore_missed_scheduled_transactions", true);
	}

	public static boolean isShowRunningBalance() {
		return true /* Il saldo progressivo si vede: nasconderlo era una scelta di quando non c'era l'occhio. */;
	}

	public static boolean isColorizeBlotterItem() {
		return true /* I campi dei movimenti sono colorati. */;
	}

	public static boolean isShowProjectInBlotter() {
		return true /* Il progetto si vede. */;
	}

	public static boolean isResetCopiedTransactionStatus() {
		return getBoolean("reset_copied_transaction_status", true);
	}

	public static TransactionStatus getCopiedTransactionStatus() {
		return TransactionStatus.valueOf(getString("reset_copied_transaction_status_to", "UR"));
	}

	public static boolean isResetCopiedForeignTransactionStatus() {
		return getBoolean("reset_copied_foreign_transaction_status", false);
	}

	public static TransactionStatus getCopiedForeignTransactionStatus() {
		return TransactionStatus.valueOf(getString("reset_copied_foreign_transaction_status_to", "PN"));
	}

	public static boolean isUpdateCopiedTransactionProject() {
		return false /* Una copia non si riscrive il progetto da sola. */;
	}

	public static boolean isHighlightCopiedUneditedTransactions() {
		return true /* Una copia non ancora toccata si riconosce a colpo d'occhio. */;
	}

	/**
	 * The colour Saturday and Sunday are written in.
	 * <p>
	 * Kept as the old one for anybody who never opens the setting, and picked
	 * from a short list rather than from a wheel of sixteen million: the point
	 * is to tell a weekend from a weekday, not to match the curtains.
	 */
	public static int getWeekendColour() {
		String chosen = getString("weekend_date_color", "");
		if (chosen.isEmpty()) {
			return 0xFFCC6666;
		}
		try {
			return (int) Long.parseLong(chosen.replace("#", ""), 16) | 0xFF000000;
		} catch (Exception e) {
			return 0xFFCC6666;
		}
	}

	public static boolean isColorizeWeekendDate() {
		return getBoolean("colorize_weekend_date", true);
	}

	public static boolean isBlotterShowTimeOfDay() {
		return true /* L'orario si vede: in una contabilita' l'ora di un pagamento e' un dato. */;
	}

	public static boolean isPreventEditClearedReconciledTransactions() {
		return getBoolean("prevent_edit_cleared_reconciled", false);
	}

	public static boolean isQuickMenuEnabledForSplit() {
		return getBoolean("quick_menu_split_transactions", true);
	}

	public static boolean isTrackSplitEntityInChild() {
		return getBoolean("split_entity_in_child", false);
	}

	private static final String DEFAULT = "default";

	public static Context switchLocale(Context context) {
		return switchLocale(context, getString("ui_language", DEFAULT));
	}

	public static Context switchLocale(Context context, String locale) {
		if (DEFAULT.equals(locale)) {
			return context;
		} else {
			String[] a = locale.split("-");
			String language = a[0];
			String country = a.length > 1 ? a[1] : null;
			Locale newLocale = country != null ? new Locale(language, country) : new Locale(language);
			return switchLocale(context, newLocale);
		}
	}

	private static Context switchLocale(Context context, Locale locale) {
		Locale.setDefault(locale);
		Resources res = context.getResources();
		Configuration config = new Configuration(res.getConfiguration());
		config.setLocale(locale);
		context = context.createConfigurationContext(config);
		Log.i("MyPreferences", "Switching locale to " + config.locale.getDisplayName());
		return context;
	}

	public static int getFirstDayOfWeek() {
		FirstDayOfWeek fw;
		try {
			fw = FirstDayOfWeek.valueOf(getString("first_day_of_week", FirstDayOfWeek.SYSTEM_DEFAULT.name()));
		} catch (IllegalArgumentException e) {
			fw = FirstDayOfWeek.SYSTEM_DEFAULT;
		}
		switch (fw) {
			case SUNDAY -> {
				return Calendar.SUNDAY;
			}
			case MONDAY -> {
				return Calendar.MONDAY;
			}
			default -> {
				return Calendar.getInstance().getFirstDayOfWeek();
			}
		}
	}

	public static int getFiscalYearStart() {
		return getInt("fiscal_year_start", 301);
	}

	/**
	 * How this phone signs the changes it makes. Empty until somebody says who
	 * they are - an unsigned record is still better than a wrong name.
	 */
	public static String getSyncAuthor() {
		return getString("sync_author", "");
	}

	/**
	 * The folder the two phones meet in, as a document tree address, or empty.
	 * <p>
	 * It has to be a folder a cloud app keeps in step - the app writes a file
	 * and reads the other phone's, and it is the cloud that carries it across.
	 * Nothing here talks to Drive or Dropbox: it talks to a folder.
	 */
	public static String getSyncFolder() {
		return getString("sync_folder", "");
	}

	/** When the last round of the exchange happened, or zero. */
	public static long getSyncLastRun() {
		return getLong("sync_last_run", 0);
	}

	public static void setSyncLastRun(long when) {
		edit().putLong("sync_last_run", when).apply();
	}

	public static void setSyncFolder(String uri) {
		edit().putString("sync_folder", uri == null ? "" : uri).apply();
	}

	/**
	 * The code that says which people belong together.
	 * <p>
	 * Everybody in a group writes the same one, and it goes in the name of the
	 * file each phone writes. A phone reads only the files carrying its own
	 * code, which means one cloud folder can hold more than one group without
	 * them mixing - and that a group can be more than two people, because what
	 * makes a group is the code and not the being two.
	 * <p>
	 * Empty means "no code": then the old behaviour stands and every file in
	 * the folder is read, so nobody who set this up before is cut off.
	 */
	public static String getSyncGroupCode() {
		return getString("sync_group_code", "");
	}

	public static void setSyncGroupCode(String code) {
		edit().putString("sync_group_code", code == null ? "" : code.trim()).apply();
	}

	/** What the other person's changes are called when they arrive. */
	public static String getSyncPartner() {
		return getString("sync_partner", "");
	}

	/**
	 * What dragging a movement to the right, and to the left, does.
	 * <p>
	 * Both start out set: a gesture nobody has been told about is found by
	 * accident, and finding it should do something worth having and undoable
	 * rather than nothing at all.
	 */
	public static SwipeAction getSwipeRight() {
		return SwipeAction.of(getString("swipe_right_action", SwipeAction.CLEAR.name()));
	}

	public static SwipeAction getSwipeLeft() {
		return SwipeAction.of(getString("swipe_left_action", SwipeAction.DELETE.name()));
	}

	/**
	 * What was last written into a person's file, and when.
	 * <p>
	 * Kept so a round can tell whether the file it is about to write would be
	 * the same one that is already there, and leave the cloud alone if it is.
	 */
	public static long getSyncLastFingerprint(String mark) {
		SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(Application.getInstance());
		return p.getLong("sync_written_" + mark, 0L);
	}

	public static long getSyncLastWrite(String mark) {
		SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(Application.getInstance());
		return p.getLong("sync_written_on_" + mark, 0L);
	}

	public static void setSyncLastWritten(String mark, long fingerprint, long when) {
		edit().putLong("sync_written_" + mark, fingerprint)
				.putLong("sync_written_on_" + mark, when).apply();
	}

	/** Whether reports count what the other person wrote. Kept between visits. */
	public static boolean isReportIncludeShared() {
		return getBoolean("report_include_shared", true);
	}

	public static void setReportIncludeShared(boolean include) {
		edit().putBoolean("report_include_shared", include).apply();
	}

	/**
	 * This phone, told apart from the other one. Made once and kept: the names
	 * can be changed at any time and both can be the same, so they cannot be
	 * what distinguishes the two devices.
	 */
	public static String getSyncDeviceId() {
		String id = getString("sync_device_id", "");
		if (id.isEmpty()) {
			id = java.util.UUID.randomUUID().toString();
			edit().putString("sync_device_id", id).apply();
		}
		return id;
	}

	/**
	 * Written from the chart screen as well as from the settings, so that
	 * changing what a chart shows and reading what the settings say never give
	 * two different answers.
	 */
	public static void setReferenceCurrency(String currencyTitle) {
		edit().putString("report_reference_currency", currencyTitle).apply();
	}

	public static void setReportAggregateUnit(String unit) {
		edit().putString("report_aggregate_unit", unit).apply();
	}

	public static void setFiscalYearStart(int month, int date) {
		edit().putInt("fiscal_year_start", month * 100 + date).apply();
	}

	public static boolean isLocationSupported() {
		return isFeatureSupported(PackageManager.FEATURE_LOCATION);
	}

	public static boolean isAutoBackupEnabled() {
		return getBoolean("auto_backup_enabled", false);
	}

	public static void setAutoBackupEnabled(boolean enabled) {
		edit().putBoolean("auto_backup_enabled", enabled).apply();
	}

	public static boolean isBackupNewlines() {
		return getBoolean("backup_newlines", false);
	}

	public static int getAutoBackupTime() {
		return getInt("auto_backup_time", 600);
	}

	public static boolean isCollapseBlotterButtons() {
		return true /* I tasti stanno nella bolla del piu', che e' piu' pulita della fila. */;
	}

	public static boolean isShowGoToTodayButton() {
		return false /* Il tasto oggi ora galleggia sopra l'occhio. */;
	}

	private static boolean isFeatureSupported(String feature) {
		if (hasSystemFeatureMethod != null) {
			PackageManager pm = Application.getInstance().getPackageManager();
			try {
				return (Boolean) hasSystemFeatureMethod.invoke(pm, feature);
			} catch (Exception e) {
				Log.w("Financisto", "Some problems executing PackageManager.hasSystemFeature(" + feature + ")", e);
				return false;
			}
		}
		Log.i("Financisto", "It's an old device - no PackageManager.hasSystemFeature");
		return true;
	}

	public static boolean shouldRebuildRunningBalance() {
		return getOneTimeFlag("should_rebuild_running_balance");
	}

	public static boolean shouldUpdateHomeCurrency() {
		return getOneTimeFlag("should_update_home_currency");
	}

	public static boolean shouldUpdateAccountsLastTransactionDate() {
		return getOneTimeFlag("should_update_accounts_last_transaction_date");
	}

	public static boolean shouldUpdateSplitParentAccountId() {
		return getOneTimeFlag("should_update_split_parent_account_id");
	}

	private static boolean getOneTimeFlag(String name) {
		boolean result = getBoolean(name, true);
		if (result) {
			edit().putBoolean(name, false).apply();
		}
		return result;
	}

	public static String getDatabaseBackupFolder() {
		return getString("database_backup_folder", Application.getInstance().getExternalFilesDir(Export.BACKUP_DIRECTORY_NAME).getAbsolutePath());
	}

	public static void setDatabaseBackupFolder(String databaseBackupFolder) {
		edit().putString("database_backup_folder", databaseBackupFolder).apply();
	}

	public static String[] getReportPreferences() {
		String[] preferences = new String[8];
		preferences[0] = getReferenceCurrencyTitle();
		preferences[1] = Integer.toString(getPeriodOfReference());
		preferences[2] = Integer.toString(getReferenceMonth());
		preferences[3] = Boolean.toString(considerNullResultsInReport());
		preferences[4] = Boolean.toString(includeNoFilterInReport());
		preferences[5] = Boolean.toString(includeSubCategoriesInReport());
		preferences[6] = Boolean.toString(addSubCategoriesToSum());
		preferences[7] = getReportAggregateUnit().name();
		return preferences;
	}

	public static boolean isQuickMenuEnabledForAccount() {
		return false /* Un tocco apre i movimenti, la bolla si apre col tocco lungo: non c'e' piu' niente da accendere. */;
	}

	public static boolean isQuickMenuEnabledForTransaction() {
		return getBoolean("quick_menu_transaction_enabled", true);
	}

	public static boolean isQuickMenuShowAdditionalTransactionStatus() {
		return true /* Gli stati ci sono tutti: sceglierne alcuni era una scelta senza motivo. */;
	}

	public static boolean isQuickMenuShowDuplicateKeepTime() {
		return getBoolean("quick_menu_transaction_duplicate_keep_time", false);
	}

	public static boolean isDuplicateKeepTimeInYesterday() {
		return getBoolean("quick_menu_transaction_duplicate_keep_time_in_yesterday", false);
	}

	public static boolean isQuickMenuShowDuplicateKeepDateTime() {
		return getBoolean("quick_menu_transaction_duplicate_keep_date_time", false);
	}

	public static String getDropboxAuthToken() {
		return getString(DROPBOX_AUTH_TOKEN, null);
	}

	public static void storeDropboxKeys(String sessionToken) {
		edit().putString(DROPBOX_AUTH_TOKEN, sessionToken)
			.putBoolean(DROPBOX_AUTHORIZE, true)
			.apply();
	}

	public static void removeDropboxKeys() {
		edit().remove(DROPBOX_AUTH_TOKEN)
			.remove(DROPBOX_AUTHORIZE)
			.apply();
	}

	public static boolean isDropboxAuthorized() {
		return getBoolean(DROPBOX_AUTHORIZE, false);
	}

	public static boolean isDropboxUploadBackups() {
		return isDropboxAuthorized() && getBoolean("dropbox_upload_backup", false);
	}

	public static boolean isDropboxUploadAutoBackups() {
		return isDropboxAuthorized() && getBoolean("dropbox_upload_autobackup", false);
	}

	public static boolean isDropboxUploadPictures() {
		return isDropboxAuthorized() && getBoolean("dropbox_upload_pictures", false);
	}

	public static boolean isDropboxDownloadPictures() {
		return isDropboxAuthorized() && getBoolean("dropbox_download_pictures", false);
	}

	public static boolean isUseHierarchicalCategorySelector() {
		return getBoolean("use_hierarchical_category_selector", true);
	}

	public static boolean isShowRecentlyUsedCategory() {
		return getBoolean("show_recently_used_category", true);
	}

	public static boolean isAutoSelectChildCategory() {
		return getBoolean("hierarchical_category_selector_select_child_immediately", true);
	}

	public static boolean isSeparateIncomeExpense() {
		return getBoolean("hierarchical_category_selector_income_expense", false);
	}

	public static AccountListDateType getAccountListDateType() {
		String accountListDateType = getString("account_list_date_type", AccountListDateType.LAST_TX.name());
		return AccountListDateType.valueOf(accountListDateType);
	}

	public static boolean isHideClosedAccounts() {
		return getBoolean("hide_closed_accounts", false);
	}

	public static boolean isPinHapticFeedbackEnabled() {
		return getBoolean("pin_protection_haptic_feedback", true);
	}

	public static boolean isShowMenuButtonOnAccountsScreen() {
		return getBoolean("show_menu_button_on_accounts_screen", true);
	}

	public static boolean isShowTransferCurrentBalance() {
		return true /* Trasferisci saldo sta nella bolla del conto, sempre. */;
	}

	public static StartupScreen getStartupScreen() {
		// The transactions list is where the app is actually used, so that is where
		// it opens unless told otherwise.
		String screen = getString("startup_screen", StartupScreen.BLOTTER.name());
		try {
			return StartupScreen.valueOf(screen);
		} catch (IllegalArgumentException e) {
			return StartupScreen.BLOTTER;
		}
	}

	/**
	 * Templates as a tab of their own rather than a button on the blotter, where it
	 * sat among the buttons that create things while it only opens a chooser.
	 */
	public static boolean isQuickBarEnabled(Context context) {
		return getBoolean("quick_bar_enabled", false);
	}

	public static boolean isQuickEntryFromWidget(Context context) {
		return getBoolean("quick_entry_from_widget", true);
	}

	public static boolean isShowSummaryTab(Context context) {
		return getBoolean("show_summary_tab", true);
	}

	/** WEEK, MONTH, QUARTER or YEAR: what the summary tab adds up at a time. */
	public static String getSummaryPeriod(Context context) {
		return getString("summary_period", "MONTH");
	}

	public static boolean isTemplatesAsTab(Context context) {
		return getBoolean("templates_as_tab", true);
	}

	public static boolean isShowBudgetsTab(Context context) {
		return getBoolean("show_budgets_tab", true);
	}

	public static boolean isShowReportsTab(Context context) {
		return getBoolean("show_reports_tab", true);
	}

	public static ExchangeRateProvider createExchangeRatesProvider(Context context) {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(Application.getInstance());
		ExchangeRateProviderFactory factory = getExchangeRateProviderFactory();
		return factory.createProvider(sharedPreferences, context);
	}

	private static ExchangeRateProviderFactory getExchangeRateProviderFactory() {
		String provider = getString("exchange_rate_provider", ExchangeRateProviderFactory.freeCurrency.name());
		ExchangeRateProviderFactory r;
		try {
			r = ExchangeRateProviderFactory.valueOf(provider);
		} catch (IllegalArgumentException e) {
			return ExchangeRateProviderFactory.freeCurrency;
		}
		return r;
	}

	public static boolean isOpenExchangeRatesProviderSelected() {
		return getExchangeRateProviderFactory() == ExchangeRateProviderFactory.openexchangerates;
	}

	private static boolean getBoolean(String name, boolean defaultValue) {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(Application.getInstance());
		return sharedPreferences.getBoolean(name, defaultValue);
	}

	private static String getString(String name, String defaultValue) {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(Application.getInstance());
		return sharedPreferences.getString(name, defaultValue);
	}

	private static long getLong(String name, long defaultValue) {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(Application.getInstance());
		return sharedPreferences.getLong(name, defaultValue);
	}

	private static int getInt(String name, int defaultValue) {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(Application.getInstance());
		return sharedPreferences.getInt(name, defaultValue);
	}

	private static SharedPreferences.Editor edit() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(Application.getInstance());
		return sharedPreferences.edit();
	}

	public static String getGoogleDriveAccount() {
		return getString("google_drive_backup_account", null);
	}

	public static boolean isGoogleDriveUploadBackups() {
		return getBoolean("google_drive_upload_backup", false);
	}

	public static boolean isGoogleDriveUploadAutoBackups() {
		return getBoolean("google_drive_upload_autobackup", false);
	}

	public static boolean isGoogleDriveUploadPictures() {
		return getBoolean("google_drive_upload_pictures", false);
	}

	public static boolean isGoogleDriveDownloadPictures() {
		return getBoolean("google_drive_download_pictures", false);
	}

	public static TransactionStatus getSmsTransactionStatus() {
		return TransactionStatus.valueOf(getString("sms_transaction_status", "PN"));
	}

	public static boolean shouldSaveSmsToTransactionNote() {
		return getBoolean("sms_transaction_note", true);
	}

	public static boolean isGoogleWalletTransactionEnabled() {
		return getBoolean("google_wallet_transaction", false);
	}

	public static TransactionStatus getGoogleWalletTransactionStatus() {
		return TransactionStatus.valueOf(getString("google_wallet_transaction_status", "PN"));
	}

	public static long getLastAutobackupCheck() {
		return getLong("last_autobackup_check", 0);
	}

	public static void updateLastAutobackupCheck() {
		edit().putLong("last_autobackup_check", System.currentTimeMillis()).apply();
	}

	public static boolean isAutoBackupReminderEnabled() {
		return getBoolean("auto_backup_reminder_enabled", true);
	}

	public static boolean isAutoBackupWarningEnabled() {
		return getBoolean("auto_backup_warning_enabled", true);
	}

	/** A failure with the name of what failed, when it was not the backup itself. */
	public static void notifyAutobackupFailed(String where, Exception e) {
		edit()
				.putBoolean("auto_backup_failed_notify", isAutoBackupWarningEnabled())
				.putString("auto_backup_failed_error", where + ": " + messageForException(e))
				.putLong("auto_backup_failed_timestamp", System.currentTimeMillis())
				.apply();
	}

	public static void notifyAutobackupFailed(Exception e) {
		edit()
				.putBoolean("auto_backup_failed_notify", isAutoBackupWarningEnabled())
				.putString("auto_backup_failed_error", messageForException(e))
				.putLong("auto_backup_failed_timestamp", System.currentTimeMillis())
				.apply();
	}

	private static String messageForException(Exception e) {
		if (e instanceof ImportExportException importExportException) {
            String message = Application.getInstance().getString(importExportException.errorResId);
			if (e.getCause() != null) {
				message += " - " + e.getCause().getMessage();
			}
			return message;
		} else {
			return e.getMessage();
		}
	}

	public static void notifyAutobackupSucceeded() {
		edit().putBoolean("auto_backup_failed_notify", false).apply();
	}

	public static AutobackupStatus getAutobackupStatus() {
		return new AutobackupStatus(
				getBoolean("auto_backup_failed_notify", false),
				getString("auto_backup_failed_error", null),
				getLong("auto_backup_failed_timestamp", 0)
		);
	}

	public static class AutobackupStatus {
		public final boolean notify;
		public final String errorMessage;
		public final long timestamp;

		private AutobackupStatus(boolean notify, String errorMessage, long timestamp) {
			this.notify = notify;
			this.errorMessage = errorMessage;
			this.timestamp = timestamp;
		}
	}

}