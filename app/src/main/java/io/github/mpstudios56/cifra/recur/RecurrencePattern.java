package io.github.mpstudios56.cifra.recur;

import com.google.ical.values.Frequency;
import com.google.ical.values.RRule;
import com.google.ical.values.Weekday;
import com.google.ical.values.WeekdayNum;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import io.github.mpstudios56.cifra.recur.RecurrenceViewFactory.DayOfWeek;
import io.github.mpstudios56.cifra.recur.RecurrenceViewFactory.MonthlyPattern;
import io.github.mpstudios56.cifra.recur.RecurrenceViewFactory.SpecificDayPostfix;
import io.github.mpstudios56.cifra.recur.RecurrenceViewFactory.SpecificDayPrefix;

/**
 * How often a movement comes back, and on which days.
 * <p>
 * Two things: the kind of repetition - daily, weekly, monthly - and the answers
 * given on that kind's panel, kept as the one line of text that panel produced.
 * Turning the two into the rule the calendar library understands is the whole
 * of the work here.
 */
public class RecurrencePattern {

    /** Monday to Friday, for "the first working day of the month". */
    private static final List<WeekdayNum> WORKING_DAYS = weekdays(
            Weekday.MO, Weekday.TU, Weekday.WE, Weekday.TH, Weekday.FR);
    /** Saturday and Sunday, for "the last weekend day of the month". */
    private static final List<WeekdayNum> WEEKEND_DAYS = weekdays(Weekday.SU, Weekday.SA);

    private static List<WeekdayNum> weekdays(Weekday... days) {
        List<WeekdayNum> list = new ArrayList<>();
        for (Weekday day : days) {
            list.add(new WeekdayNum(0, day));
        }
        return Collections.unmodifiableList(list);
    }

    public final RecurrenceFrequency frequency;
    /** The answers from that kind's panel, as one line. */
    public final String params;

    public RecurrencePattern(RecurrenceFrequency frequency, String params) {
        this.frequency = frequency;
        this.params = params;
    }

    public static RecurrencePattern noRecur() {
        return new RecurrencePattern(RecurrenceFrequency.NO_RECUR, null);
    }

    public static RecurrencePattern empty(RecurrenceFrequency frequency) {
        return new RecurrencePattern(frequency, null);
    }

    public static RecurrencePattern parse(String written) {
        String[] parts = written.split(":");
        return new RecurrencePattern(RecurrenceFrequency.valueOf(parts[0]), parts[1]);
    }

    public Object stateToString() {
        return frequency.name() + ":" + params;
    }

    /** Writes this pattern into the rule the calendar library will walk. */
    public void updateRRule(RRule rule) {
        HashMap<String, String> answers = RecurrenceViewFactory.parseState(params);
        rule.setInterval(Integer.parseInt(answers.get(RecurrenceViewFactory.P_INTERVAL)));
        switch (frequency) {
            case DAILY:
                rule.setFreq(Frequency.DAILY);
                break;
            case WEEKLY:
                rule.setFreq(Frequency.WEEKLY);
                rule.setByDay(chosenDays(answers.get(RecurrenceViewFactory.P_DAYS)));
                break;
            case MONTHLY:
                rule.setFreq(Frequency.MONTHLY);
                writeMonthly(rule, answers);
                break;
            default:
                break;
        }
    }

    /** The days of the week ticked on the weekly panel. */
    private static List<WeekdayNum> chosenDays(String written) {
        List<WeekdayNum> days = new ArrayList<>();
        for (String one : written.split(",")) {
            days.add(new WeekdayNum(0, Weekday.valueOf(DayOfWeek.valueOf(one).rfcName)));
        }
        return days;
    }

    /**
     * The monthly panel asks in one of two ways: a plain day of the month, or a
     * day picked by its place - the second Tuesday, the last working day.
     */
    private static void writeMonthly(RRule rule, HashMap<String, String> answers) {
        MonthlyPattern kind = MonthlyPattern.valueOf(
                answers.get(RecurrenceViewFactory.P_MONTHLY_PATTERN + "_0"));
        String chosen = answers.get(RecurrenceViewFactory.P_MONTHLY_PATTERN_PARAMS + "_0");
        if (kind == MonthlyPattern.EVERY_NTH_DAY) {
            rule.setByMonthDay(new int[]{Integer.parseInt(chosen)});
            return;
        }
        if (kind != MonthlyPattern.SPECIFIC_DAY) {
            return;
        }
        String[] halves = chosen.split("-");
        SpecificDayPrefix which = SpecificDayPrefix.valueOf(halves[0]);
        SpecificDayPostfix what = SpecificDayPostfix.valueOf(halves[1]);
        // "Last" counts backwards from the end of the month; the rest count
        // forwards, and are one-based where the list is not.
        int place = which == SpecificDayPrefix.LAST ? -1 : which.ordinal() + 1;
        switch (what) {
            case DAY:
                rule.setByMonthDay(new int[]{place});
                break;
            case WEEKDAY:
                rule.setByDay(WORKING_DAYS);
                rule.setBySetPos(new int[]{place});
                break;
            case WEEKEND_DAY:
                rule.setByDay(WEEKEND_DAYS);
                rule.setBySetPos(new int[]{place});
                break;
            default:
                // A named day of the week. The three entries before them in the
                // list are the ones handled above, so they are stepped over.
                Weekday named = Weekday.values()[what.ordinal() - 3];
                rule.setByDay(Collections.singletonList(new WeekdayNum(place, named)));
                break;
        }
    }
}
