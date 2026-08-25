package io.github.mpstudios56.cifra.model;

import android.content.Context;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import io.github.mpstudios56.cifra.R;

/**
 * One extra field on one movement, with the question it answers.
 * <p>
 * The answers are kept in one table and the questions in another; this is the
 * two read together, so that showing a movement does not mean asking after the
 * name of every field on it one at a time.
 * <p>
 * Two columns name a row here, the movement and the field: neither is enough on
 * its own, since a movement has several fields and a field is used by many
 * movements.
 */
@Entity
@Table(name = "V_TRANSACTION_ATTRIBUTES")
public class TransactionAttributeInfo {

    @Id
    @Column(name = "_id")
    public long transactionId;

    @Id
    @Column(name = "attribute_id")
    public long attributeId;

    @Column(name = "attribute_type")
    public int type;

    @Column(name = "attribute_name")
    public String name;

    @Column(name = "attribute_value")
    public String value;

    /** For a field offering a choice: the words to choose between, separated by a semicolon. */
    @Column(name = "attribute_list_values")
    public String listValues;

    /**
     * The answer as it should be read.
     * <p>
     * A tick is stored as true or false, which is not what anybody wants to
     * read on a movement. If the field was given its own two words - "Paid;Not
     * paid" - those are used; otherwise it falls back to the app's own yes and
     * no, in the reader's language.
     */
    public String getValue(Context context) {
        if (type != Attribute.TYPE_CHECKBOX) {
            return value;
        }
        boolean ticked = Boolean.parseBoolean(value);
        String[] itsOwnWords = listValues != null ? listValues.split(";") : null;
        if (itsOwnWords != null && itsOwnWords.length > 1) {
            return itsOwnWords[ticked ? 0 : 1];
        }
        return context.getString(ticked ? R.string.checkbox_values_yes : R.string.checkbox_values_no);
    }
}
