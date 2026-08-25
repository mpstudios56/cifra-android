package io.github.mpstudios56.cifra.model;

/**
 * Where the currency mark goes, and whether a space goes with it.
 * <p>
 * Four arrangements, because there is no agreement: 1.234,56 EUR here, $1,234.56
 * there. Which one is used is a setting of the currency itself, so the same
 * account can be written the way its own country writes it.
 * <p>
 * The two letters name the arrangement: L or R for the side the mark goes on,
 * and an S for a space between it and the figure.
 */
public enum SymbolFormat {

    /** 1.234,56 EUR */
    RS {
        @Override
        public void appendSymbol(StringBuilder amount, String symbol) {
            amount.append(" ").append(symbol);
        }
    },
    /** 1.234,56EUR */
    R {
        @Override
        public void appendSymbol(StringBuilder amount, String symbol) {
            amount.append(symbol);
        }
    },
    /** EUR 1.234,56 */
    LS {
        @Override
        public void appendSymbol(StringBuilder amount, String symbol) {
            int where = afterTheSign(amount);
            amount.insert(where, " ").insert(where, symbol);
        }
    },
    /** EUR1.234,56 */
    L {
        @Override
        public void appendSymbol(StringBuilder amount, String symbol) {
            amount.insert(afterTheSign(amount), symbol);
        }
    };

    /**
     * Where a mark written on the left has to start.
     * <p>
     * After the plus or the minus, never before it: -EUR 20 reads as an amount
     * owed, EUR -20 reads as a puzzle.
     */
    private static int afterTheSign(StringBuilder amount) {
        if (amount.length() == 0) {
            return 0;
        }
        char first = amount.charAt(0);
        return first == '+' || first == '-' ? 1 : 0;
    }

    public abstract void appendSymbol(StringBuilder amount, String symbol);
}
