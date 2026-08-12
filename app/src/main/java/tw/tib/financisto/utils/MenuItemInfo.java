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
package tw.tib.financisto.utils;

public class MenuItemInfo {
	public final int menuId;
	public int titleId;
	public boolean enabled = true;
	/** Optional: the symbol shown beside the words. 0 leaves the space empty. */
	public int iconId;

	public MenuItemInfo(int menuId, int titleId) {
		this.menuId = menuId;
		this.titleId = titleId;
	}

	public MenuItemInfo(int menuId, int titleId, int iconId) {
		this(menuId, titleId);
		this.iconId = iconId;
	}

}
