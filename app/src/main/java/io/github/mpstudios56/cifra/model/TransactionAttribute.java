package io.github.mpstudios56.cifra.model;

import android.content.ContentValues;
import android.database.Cursor;

import io.github.mpstudios56.cifra.db.DatabaseHelper.TransactionAttributeColumns;

/**
 * One extra field written on one movement.
 * <p>
 * Categories can ask for particulars of their own - a car repair for the
 * mileage, a bill for the meter reading - and this is one such answer: which
 * question, on which movement, and what was written.
 * <p>
 * Read and written by hand rather than through the usual mapping, because it
 * belongs to a table with no identity of its own: what names a row is the pair
 * of the movement and the question.
 */
public class TransactionAttribute {

    public long attributeId;
    public long transactionId;
    public String value;

    public static TransactionAttribute fromCursor(Cursor c) {
        TransactionAttribute answer = new TransactionAttribute();
        answer.attributeId = c.getLong(TransactionAttributeColumns.Indicies.ATTRIBUTE_ID);
        answer.transactionId = c.getLong(TransactionAttributeColumns.Indicies.TRANSACTION_ID);
        answer.value = c.getString(TransactionAttributeColumns.Indicies.VALUE);
        return answer;
    }

    public ContentValues toValues() {
        ContentValues values = new ContentValues();
        values.put(TransactionAttributeColumns.TRANSACTION_ID, transactionId);
        values.put(TransactionAttributeColumns.ATTRIBUTE_ID, attributeId);
        values.put(TransactionAttributeColumns.VALUE, value);
        return values;
    }
}
