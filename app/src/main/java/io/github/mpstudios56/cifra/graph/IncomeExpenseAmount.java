package io.github.mpstudios56.cifra.graph;

import java.math.BigDecimal;

import io.github.mpstudios56.cifra.report.IncomeExpense;

/**
 * What came in and what went out, kept apart until asked otherwise.
 * <p>
 * A report row needs both halves separately - two bars, one each way - and
 * their difference only when the row is asked to speak as a single figure. So
 * they are added up as they arrive and brought together, or set aside, at the
 * end.
 */
public class IncomeExpenseAmount {

    public BigDecimal income = BigDecimal.ZERO;
    public BigDecimal expense = BigDecimal.ZERO;

    /**
     * Adds one movement to whichever half it belongs to.
     *
     * @param forceIncome counts the amount as money in whatever its sign. A
     *                    transfer seen from the account receiving it is money
     *                    arriving even when it is written down as a negative.
     */
    public void add(BigDecimal amount, boolean forceIncome) {
        if (forceIncome || amount.longValue() > 0) {
            income = income.add(amount);
        } else {
            expense = expense.add(amount);
        }
    }

    /** The larger of the two halves, whichever way it goes: the bar to scale by. */
    public long max() {
        return Math.max(Math.abs(income.longValue()), Math.abs(expense.longValue()));
    }

    /** What is left when the two are set against each other. */
    public long balance() {
        return income.longValue() + expense.longValue();
    }

    /**
     * Keeps only the half the report was asked for.
     * <p>
     * Asked for a summary, the two are added together and the answer is put in
     * income: what remains is one figure, and a single bar draws it.
     */
    public void filter(IncomeExpense incomeExpense) {
        switch (incomeExpense) {
            case INCOME:
                expense = BigDecimal.ZERO;
                break;
            case EXPENSE:
                income = BigDecimal.ZERO;
                break;
            case SUMMARY:
                income = income.add(expense);
                expense = BigDecimal.ZERO;
                break;
        }
    }
}
