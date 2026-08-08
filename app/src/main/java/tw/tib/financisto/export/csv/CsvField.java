/*
 * This program is made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */
package tw.tib.financisto.export.csv;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * What a column of somebody else's file can turn out to be.
 * <p>
 * The synonyms are in several languages because they have to be: every app
 * tested so far writes its headers in whatever language it is running in. An
 * Italian phone exports <em>Data, Importo, Conto</em>, an English one
 * <em>Date, Amount, Account</em>, and it is the same app and the same version.
 * <p>
 * The words of {@link #INCOME} and {@link #EXPENSE} do double duty: they are also
 * what a Type column <em>says</em>, line by line, in the apps that spell out
 * whether money came in or went out instead of using a sign.
 */
public enum CsvField {

    DATE(true, "date", "data", "fecha", "datum", "date de", "fechaoperacion"),
    TIME(false, "time", "ora", "orario", "hora", "uhrzeit", "heure"),
    /** One column carrying the sign. */
    AMOUNT(true, "amount", "importo", "monto", "importe", "betrag", "montant", "value", "valore"),
    /** Two columns instead, with a zero in the one that does not apply. */
    INCOME(false, "income", "entrate", "entrata", "reddito", "ingreso", "ingresos", "einnahme", "recette", "deposit", "credit"),
    EXPENSE(false, "expense", "spesa", "spese", "uscite", "gasto", "ausgabe", "depense", "withdrawal", "debit"),
    /** Expense / Income / Transfer, when the app says it in words. */
    TYPE(false, "type", "tipo", "art", "typ"),
    ACCOUNT(false, "account", "conto", "cuenta", "konto", "compte"),
    TRANSFER_ACCOUNT(false, "transfer account", "conto di destinazione", "to account", "a conto"),
    CATEGORY(false, "category", "categoria", "categoría", "kategorie", "categorie"),
    PARENT_CATEGORY(false, "parent category", "gruppo di categorie", "categoria madre",
            "categoria principale", "hauptkategorie", "categorie parente"),
    PAYEE(false, "payee", "beneficiario", "controparte", "name", "nome", "item or payee",
            "empfanger", "beneficiaire", "item"),
    NOTE(false, "note", "notes", "nota", "note", "memo", "notiz", "beschreibung", "description"),
    CURRENCY(false, "currency", "valuta", "moneda", "wahrung", "devise");

    /** Without these nothing can be imported at all. */
    public final boolean required;
    private final List<String> synonyms;

    CsvField(boolean required, String... synonyms) {
        this.required = required;
        this.synonyms = Arrays.asList(synonyms);
    }

    /**
     * How well a column heading matches this field: 2 for the same word, 1 for a
     * heading that starts with one of them, 0 for no.
     * <p>
     * Graded rather than yes-or-no because headings carry extra words - "Importo
     * originario", "Item or Payee" - and the closest match should win rather than
     * whichever field happens to be tried first.
     */
    public int score(String heading) {
        String h = normalise(heading);
        if (h.isEmpty()) {
            return 0;
        }
        for (String synonym : synonyms) {
            String s = normalise(synonym);
            if (h.equals(s)) {
                return 2;
            }
        }
        for (String synonym : synonyms) {
            String s = normalise(synonym);
            if (s.length() >= 4 && (h.startsWith(s) || h.contains(" " + s))) {
                return 1;
            }
        }
        return 0;
    }

    /** Lower case, without accents or punctuation, so "Wahrung" matches "Währung". */
    static String normalise(String text) {
        if (text == null) {
            return "";
        }
        String s = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return s;
    }
}
