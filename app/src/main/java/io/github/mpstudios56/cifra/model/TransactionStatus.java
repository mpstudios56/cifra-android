/*******************************************************************************
 * Copyright (c) 2010 Denis Solonenko.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * Contributors:
 *     Denis Solonenko - initial API and implementation
 ******************************************************************************/
package io.github.mpstudios56.cifra.model;

import io.github.mpstudios56.cifra.R;
import io.github.mpstudios56.cifra.utils.EntityEnum;

/**
 * Where a movement stands: written down, still to happen, checked against the
 * statement. Each carries its own mark, so wherever the five are listed they
 * can be shown as they are drawn on the rows themselves.
 */
public enum TransactionStatus implements EntityEnum {
	RS(R.string.transaction_status_restored, R.drawable.status_restored, R.color.restored_transaction_color),
	PN(R.string.transaction_status_pending, R.drawable.status_pending, R.color.pending_transaction_color),
	UR(R.string.transaction_status_unreconciled, R.drawable.status_unreconciled, R.color.unreconciled_transaction_color),
	CL(R.string.transaction_status_cleared, R.drawable.status_cleared, R.color.cleared_transaction_color),
	RC(R.string.transaction_status_reconciled, R.drawable.status_reconciled, R.color.reconciled_transaction_color);
	
	public final int titleId;
	public final int iconId;
	public final int colorId;
	
	private TransactionStatus(int titleId, int iconId, int colorId) {
		this.titleId = titleId;
		this.iconId = iconId;
		this.colorId = colorId;
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
