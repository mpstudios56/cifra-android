package io.github.mpstudios56.cifra.utils;

import java.util.List;

import io.github.mpstudios56.cifra.model.Transaction;

/**
 * Shares out what is left over when a movement is split into parts.
 * <p>
 * A hundred euros split three ways is thirty-three and a third each, and money
 * has no thirds: three parts of 33.33 leave a cent unaccounted for. A cent lost
 * on every split is a balance that drifts, so the remainder is always given to
 * somebody rather than dropped.
 * <p>
 * Amounts are whole cents throughout, which is why this is done in whole
 * numbers and not with fractions.
 */
public class SplitAdjuster {

    private SplitAdjuster() {
    }

    /**
     * The same to each part, and the odd cents to the last few.
     * <p>
     * Whoever comes last takes the remainder - a cent each, from the end
     * backwards - so no part is more than a cent away from its fair share.
     */
    public static void adjustEvenly(List<Transaction> splits, long unsplitAmount) {
        if (noSplits(splits)) {
            return;
        }
        int parts = splits.size();
        long each = unsplitAmount / parts;
        for (Transaction split : splits) {
            split.fromAmount += each;
        }
        long leftOver = unsplitAmount - each * parts;
        if (leftOver == 0) {
            return;
        }
        // Which way the odd cents go depends on the sign: a remainder of -2 is
        // two cents to take away, not two to add.
        int oneCent = leftOver > 0 ? 1 : -1;
        for (int i = parts - 1; i >= parts - oneCent * leftOver; i--) {
            splits.get(i).fromAmount += oneCent;
        }
    }

    /** All of it to the last part: what one does when the parts are not equal. */
    public static void adjustLast(List<Transaction> splits, long unsplitAmount) {
        if (noSplits(splits)) {
            return;
        }
        adjustSplit(splits.get(splits.size() - 1), unsplitAmount);
    }

    public static void adjustSplit(Transaction split, long unsplitAmount) {
        split.fromAmount += unsplitAmount;
    }

    private static boolean noSplits(List<Transaction> splits) {
        return splits == null || splits.isEmpty();
    }
}
