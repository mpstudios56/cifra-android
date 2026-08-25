/*******************************************************************************
 * Copyright (c) 2010 Denis Solonenko.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * Contributors:
 *     Denis Solonenko - initial API and implementation
 *     Abdsandryk - parameters for bill filtering
 ******************************************************************************/
package io.github.mpstudios56.cifra.model;

import static io.github.mpstudios56.cifra.db.DatabaseHelper.ACCOUNT_TABLE;
import static io.github.mpstudios56.cifra.orb.EntityManager.DEF_SORT_COL;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.Table;

@Entity
@Table(name = ACCOUNT_TABLE)
public class Account extends MyEntity {
	
	@Column(name = "creation_date")
	public long creationDate = System.currentTimeMillis();

    @Column(name = "last_transaction_date")
    public long lastTransactionDate = System.currentTimeMillis();

	@Column(name = "updated_on")
	public long updatedOn = System.currentTimeMillis();

    @JoinColumn(name = "currency_id")
	public Currency currency;

	@Column(name = "type")
	public String type = AccountType.CASH.name();
	
	@Column(name = "card_issuer")
	public String cardIssuer;

	@Column(name = "issuer")
	public String issuer;
	
	@Column(name = "number")
	public String number;
	
	@Column(name = "total_amount")
	public long totalAmount;
	
	@Column(name = "total_limit")
	public long limitAmount;

	@Column(name = DEF_SORT_COL)
	public int sortOrder;

	@Column(name = "is_include_into_totals")
	public boolean isIncludeIntoTotals = true;

	/**
	 * false = virtual sub-account (e.g. a budget envelope or sinking fund): still
	 * counted into totals (that is what it exists for), but its own transactions are
	 * excluded from report statistics, and transfers into it from a normal account
	 * count as expenses of the source account (see the v_report_* views).
	 */
	@Column(name = "is_include_into_reports")
	public boolean isIncludeIntoReports = true;

	/**
	 * One of the handful of accounts money actually moves through every day.
	 * <p>
	 * A household ends up with a dozen accounts and uses three of them: those
	 * three are offered first when a movement asks which account it belongs to,
	 * so the answer is one touch away instead of a scroll.
	 */
	@Column(name = "is_main")
	public boolean isMain = false;

	/**
	 * The heading this account is gathered under, or zero for none.
	 * <p>
	 * Written on the account rather than kept in a list belonging to the
	 * heading, because an account belongs to one group at most: asking the
	 * account is then a single answer and cannot contradict itself.
	 */
	@Column(name = "separator_id")
	public long separatorId = 0;
	
	@Column(name = "last_account_id")
	public long lastAccountId;

	@Column(name = "last_category_id")
	public long lastCategoryId;
	
	@Column(name = "closing_day")
	public int closingDay;
	
	@Column(name = "payment_day")
	public int paymentDay;

    @Column(name = "note")
    public String note;

	@Column(name = "icon")
	public String icon;

	/**
	 * What the chosen colour paints: the symbol, the stripe, or both.
	 * <p>
	 * It used to be written inside the icon field, after the name of the
	 * symbol. An account with no symbol of the app's own - which is every
	 * account restored from an old backup - had nowhere to keep it, so the
	 * choice was made, saved, and read back as "both" for ever.
	 */
	@Column(name = "accent_target")
	public String accentTarget;

	@Column(name = "accent_color")
	public String accentColor;

    public boolean shouldIncludeIntoTotals() {
        return isActive && isIncludeIntoTotals;
    }
}
