package io.github.mpstudios56.cifra.utils;

import java.util.List;

import io.github.mpstudios56.cifra.model.Total;
import io.github.mpstudios56.cifra.model.TransactionInfo;

/**
 * A list of movements and the sums beneath it, fetched together.
 * <p>
 * The two arrive from the same question and are shown on the same screen, so
 * they travel together: fetching the rows and then asking separately for their
 * totals would read the same movements twice, and leave a moment in which the
 * figure at the top belonged to a different list from the one below it.
 * <p>
 * More than one total, because a list can hold several currencies, and those
 * cannot honestly be added into one figure.
 */
public class TransactionList {

    public final List<TransactionInfo> transactions;
    public final Total[] totals;

    public TransactionList(List<TransactionInfo> transactions, Total[] totals) {
        this.transactions = transactions;
        this.totals = totals;
    }
}
