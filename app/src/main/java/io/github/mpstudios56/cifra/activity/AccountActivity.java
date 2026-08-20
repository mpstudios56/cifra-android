/*******************************************************************************
 * Copyright (c) 2010 Denis Solonenko.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 *
 * Contributors:
 *     Denis Solonenko - initial API and implementation
 *     Abdsandryk - adding bill filtering parameters
 ******************************************************************************/
package io.github.mpstudios56.cifra.activity;

import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import android.text.InputFilter;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import io.github.mpstudios56.cifra.R;
import io.github.mpstudios56.cifra.adapter.EntityEnumAdapter;
import io.github.mpstudios56.cifra.model.Account;
import io.github.mpstudios56.cifra.model.AccountType;
import io.github.mpstudios56.cifra.model.CardIssuer;
import io.github.mpstudios56.cifra.model.Currency;
import io.github.mpstudios56.cifra.model.ElectronicPaymentType;
import io.github.mpstudios56.cifra.model.MyEntity;
import io.github.mpstudios56.cifra.model.Transaction;
import io.github.mpstudios56.cifra.utils.EntityEnum;
import io.github.mpstudios56.cifra.utils.TransactionUtils;
import io.github.mpstudios56.cifra.utils.AccountIcon;
import io.github.mpstudios56.cifra.utils.Utils;
import io.github.mpstudios56.cifra.widget.AmountInput;
import io.github.mpstudios56.cifra.widget.AmountInput_;
import io.github.mpstudios56.cifra.utils.EnumUtils;

public class AccountActivity extends AbstractActivity {
	public static final String TAG = "AccountActivity";

	public static final String ACCOUNT_ID_EXTRA = "accountId";

	private static final int NEW_CURRENCY_REQUEST = 1;
	public static final int EDIT_ACCOUNT_REQUEST = 2;

	private AmountInput amountInput;
	private AmountInput limitInput;
	private View limitAmountView;
	private EditText accountTitle;
	private EditText iconText;
	private TextView sharedWith;
	/** Empty means everybody in the group. */
	private final java.util.List<String> sharedWithMarks = new java.util.ArrayList<>();
	/** Who it was held with when the screen opened, to see who is new on saving. */
	private final java.util.List<String> sharedWithBefore = new java.util.ArrayList<>();
	private EditText accentColor;

	private List<Currency> currencies;
	private TextView currencyText;
	private View accountTypeNode;
	private View cardIssuerNode;
	private View electronicPaymentNode;
	private View issuerNode;
	private EditText numberText;
	private View numberNode;
	private EditText issuerName;
	private EditText sortOrderText;
	private CheckBox isIncludedIntoTotals;
	private CheckBox isIncludedIntoReports;
	private EditText noteText;
	private EditText closingDayText;
	private EditText paymentDayText;
	private View closingDayNode;
	private View paymentDayNode;
	private View accountIconNode;
	private View accentColorNode;
	private TextView accentTargetText;
	/**
	 * What the accent colour paints, kept here and written to a field of its
	 * own on the account.
	 * <p>
	 * It used to live inside the icon string, so it could only be remembered
	 * for accounts wearing one of the app's own symbols: on everything restored
	 * from an old backup the choice was made, shown, and thrown away.
	 */
	private AccountIcon.Target accentTarget = AccountIcon.Target.BOTH;

	private EntityEnumAdapter<AccountType> accountTypeAdapter;
	private EntityEnumAdapter<CardIssuer> cardIssuerAdapter;
	private EntityEnumAdapter<ElectronicPaymentType> electronicPaymentAdapter;
	private ListAdapter currencyAdapter;

	private Account account = new Account();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.account);

		ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.account), (v, windowInsets) -> {
			Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
					| WindowInsetsCompat.Type.ime());
			var lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
			lp.topMargin = insets.top;
			lp.bottomMargin = insets.bottom;
			v.setLayoutParams(lp);
			return WindowInsetsCompat.CONSUMED;
		});

		accountTitle = new EditText(this);
		accountTitle.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
		accountTitle.setSingleLine();

		issuerName = new EditText(this);
		issuerName.setSingleLine();

		numberText = new EditText(this);
		numberText.setHint(R.string.card_number_hint);
		numberText.setSingleLine();

		sortOrderText = new EditText(this);
		sortOrderText.setInputType(InputType.TYPE_CLASS_NUMBER);
		sortOrderText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
		sortOrderText.setSingleLine();

		closingDayText = new EditText(this);
		closingDayText.setInputType(InputType.TYPE_CLASS_NUMBER);
		closingDayText.setHint(R.string.closing_day_hint);
		closingDayText.setSingleLine();

		paymentDayText = new EditText(this);
		paymentDayText.setInputType(InputType.TYPE_CLASS_NUMBER);
		paymentDayText.setHint(R.string.payment_day_hint);
		paymentDayText.setSingleLine();

		amountInput = AmountInput_.build(this);
		amountInput.setOwner(this);

		limitInput = AmountInput_.build(this);
		limitInput.setOwner(this);

		LinearLayout layout = findViewById(R.id.layout);

		accountTypeAdapter = new EntityEnumAdapter<>(this, AccountType.values(), false);
		accountTypeNode = x.addListNodeIcon(layout, R.id.account_type, R.string.account_type, R.string.account_type);
		ImageView icon = accountTypeNode.findViewById(R.id.icon);
		icon.setColorFilter(ContextCompat.getColor(this, R.color.holo_gray_light));

		// Directly under the kind of account, because this is the other half of how
		// the row will look, and none of it depends on the kind.
		accountIconNode = x.addListNodeIcon(layout, R.id.account_icon, R.string.account_icon, R.string.account_icon_none);
		accentColorNode = x.addListNodeIcon(layout, R.id.accent_color, R.string.accent_color, R.string.account_icon_none);
		// No icon on this one: it is a choice between three words, and the picture
		// beside it said nothing.
		accentTargetText = x.addListNode(layout, R.id.accent_target, R.string.accent_target_title, R.string.accent_target_both);

		cardIssuerAdapter = new EntityEnumAdapter<>(this, CardIssuer.values(), false);
		cardIssuerNode = x.addListNodeIcon(layout, R.id.card_issuer, R.string.card_issuer, R.string.card_issuer);
		setVisibility(cardIssuerNode, View.GONE);

		electronicPaymentAdapter = new EntityEnumAdapter<>(this, ElectronicPaymentType.values(), false);
		electronicPaymentNode = x.addListNodeIcon(layout, R.id.electronic_payment_type, R.string.electronic_payment_type, R.string.card_issuer);
		setVisibility(electronicPaymentNode, View.GONE);

		issuerNode = x.addEditNode(layout, R.string.issuer, issuerName);
		setVisibility(issuerNode, View.GONE);

		numberNode = x.addEditNode(layout, R.string.card_number, numberText);
		setVisibility(numberNode, View.GONE);

		closingDayNode = x.addEditNode(layout, R.string.closing_day, closingDayText);
		setVisibility(closingDayNode, View.GONE);

		paymentDayNode = x.addEditNode(layout, R.string.payment_day, paymentDayText);
		setVisibility(paymentDayNode, View.GONE);

		currencies = db.getAllCurrenciesList();
		currencyAdapter = TransactionUtils.createCurrencyAdapter(this, currencies);

		x.addEditNode(layout, R.string.title, accountTitle);
		currencyText = x.addListNodePlus(layout, R.id.currency, R.id.currency_add, R.string.currency, R.string.select_currency);

		limitInput.setExpense();
		limitInput.disableIncomeExpenseButton();
		limitAmountView = x.addEditNode(layout, R.string.limit_amount, limitInput);
		setVisibility(limitAmountView, View.GONE);

		Intent intent = getIntent();
		if (intent != null) {
			long accountId = intent.getLongExtra(ACCOUNT_ID_EXTRA, -1);
			if (accountId != -1) {
				this.account = db.getAccount(accountId);
				if (this.account == null) {
					this.account = new Account();
				}
			} else {
				selectAccountType(AccountType.valueOf(account.type));
			}
		}

		if (account.id == -1) {
			x.addEditNode(layout, R.string.opening_amount, amountInput);
			amountInput.setIncome();
		}

		noteText = new EditText(this);
		noteText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
		noteText.setLines(2);
		x.addEditNode(layout, R.string.note, noteText);

		// Kept, hidden: an emoji typed in earlier still has to survive a save.
		iconText = new EditText(this);
		iconText.setSingleLine();

		accentColor = new EditText(this);
		accentColor.setSingleLine();

		x.addEditNode(layout, R.string.sort_order, sortOrderText);
		isIncludedIntoTotals = x.addCheckboxNode(layout,
				R.id.is_included_into_totals, R.string.is_included_into_totals,
				R.string.is_included_into_totals_summary, true);
		isIncludedIntoReports = x.addCheckboxNode(layout,
				R.id.is_included_into_reports, R.string.is_included_into_reports,
				R.string.is_included_into_reports_summary, true);
		// Here rather than only on the sharing screen: this is where somebody
		// thinks about what an account is for, and a decision about who sees it
		// belongs beside the decision about whether it counts in the totals.

		// And with whom, once there is more than one person in the folder. With
		// two people the question does not arise; with three the joint account
		// is everybody's and the one for the flat is two of the three, which a
		// tick cannot say.
		// One entry, not two. A tick saying "share this" and then a list of who
		// with is the same question asked twice: ticking somebody is what
		// sharing means, and ticking nobody is what not sharing means.
		sharedWith = x.addInfoNode(layout, R.id.shared_with,
				R.string.account_shared_with, getString(R.string.account_shared_with_nobody));

		if (account.id > 0) {
			editAccount();
		}

		Button bOK = findViewById(R.id.bOK);
		bOK.setOnClickListener(arg0 -> {
			if (account.currency == null) {
				Toast.makeText(AccountActivity.this, R.string.select_currency, Toast.LENGTH_SHORT).show();
				return;
			}
			if (Utils.isEmpty(accountTitle)) {
				accountTitle.setError(getString(R.string.title));
				return;
			}
			AccountType type = AccountType.valueOf(account.type);
			if (type.hasIssuer) {
				account.issuer = Utils.text(issuerName);
			}
			if (type.hasNumber) {
				account.number = Utils.text(numberText);
			}

			/********** validate closing and payment days **********/
			if (type.isCreditCard) {
				String closingDay = Utils.text(closingDayText);
				account.closingDay = closingDay == null ? 0 : Integer.parseInt(closingDay);
				if (account.closingDay != 0) {
					if (account.closingDay > 31) {
						Toast.makeText(AccountActivity.this, R.string.closing_day_error, Toast.LENGTH_SHORT).show();
						return;
					}
				}

				String paymentDay = Utils.text(paymentDayText);
				account.paymentDay = paymentDay == null ? 0 : Integer.parseInt(paymentDay);
				if (account.paymentDay != 0) {
					if (account.paymentDay > 31) {
						Toast.makeText(AccountActivity.this, R.string.payment_day_error, Toast.LENGTH_SHORT).show();
						return;
					}
				}
			}

			account.title = Utils.text(accountTitle);
			String sortOrder = Utils.text(sortOrderText);
			account.sortOrder = sortOrder == null ? 0 : Integer.parseInt(sortOrder);
			account.isIncludeIntoTotals = isIncludedIntoTotals.isChecked();
			account.isIncludeIntoReports = isIncludedIntoReports.isChecked();
			account.limitAmount = -Math.abs(limitInput.getAmount());
			account.note = Utils.text(noteText);
			account.icon = iconText.getText().toString().trim();
			account.accentTarget = accentTarget.tag;
			account.accentColor = accentColor.getText().toString().trim();
			// stored as written; see AccountIcon for the format

			long accountId = db.saveAccount(account);
			// After saving, because a brand new account has no identifier to
			// share until it has one.
			String accountUuid = io.github.mpstudios56.cifra.db.Uuids.of(db.db(),
					io.github.mpstudios56.cifra.db.DatabaseHelper.ACCOUNT_TABLE, accountId);
			boolean wasShared = io.github.mpstudios56.cifra.sync.SharedThings.isShared(db.db(),
					io.github.mpstudios56.cifra.sync.SharedThings.ACCOUNT, accountUuid);
			// Shared with somebody is what shared means - and an account that
			// arrived from somebody keeps travelling with them whether or not
			// this phone gives it to anyone.
			boolean nowShared = !sharedWithMarks.isEmpty();
			boolean arrived = !io.github.mpstudios56.cifra.sync.SharedWith
					.arrivedFrom(db.db(), accountUuid).isEmpty();
			io.github.mpstudios56.cifra.sync.SharedThings.set(db.db(),
					io.github.mpstudios56.cifra.sync.SharedThings.ACCOUNT, accountUuid,
					nowShared || arrived);
			io.github.mpstudios56.cifra.sync.SharedWith.set(db.db(), accountUuid, sharedWithMarks);

			long amount = amountInput.getAmount();
			if (amount != 0) {
				Transaction t = new Transaction();
				t.fromAccountId = accountId;
				t.categoryId = 0;
				t.note = getResources().getText(R.string.opening_amount) + " (" + account.title + ")";
				t.fromAmount = amount;
				db.insertOrUpdate(t, null);
			}
			AccountWidget.updateWidgets(this);
			Intent intent1 = new Intent();
			intent1.putExtra(ACCOUNT_ID_EXTRA, accountId);
			setResult(RESULT_OK, intent1);
			// Shared for the first time: ask before leaving, and leave when the
			// answer is given. It used to be asked and closed in the same
			// breath, so the question was on screen for as long as it took the
			// screen to go away.
			// Asked every time the account is saved while it is shared, not only
			// when somebody new appears: an account detached and put back is the
			// same decision to take again, and there is no way to take it if the
			// question does not come.
			if (nowShared) {
				askWhatToShareOfThePast(accountId, accountUuid, account.title);
			} else {
				finish();
			}
		});

		Button bCancel = findViewById(R.id.bCancel);
		bCancel.setOnClickListener(arg0 -> {
			setResult(RESULT_CANCELED);
			finish();
		});

	}

	@Override
	protected void onClick(View v, int id) {
		switch (id) {
			case R.id.is_included_into_totals:
				isIncludedIntoTotals.performClick();
				break;
			case R.id.is_included_into_reports:
				isIncludedIntoReports.performClick();
				break;
			// Without this the row does nothing at all when tapped: the tick
			// itself is not what receives the touch, the row is.
			case R.id.shared_with:
				chooseWhoWith();
				break;
			case R.id.account_icon:
				showIconPicker();
				break;
			case R.id.accent_color:
				showColorPicker();
				break;
			case R.id.accent_target:
				showAccentTargetPicker();
				break;
			case R.id.account_type:
				x.selectPosition(this, R.id.account_type, R.string.account_type, accountTypeAdapter, AccountType.valueOf(account.type).ordinal());
				break;
			case R.id.card_issuer:
				x.selectPosition(this, R.id.card_issuer, R.string.card_issuer, cardIssuerAdapter,
						account.cardIssuer != null ? CardIssuer.valueOf(account.cardIssuer).ordinal() : 0);
				break;
			case R.id.electronic_payment_type:
				x.selectPosition(this, R.id.electronic_payment_type, R.string.electronic_payment_type, electronicPaymentAdapter,
						EnumUtils.selectEnum(ElectronicPaymentType.class, account.cardIssuer, ElectronicPaymentType.PAYPAL).ordinal());
				break;
			case R.id.currency:
				int selectedPos = MyEntity.indexOf(currencies, account.currency != null ? account.currency.id : -1);
				x.selectItemId(this, R.id.currency, R.string.currency, currencyAdapter, selectedPos);
				break;
			case R.id.currency_add:
				addNewCurrency();
				break;
		}
	}

	/**
	 * Who this account is held with.
	 * <p>
	 * The people are the ones seen in the shared folder - nobody types them in,
	 * because each phone already writes a file called after its owner. Choosing
	 * nobody means everybody, which is what the tick meant on its own.
	 */
	private void chooseWhoWith() {
		java.util.List<io.github.mpstudios56.cifra.sync.People.Person> people =
				io.github.mpstudios56.cifra.sync.People.all(db.db());
		if (people.isEmpty()) {
			Toast.makeText(this, R.string.account_shared_with_nobody_yet, Toast.LENGTH_LONG).show();
			return;
		}
		String[] labels = new String[people.size()];
		boolean[] chosen = new boolean[people.size()];
		for (int i = 0; i < people.size(); i++) {
			labels[i] = people.get(i).label();
			chosen[i] = sharedWithMarks.contains(people.get(i).mark);
		}
		new AlertDialog.Builder(this)
				.setTitle(R.string.account_shared_with)
				.setMultiChoiceItems(labels, chosen, (d, which, isChecked) -> chosen[which] = isChecked)
				.setPositiveButton(R.string.ok, (d, w) -> {
					sharedWithMarks.clear();
					for (int i = 0; i < people.size(); i++) {
						if (chosen[i]) {
							sharedWithMarks.add(people.get(i).mark);
						}
					}
					showWhoWith(people);
				})
				.setNegativeButton(R.string.cancel, null)
				.show();
	}

	private void showWhoWith(java.util.List<io.github.mpstudios56.cifra.sync.People.Person> people) {
		if (sharedWith == null) {
			return;
		}
		if (sharedWithMarks.isEmpty()) {
			sharedWith.setText(R.string.account_shared_with_nobody);
			return;
		}
		StringBuilder sb = new StringBuilder();
		for (io.github.mpstudios56.cifra.sync.People.Person p : people) {
			if (sharedWithMarks.contains(p.mark)) {
				if (sb.length() > 0) sb.append(", ");
				sb.append(p.label());
			}
		}
		sharedWith.setText(sb.toString());
	}

	/**
	 * What of the account's past the other person gets: everything, today's
	 * balance as one opening line, or nothing.
	 * <p>
	 * The exchange carries changes as they are made, so without this an account
	 * that has been running for years arrives at the other phone empty.
	 */
	private void askWhatToShareOfThePast(long accountId, String accountUuid, String title) {
		String[] choices = {
				getString(R.string.share_past_everything),
				getString(R.string.share_past_balance),
				getString(R.string.share_past_nothing),
		};
		final int[] picked = {0};
		new AlertDialog.Builder(this)
				.setTitle(R.string.share_past_title)
				.setSingleChoiceItems(choices, 0, (d, which) -> picked[0] = which)
				.setPositiveButton(R.string.save, (d, w) -> {
					int queued = io.github.mpstudios56.cifra.sync.SharingStart.apply(
							db.db(), accountId, accountUuid, title, picked[0]);
					if (queued > 0) {
						Toast.makeText(this,
								getString(R.string.share_past_queued, queued),
								Toast.LENGTH_SHORT).show();
					}
					finish();
				})
				// Cancel leaves the account shared, because that was saved on
				// the screen behind: what is being answered here is only which
				// movements go, and not answering means the new ones.
				.setNegativeButton(R.string.cancel, (d, w) -> finish())
				.setCancelable(false)
				.show();
	}

	private void addNewCurrency() {
		new CurrencySelector(this, db, currencyId -> {
			if (currencyId == 0) {
				Intent intent = new Intent(AccountActivity.this, CurrencyActivity.class);
				startActivityForResult(intent, NEW_CURRENCY_REQUEST);
			} else {
				currencies = db.getAllCurrenciesList();
				selectCurrency(currencyId);
			}
		}).show();
	}

	@Override
	public void onSelectedId(int id, long selectedId) {
		switch (id) {
			case R.id.currency:
				selectCurrency(selectedId);
				break;
		}
	}

	@Override
	public void onSelectedPos(int id, int selectedPos) {
		switch (id) {
			case R.id.account_icon:
				showIconPicker();
				break;
			case R.id.accent_color:
				showColorPicker();
				break;
			case R.id.accent_target:
				showAccentTargetPicker();
				break;
			case R.id.account_type:
				AccountType type = AccountType.values()[selectedPos];
				selectAccountType(type);
				break;
			case R.id.card_issuer:
				CardIssuer issuer = CardIssuer.values()[selectedPos];
				selectCardIssuer(issuer);
				break;
			case R.id.electronic_payment_type:
				ElectronicPaymentType paymentType = ElectronicPaymentType.values()[selectedPos];
				selectElectronicType(paymentType);
				break;
		}
	}

	/**
	 * Symbols the account can carry regardless of its kind, so a current account and
	 * a securities account at the same institution can be marked the same way.
	 */
	private void showIconPicker() {
		final AccountIcon[] icons = AccountIcon.values();
		final int tint = currentAccentColor();

		// Three columns of larger tiles rather than four small ones: several of the
		// marks are words, and at four across they were unreadable. The dialog is
		// given a height so the grid scrolls instead of running off the bottom -
		// the marks added last were simply unreachable before.
		GridView grid = new GridView(this);
		grid.setNumColumns(3);
		grid.setPadding(24, 24, 24, 24);
		grid.setVerticalScrollBarEnabled(true);
		grid.setLayoutParams(new ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				(int) (getResources().getDisplayMetrics().heightPixels * 0.6)));
		grid.setAdapter(new BaseAdapter() {
			@Override public int getCount() { return icons.length; }
			@Override public Object getItem(int position) { return icons[position]; }
			@Override public long getItemId(int position) { return position; }
			@Override public View getView(int position, View convertView, ViewGroup parent) {
				ImageView view = convertView instanceof ImageView
						? (ImageView) convertView : new ImageView(AccountActivity.this);
				view.setLayoutParams(new GridView.LayoutParams(
						GridView.LayoutParams.MATCH_PARENT, 230));
				view.setPadding(20, 20, 20, 20);
				view.setImageResource(icons[position].iconId);
				if (icons[position].tintable) {
					view.setColorFilter(tint);
				} else {
					view.clearColorFilter();
				}
				return view;
			}
		});

		final AlertDialog dialog = new AlertDialog.Builder(this)
				.setTitle(R.string.account_icon)
				.setView(grid)
				.setNeutralButton(R.string.account_icon_none, (d, which) -> {
					iconText.setText("");
					showIconOnNode();
				})
				.setNegativeButton(R.string.cancel, null)
				.create();
		grid.setOnItemClickListener((parent, view, position, id) -> {
			iconText.setText(icons[position].toStoredValue(currentTarget()));
			showIconOnNode();
			dialog.dismiss();
		});
		dialog.show();
	}

	private void showColorPicker() {
		// The same eighteen the categories are painted from, and the only
		// palette in the app. The twenty here were the web colours of 1996 -
		// pure red, pure yellow, black and white - and an account wearing one
		// of them beside a category wearing a material colour looked like two
		// different programs.
		final String[] colors = {
				"#e53935", "#d81b60", "#8e24aa", "#5e35b1", "#3949ab", "#1e88e5",
				"#039be5", "#00acc1", "#00897b", "#43a047", "#7cb342", "#c0ca33",
				"#fdd835", "#ffb300", "#fb8c00", "#f4511e", "#6d4c41", "#757575"
		};
		var adapter = new ArrayAdapter<>(this, R.layout.select_entry_color_row, colors) {
			@Override
			public View getView(int position, View convertView, ViewGroup parent) {
				View v;
				final var inflater = LayoutInflater.from(getContext());
				if (convertView == null) {
					convertView = inflater.inflate(R.layout.select_entry_color_row, parent, false);
					v = convertView.findViewById(R.id.color_patch);
					convertView.setTag(v);
				} else {
					v = (View) convertView.getTag();
				}
				v.setBackground(new ColorDrawable(Color.parseColor(colors[position])));
				return convertView;
			}
		};
		new AlertDialog.Builder(this)
				.setTitle(R.string.select_color)
				.setAdapter(adapter, (dialog, which) -> {
					accentColor.setText(colors[which]);
					showColorOnNode();
					showIconOnNode();
				})
				.setNeutralButton(R.string.account_icon_none, (d, which) -> {
					accentColor.setText("");
					showColorOnNode();
					showIconOnNode();
				})
				.show();
	}

	private void showAccentTargetPicker() {
		final AccountIcon.Target[] targets = {
				AccountIcon.Target.ICON, AccountIcon.Target.BAR, AccountIcon.Target.BOTH};
		final String[] labels = {
				getString(R.string.accent_target_icon),
				getString(R.string.accent_target_bar),
				getString(R.string.accent_target_both)};
		new AlertDialog.Builder(this)
				.setTitle(R.string.accent_target_title)
				.setItems(labels, (d, which) -> {
					accentTarget = targets[which];
					AccountIcon chosen = AccountIcon.parse(iconText.getText().toString());
					if (chosen != null) {
						// Written into the icon as well, so a phone with an
						// older copy of the app still reads it.
						iconText.setText(chosen.toStoredValue(targets[which]));
					}
					showTargetOnNode();
					showIconOnNode();
				})
				.show();
	}

	/** The accent as a colour, or the symbols' own grey when none is set. */
	private int currentAccentColor() {
		try {
			return Color.parseColor(accentColor.getText().toString().trim());
		} catch (Exception e) {
			return Color.parseColor("#FFFFFFFF");
		}
	}

	private AccountIcon.Target currentTarget() {
		return accentTarget;
	}

	private void showIconOnNode() {
		String stored = iconText.getText().toString();
		AccountIcon chosen = AccountIcon.parse(stored);
		TextView label = accountIconNode.findViewById(R.id.data);
		ImageView preview = accountIconNode.findViewById(R.id.icon);
		if (chosen != null) {
			label.setText(chosen.titleId);
			preview.setVisibility(View.VISIBLE);
			preview.setImageResource(chosen.iconId);
			if (!chosen.tintable) {
				preview.clearColorFilter();
			} else {
				preview.setColorFilter(currentTarget() == AccountIcon.Target.BAR
						? Color.parseColor("#FFFFFFFF") : currentAccentColor());
			}
		} else if (!Utils.isEmpty(stored)) {
			label.setText(stored);
			preview.setVisibility(View.INVISIBLE);
		} else {
			label.setText(R.string.account_icon_none);
			preview.setVisibility(View.INVISIBLE);
		}
	}

	private void showColorOnNode() {
		String value = accentColor.getText().toString().trim();
		TextView label = accentColorNode.findViewById(R.id.data);
		ImageView preview = accentColorNode.findViewById(R.id.icon);
		if (Utils.isEmpty(value)) {
			label.setText(R.string.account_icon_none);
			preview.setVisibility(View.INVISIBLE);
			return;
		}
		label.setText(value);
		try {
			preview.setVisibility(View.VISIBLE);
			preview.setImageResource(R.drawable.color_swatch);
			preview.setColorFilter(Color.parseColor(value));
		} catch (Exception e) {
			preview.setVisibility(View.INVISIBLE);
		}
	}

	private void showTargetOnNode() {
		switch (currentTarget()) {
			case ICON: accentTargetText.setText(R.string.accent_target_icon); break;
			case BAR: accentTargetText.setText(R.string.accent_target_bar); break;
			default: accentTargetText.setText(R.string.accent_target_both); break;
		}
	}

	private void selectAccountType(AccountType type) {
		ImageView icon = accountTypeNode.findViewById(R.id.icon);
		icon.setImageResource(type.iconId);
		TextView label = accountTypeNode.findViewById(R.id.label);
		label.setText(type.titleId);

		// The symbol chooser holds the card and service marks itself, so these
		// two older lists showed the same thing twice and won when they should
		// not have.
		setVisibility(cardIssuerNode, View.GONE);
		setVisibility(issuerNode, type.hasIssuer ? View.VISIBLE : View.GONE);
		setVisibility(electronicPaymentNode, View.GONE);
		setVisibility(numberNode, type.hasNumber ? View.VISIBLE : View.GONE);
		setVisibility(closingDayNode, type.isCreditCard ? View.VISIBLE : View.GONE);
		setVisibility(paymentDayNode, type.isCreditCard ? View.VISIBLE : View.GONE);

		setVisibility(limitAmountView, type == AccountType.CREDIT_CARD ? View.VISIBLE : View.GONE);
		account.type = type.name();
		if (type.isCard) {
			selectCardIssuer(EnumUtils.selectEnum(CardIssuer.class, account.cardIssuer, CardIssuer.DEFAULT));
		} else if (type.isElectronic) {
			selectElectronicType(EnumUtils.selectEnum(ElectronicPaymentType.class, account.cardIssuer, ElectronicPaymentType.PAYPAL));
		} else {
			account.cardIssuer = null;
		}
	}

	private void selectCardIssuer(CardIssuer issuer) {
		updateNode(cardIssuerNode, issuer);
		account.cardIssuer = issuer.name();
	}

	private void selectElectronicType(ElectronicPaymentType paymentType) {
		updateNode(electronicPaymentNode, paymentType);
		account.cardIssuer = paymentType.name();
	}

	private void updateNode(View note, EntityEnum enumItem) {
		ImageView icon = note.findViewById(R.id.icon);
		icon.setImageResource(enumItem.getIconId());
		TextView label = note.findViewById(R.id.label);
		label.setText(enumItem.getTitleId());
	}

	private void selectCurrency(long currencyId) {
		Currency c = db.get(Currency.class, currencyId);
		if (c != null) {
			selectCurrency(c);
		}
	}

	private void selectCurrency(Currency c) {
		currencyText.setText(c.name);
		amountInput.setCurrency(c);
		limitInput.setCurrency(c);
		account.currency = c;
	}

	private void editAccount() {
		selectAccountType(AccountType.valueOf(account.type));
		selectCurrency(account.currency);
		accountTitle.setText(account.title);
		issuerName.setText(account.issuer);
		numberText.setText(account.number);
		sortOrderText.setText(String.valueOf(account.sortOrder));

		/******** bill filtering ********/
		if (account.closingDay > 0) {
			closingDayText.setText(String.valueOf(account.closingDay));
		}
		if (account.paymentDay > 0) {
			paymentDayText.setText(String.valueOf(account.paymentDay));
		}
		/********************************/

		isIncludedIntoTotals.setChecked(account.isIncludeIntoTotals);
		isIncludedIntoReports.setChecked(account.isIncludeIntoReports);
		String uuid = io.github.mpstudios56.cifra.db.Uuids.of(db.db(),
				io.github.mpstudios56.cifra.db.DatabaseHelper.ACCOUNT_TABLE, account.id);
		sharedWithMarks.clear();
		sharedWithMarks.addAll(io.github.mpstudios56.cifra.sync.SharedWith.of(db.db(), uuid));
		sharedWithBefore.clear();
		sharedWithBefore.addAll(sharedWithMarks);
		showWhoWith(io.github.mpstudios56.cifra.sync.People.all(db.db()));
		if (account.limitAmount != 0) {
			limitInput.setAmount(-Math.abs(account.limitAmount));
		}
		noteText.setText(account.note);
		accentColor.setText(account.accentColor);
		iconText.setText(account.icon);
		// What the colour paints, as this account has it: from its own field
		// when it has one, and from the old way of writing it otherwise.
		accentTarget = AccountIcon.targetOf(account);
		showIconOnNode();
		showColorOnNode();
		showTargetOnNode();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (resultCode == RESULT_OK) {
			switch (requestCode) {
				case NEW_CURRENCY_REQUEST:
					currencies = db.getAllCurrenciesList();
					long currencyId = data.getLongExtra(CurrencyActivity.CURRENCY_ID_EXTRA, -1);
					if (currencyId != -1) {
						selectCurrency(currencyId);
					}
					break;
			}
		}
	}

	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
	}

}
