/*
 * Copyright (c) 2011 Denis Solonenko.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */

package io.github.mpstudios56.cifra.export.qif;

import android.content.Intent;
import android.net.Uri;

import io.github.mpstudios56.cifra.activity.QifImportActivity;
import io.github.mpstudios56.cifra.model.Currency;
import io.github.mpstudios56.cifra.utils.CurrencyCache;

/**
 * Created by IntelliJ IDEA.
 * User: Denis Solonenko
 * Date: 7/10/11 7:01 PM
 */
public class QifImportOptions {

    public final QifDateFormat dateFormat;
    public final Uri uri;
    public final Currency currency;

    public QifImportOptions(Uri uri, QifDateFormat dateFormat, Currency currency) {
        this.uri = uri;
        this.dateFormat = dateFormat;
        this.currency = currency;
    }

    public static QifImportOptions fromIntent(Intent data) {
        Uri uri = Uri.parse(data.getStringExtra(QifImportActivity.QIF_IMPORT_URI));
        int f = data.getIntExtra(QifImportActivity.QIF_IMPORT_DATE_FORMAT, 0);
        long currencyId = data.getLongExtra(QifImportActivity.QIF_IMPORT_CURRENCY, 1);
        Currency currency = CurrencyCache.getCurrency(currencyId);
        return new QifImportOptions(uri, QifDateFormat.values()[f], currency);
    }

}
