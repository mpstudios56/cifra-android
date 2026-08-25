package io.github.mpstudios56.cifra.utils;

import io.github.mpstudios56.cifra.db.DatabaseAdapter;

/**
 * Puts the figures back in agreement with the movements.
 * <p>
 * Balances and running totals are not worked out afresh each time they are
 * shown - that would mean adding up years of movements to draw one row - they
 * are kept and adjusted as movements come and go. Anything that puts movements
 * in by another road, a restore or an import, can leave those kept figures
 * behind, and then a balance disagrees with the sum of its own rows.
 * <p>
 * This throws the kept figures away and works them out again from the movements
 * themselves. Nothing a person wrote is touched.
 * <p>
 * The order matters: the entries the app relies on have to be back before
 * anything is counted, and the balances have to be right before the running
 * totals are built on top of them.
 */
public class IntegrityFix {

    private final DatabaseAdapter db;

    public IntegrityFix(DatabaseAdapter db) {
        this.db = db;
    }

    public void fix() {
        db.restoreSystemEntities();
        db.recalculateAccountsBalances();
        db.rebuildRunningBalances();
        db.updateSplitParentAccountId();
    }
}
