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
package io.github.mpstudios56.cifra.dialog;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import io.github.mpstudios56.cifra.R;
import io.github.mpstudios56.cifra.activity.BlotterOperations;
import io.github.mpstudios56.cifra.db.DatabaseAdapter;
import io.github.mpstudios56.cifra.model.Account;
import io.github.mpstudios56.cifra.model.AccountType;
import io.github.mpstudios56.cifra.model.Category;
import io.github.mpstudios56.cifra.model.MyLocation;
import io.github.mpstudios56.cifra.model.Project;
import io.github.mpstudios56.cifra.model.Transaction;
import io.github.mpstudios56.cifra.model.TransactionAttributeInfo;
import io.github.mpstudios56.cifra.model.TransactionInfo;
import io.github.mpstudios56.cifra.model.TransactionStatus;
import io.github.mpstudios56.cifra.recur.Recurrence;
import io.github.mpstudios56.cifra.utils.MyPreferences;
import io.github.mpstudios56.cifra.utils.Utils;
import io.github.mpstudios56.cifra.view.NodeInflater;

import static io.github.mpstudios56.cifra.utils.Utils.isNotEmpty;

public class TransactionInfoDialog {

    private final Context context;
    private final DatabaseAdapter db;
    private final NodeInflater inflater;
    private final LayoutInflater layoutInflater;
    private final int splitPadding;
    private final Utils u;

    public TransactionInfoDialog(Context context, DatabaseAdapter db, NodeInflater inflater) {
        this.context = context;
        this.db = db;
        this.inflater = inflater;
        this.layoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        this.splitPadding = context.getResources().getDimensionPixelSize(R.dimen.transaction_icon_padding);
        this.u = new Utils(context);
    }

    public void show(Context context, BlotterOperations.BlotterOperationsCallback callback, long transactionId) {
        TransactionInfo ti = db.getTransactionInfo(transactionId);
        if (ti == null) {
            Toast t = Toast.makeText(context, R.string.no_transaction_found, Toast.LENGTH_LONG);
            t.show();
            return;
        }
        if (ti.parentId > 0) {
            ti = db.getTransactionInfo(ti.parentId);
        }
        View v = layoutInflater.inflate(R.layout.info_dialog, null);
        LinearLayout layout = v.findViewById(R.id.list);

        View titleView = createTitleView(ti, layout);
        createMainInfoNodes(ti, layout);
        createAdditionalInfoNodes(ti, layout);

        showDialog(context, callback, transactionId, v, titleView);
    }

    private void createMainInfoNodes(TransactionInfo ti, LinearLayout layout) {
        if (ti.toAccount == null) {
            createLayoutForTransaction(ti, layout);
        } else {
            createLayoutForTransfer(ti, layout);
        }
    }

    private void createLayoutForTransaction(TransactionInfo ti, LinearLayout layout) {
        Account fromAccount = ti.fromAccount;
        AccountType formAccountType = AccountType.valueOf(ti.fromAccount.type);
        add(layout, R.string.account, ti.fromAccount.title, formAccountType);
        if (ti.payee != null) {
            addWithIcon(layout, R.string.payee, ti.payee.title, R.drawable.ic_action_users);
        }
        addCategory(layout, ti);
        if (ti.originalCurrency != null) {
            TextView amount = add(layout, R.string.original_amount, "");
            u.setAmountText(amount, ti.originalCurrency, ti.originalFromAmount, true);
        }
        TextView amount = add(layout, R.string.amount, "");
        u.setAmountText(amount, ti.fromAccount.currency, ti.fromAmount, true);
        if (ti.category.isSplit()) {
            List<Transaction> splits = db.getSplitsForTransaction(ti.id);
            for (Transaction split : splits) {
                addSplitInfo(layout, fromAccount, split);
            }
        }
    }

    private void addSplitInfo(LinearLayout layout, Account fromAccount, Transaction split) {
        if (split.isTransfer()) {
            Account toAccount = db.getAccount(split.toAccountId);
            String title = u.getTransferTitleText(fromAccount, toAccount);
            LinearLayout topLayout = add(layout, title, "");
            TextView amountView = topLayout.findViewById(R.id.data);
            u.setTransferAmountText(amountView, fromAccount.currency, split.fromAmount, toAccount.currency, split.toAmount);
            topLayout.setPadding(splitPadding, 0, 0, 0);
        } else {
            Category c = db.getCategoryWithParent(split.categoryId);
            StringBuilder sb = new StringBuilder();
            if (c != null && c.id > 0) {
                sb.append(c.title);
            }
            if (isNotEmpty(split.note)) {
                sb.append(" (").append(split.note).append(")");
            }
            LinearLayout topLayout = add(layout, sb.toString(), "");
            TextView amountView = topLayout.findViewById(R.id.data);
            u.setAmountText(amountView, fromAccount.currency, split.fromAmount, true);
            topLayout.setPadding(splitPadding, 0, 0, 0);
        }
    }

    private void createLayoutForTransfer(TransactionInfo ti, LinearLayout layout) {
        AccountType fromAccountType = AccountType.valueOf(ti.fromAccount.type);
        add(layout, R.string.account_from, ti.fromAccount.title, fromAccountType);
        TextView amountView = add(layout, R.string.amount_from, "");
        u.setAmountText(amountView, ti.fromAccount.currency, ti.fromAmount, true);
        AccountType toAccountType = AccountType.valueOf(ti.toAccount.type);
        add(layout, R.string.account_to, ti.toAccount.title, toAccountType);
        amountView = add(layout, R.string.amount_to, "");
        u.setAmountText(amountView, ti.toAccount.currency, ti.toAmount, true);
        if (MyPreferences.isShowPayeeInTransfers()) {
            add(layout, R.string.payee, ti.payee != null ? ti.payee.title : "");
        }
        if (MyPreferences.isShowCategoryInTransferScreen()) {
            add(layout, R.string.category, ti.category != null ? ti.category.title : "");
        }
    }

    private void createAdditionalInfoNodes(TransactionInfo ti, LinearLayout layout) {
        List<TransactionAttributeInfo> attributes = db.getAttributesForTransaction(ti.id);
        for (TransactionAttributeInfo tai : attributes) {
            String value = tai.getValue(context);
            if (isNotEmpty(value)) {
                add(layout, tai.name, value);
            }
        }

        Project project = ti.project;
        if (project != null && project.id > 0) {
            add(layout, R.string.project, project.title);
        }

        if (!Utils.isEmpty(ti.note)) {
            add(layout, R.string.note, ti.note);
        }

        // Who wrote it, when it was not written here. A colour on the row says
        // "not yours"; the name says whose, which is the question somebody asks
        // when a payment they do not recognise turns up in a shared account.
        addAuthor(layout, ti.id);

        MyLocation location = ti.location;
        String locationTitle;
        if (location != null && location.id > 0) {
            locationTitle = location.title + (location.resolvedAddress != null ? " (" + location.resolvedAddress + ")" : "");
            addWithIcon(layout, R.string.location, locationTitle, R.drawable.ic_action_location_2);
        }

    }

    private View createTitleView(TransactionInfo ti, LinearLayout layout) {
        View titleView = layoutInflater.inflate(R.layout.info_dialog_title, null);
        TextView titleLabel = titleView.findViewById(R.id.label);
        TextView titleData = titleView.findViewById(R.id.data);
        ImageView titleIcon = titleView.findViewById(R.id.icon);
        if (ti.isTemplate()) {
            titleLabel.setText(ti.templateName);
        } else {
            if (ti.isScheduled() && ti.recurrence != null) {
                Recurrence r = Recurrence.parse(ti.recurrence);
                titleLabel.setText(r.toInfoString(context));
            } else {
                int titleId = ti.isSplitParent()
                        ? R.string.split
                        : (ti.toAccount == null ? R.string.transaction : R.string.transfer);
                titleLabel.setText(titleId);
                add(layout, R.string.date, DateUtils.formatDateTime(context, ti.dateTime,
                        DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_YEAR),
                        ti.attachedPicture);
            }
        }
        TransactionStatus status = ti.status;
        titleData.setText(context.getString(status.titleId));
        // In the colour of the state itself. It is the one word up here that
        // carries a meaning of its own, and it was the same grey as the rest.
        titleData.setTextColor(readableOnDark(androidx.core.content.ContextCompat.getColor(
                context, status.colorId)));
        titleIcon.setImageResource(status.iconId);
        return titleView;
    }

    private void showDialog(final Context context, final BlotterOperations.BlotterOperationsCallback callback, final long transactionId, final View v, View titleView) {
        final Dialog d = new AlertDialog.Builder(context)
                .setCustomTitle(titleView)
                .setView(v)
                .create();
        d.setCanceledOnTouchOutside(true);

        Button bEdit = v.findViewById(R.id.bEdit);
        bEdit.setOnClickListener(arg0 -> {
            d.dismiss();
            new BlotterOperations(context, callback, db, transactionId).editTransaction();
        });

        Button bClose = v.findViewById(R.id.bClose);
        bClose.setOnClickListener(arg0 -> d.dismiss());

        d.show();
    }

    private void add(LinearLayout layout, int labelId, String data, AccountType accountType) {
        inflater.new Builder(layout, R.layout.select_entry_simple_icon)
                .withIcon(accountType.iconId).withLabel(labelId).withData(data).create();
    }

    /** The line "Generato da: X", shown only on movements that came from somebody. */
    private void addAuthor(LinearLayout layout, long transactionId) {
        String createdBy = "";
        try (android.database.Cursor c = db.db().query("transactions",
                new String[]{"created_by"}, "_id=?",
                new String[]{String.valueOf(transactionId)}, null, null, null)) {
            if (c.moveToFirst() && c.getString(0) != null) {
                createdBy = c.getString(0);
            }
        } catch (Exception ignored) {
        }
        if (createdBy.isEmpty()
                || io.github.mpstudios56.cifra.utils.Identity.isMine(createdBy)) {
            return;
        }
        String who = createdBy;
        for (io.github.mpstudios56.cifra.sync.People.Person p
                : io.github.mpstudios56.cifra.sync.People.all(db.db())) {
            if (createdBy.equals(p.mark)) {
                who = p.label();
                break;
            }
        }
        // In the colour that person was given, the same one the row in the list
        // carries: the name answers "whose", the colour joins it to the dot that
        // led somebody to open the card in the first place.
        View row = addWithIcon(layout, R.string.transaction_written_by, who,
                R.drawable.dot);
        ImageView mark = row.findViewById(R.id.icon);
        if (mark != null) {
            mark.setColorFilter(io.github.mpstudios56.cifra.utils.Identity.colourOf(db.db(), createdBy));
        }
    }

    /** The category, wearing its own symbol and its own colour. */
    private void addCategory(LinearLayout layout, TransactionInfo ti) {
        String path = String.join(" / ", db.getFullCategoryPath(ti.category));
        View row = addWithIcon(layout, R.string.category, path, R.drawable.ic_action_category);
        ImageView icon = row.findViewById(R.id.icon);
        if (icon == null || ti.category == null) {
            return;
        }
        try {
            io.github.mpstudios56.cifra.utils.CategoryIcons.show(icon, ti.category);
        } catch (Exception ignored) {
            // A category with no symbol of its own keeps the plain one.
        }
    }

    /** One line with a symbol beside it. */
    private View addWithIcon(LinearLayout layout, int labelId, String data, int iconId) {
        return inflater.new Builder(layout, R.layout.select_entry_simple_icon)
                .withIcon(iconId).withLabel(labelId).withData(data).create();
    }

    private TextView add(LinearLayout layout, int labelId, String data) {
        View v = inflater.new Builder(layout, R.layout.select_entry_simple).withLabel(labelId)
                .withData(data).create();
        return (TextView)v.findViewById(R.id.data);
    }

    private void add(LinearLayout layout, int labelId, String data, String pictureFileName) {
        View v = inflater.new PictureBuilder(layout)
                .withPicture(context, pictureFileName)
                .withLabel(labelId)
                .withData(data)
                .create();
        v.setClickable(false);
        v.setFocusable(false);
        v.setFocusableInTouchMode(false);
        ImageView pictureView = v.findViewById(R.id.picture);
        pictureView.setTag(pictureFileName);
    }

    private LinearLayout add(LinearLayout layout, String label, String data) {
        return (LinearLayout) inflater.new Builder(layout, R.layout.select_entry_simple).withLabel(label)
                .withData(data).create();
    }


    /**
     * The same colour, lifted enough to be read on the dark title bar.
     * <p>
     * Two of the five states are dark by nature - the settled green and the
     * grey of one not yet checked - and written straight onto a near-black bar
     * they were a word one had to guess at. Lightened only as far as they need:
     * the colours stay recognisably the ones on the rows below.
     */
    private static int readableOnDark(int colour) {
        int r = (colour >> 16) & 0xFF;
        int g = (colour >> 8) & 0xFF;
        int b = colour & 0xFF;
        double light = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
        if (light >= 0.55) {
            return colour;
        }
        double lift = (0.55 - light) * 1.4;
        r = (int) Math.min(255, r + (255 - r) * lift);
        g = (int) Math.min(255, g + (255 - g) * lift);
        b = (int) Math.min(255, b + (255 - b) * lift);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

}
