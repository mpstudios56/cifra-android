package io.github.mpstudios56.cifra.recur;

import android.app.DatePickerDialog;
import android.content.Context;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.mpstudios56.cifra.R;
import io.github.mpstudios56.cifra.activity.ActivityLayout;
import io.github.mpstudios56.cifra.activity.ActivityLayoutListener;
import io.github.mpstudios56.cifra.activity.RecurrenceActivity;
import io.github.mpstudios56.cifra.datetime.DateUtils;
import io.github.mpstudios56.cifra.model.MultiChoiceItem;
import io.github.mpstudios56.cifra.utils.EnumUtils;
import io.github.mpstudios56.cifra.utils.LocalizableEnum;
import io.github.mpstudios56.cifra.utils.Utils;
import io.github.mpstudios56.cifra.view.NodeInflater;

/**
 * The panels of the repetition screen.
 * <p>
 * Each way of repeating asks for different things - a number of days, a set of
 * weekdays, a day of the month - so each has a panel of its own. This makes the
 * one that matches what has been chosen, and keeps the small vocabulary the
 * panels share: the names under which their answers are written down, and the
 * lists they offer.
 * <p>
 * Every panel writes its answers as one line of the form
 * {@code NAME:key@value#key@value#}, which is what ends up in the movement's
 * row in the database.
 */
public class RecurrenceViewFactory {

    /** The names the answers are written under. */
    public static final String P_INTERVAL = "interval";
    public static final String P_DAYS = "days";
    public static final String P_COUNT = "count";
    public static final String P_DATE = "date";
    public static final String P_MONTHLY_PATTERN = "monthly_pattern";
    public static final String P_MONTHLY_PATTERN_PARAMS = "monthly_pattern_params";

    /** Separates one answer from the next, and a name from its value. */
    private static final String BETWEEN_ANSWERS = "#";
    private static final String BETWEEN_NAME_AND_VALUE = "@";

    /** Where the panels for choosing a day by its place are numbered from. */
    private static final int PLACE_PICKER = 200;
    /** Where the panels for choosing the kind of monthly rule are numbered from. */
    private static final int KIND_PICKER = 100;

    private final RecurrenceActivity activity;

    public RecurrenceViewFactory(RecurrenceActivity activity) {
        this.activity = activity;
    }

    /** The panel that asks what a repetition of this kind needs to know. */
    public RecurrenceView create(RecurrencePattern pattern) {
        switch (pattern.frequency) {
            case DAILY:
                return new DailyView();
            case WEEKLY:
                return new WeeklyView();
            case MONTHLY:
                return new MonthlyView(pattern.params);
            case GEEKY:
                return new GeekyView();
            default:
                return null;
        }
    }

    /** The panel that asks when the repetition stops, where it stops at all. */
    public RecurrenceView create(RecurrenceUntil until) {
        switch (until) {
            case EXACTLY_TIMES:
                return new ExactlyTimesView();
            case STOPS_ON_DATE:
                return new StopsOnDateView();
            default:
                return null;
        }
    }

    /** Reads a written line back into the answers it holds. */
    public static HashMap<String, String> parseState(String written) {
        HashMap<String, String> answers = new HashMap<>();
        if (written == null) {
            return answers;
        }
        for (String one : written.split(BETWEEN_ANSWERS)) {
            String[] halves = one.split(BETWEEN_NAME_AND_VALUE);
            if (halves.length == 2) {
                answers.put(halves[0], halves[1]);
            }
        }
        return answers;
    }

    // ------------------------------------------------------ what every panel does

    /**
     * The part every panel has in common: drawing itself into the screen,
     * writing its answers down, and reading them back.
     */
    abstract class AbstractView implements RecurrenceView, ActivityLayoutListener {

        private final LocalizableEnum kind;
        /** Draws the rows - a label with a field or a list beside it. */
        protected final ActivityLayout x;

        AbstractView(LocalizableEnum kind) {
            this.kind = kind;
            LayoutInflater inflater = (LayoutInflater)
                    activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            this.x = new ActivityLayout(new NodeInflater(inflater), this);
        }

        @Override
        public String stateToString() {
            HashMap<String, String> answers = new HashMap<>();
            stateToMap(answers);
            StringBuilder written = new StringBuilder(kind.name()).append(':');
            for (Map.Entry<String, String> answer : answers.entrySet()) {
                written.append(answer.getKey())
                        .append(BETWEEN_NAME_AND_VALUE)
                        .append(answer.getValue())
                        .append(BETWEEN_ANSWERS);
            }
            return written.toString();
        }

        @Override
        public void stateFromString(String written) {
            stateFromMap(parseState(written));
        }

        @Override
        public abstract boolean validateState();

        protected abstract void stateToMap(HashMap<String, String> answers);

        protected abstract void stateFromMap(HashMap<String, String> answers);

        /** Complains on the field and refuses, when it has been left empty. */
        protected boolean insist(EditText field) {
            if (Utils.isEmpty(field)) {
                field.setError(activity.getString(R.string.specify_value));
                return false;
            }
            return true;
        }

        @Override
        public void onSelected(int id, List<? extends MultiChoiceItem> items) {
        }

        @Override
        public void onSelectedId(int id, long selectedId) {
        }

        @Override
        public void onSelectedPos(int id, int selectedPos) {
        }

        @Override
        public void onClick(View v) {
            onClick(v, v.getId());
        }

        protected abstract void onClick(View v, int id);
    }

    // ------------------------------------------------------------------- daily

    /** Every so many days. */
    class DailyView extends AbstractView {

        private final EditText everyDays = numericEditText(activity);

        DailyView() {
            super(RecurrenceFrequency.DAILY);
            everyDays.setText("1");
        }

        @Override
        public void createNodes(LinearLayout layout) {
            removeAllViewsFromParent(everyDays);
            x.addEditNode(layout, R.string.recur_interval_every_x_day, everyDays);
        }

        @Override
        protected void onClick(View v, int id) {
        }

        @Override
        public boolean validateState() {
            return insist(everyDays);
        }

        @Override
        protected void stateToMap(HashMap<String, String> answers) {
            answers.put(P_INTERVAL, everyDays.getText().toString());
        }

        @Override
        protected void stateFromMap(HashMap<String, String> answers) {
            everyDays.setText(answers.get(P_INTERVAL));
        }
    }

    // ------------------------------------------------------------------ weekly

    /** The days of the week, each with its box on screen and its name in the rule. */
    enum DayOfWeek implements LocalizableEnum {
        MON(R.id.dayMon, R.string.day_mon, "MO"),
        TUE(R.id.dayTue, R.string.day_tue, "TU"),
        WED(R.id.dayWed, R.string.day_wed, "WE"),
        THR(R.id.dayThr, R.string.day_thr, "TH"),
        FRI(R.id.dayFri, R.string.day_fri, "FR"),
        SAT(R.id.daySat, R.string.day_sat, "SA"),
        SUN(R.id.daySun, R.string.day_sun, "SU");

        public final int checkboxId;
        public final int titleId;
        /** How the calendar standard names this day. */
        public final String rfcName;

        DayOfWeek(int checkboxId, int titleId, String rfcName) {
            this.checkboxId = checkboxId;
            this.titleId = titleId;
            this.rfcName = rfcName;
        }

        @Override
        public int getTitleId() {
            return titleId;
        }
    }

    /** One day of the week as a line that can be ticked. */
    class DayOfWeekItem implements MultiChoiceItem {

        public final DayOfWeek d;
        private final String title;
        private boolean checked;

        DayOfWeekItem(DayOfWeek d) {
            this.d = d;
            this.title = activity.getString(d.titleId);
        }

        @Override
        public long getId() {
            return d.checkboxId;
        }

        @Override
        public String getTitle() {
            return title;
        }

        @Override
        public boolean isChecked() {
            return checked;
        }

        @Override
        public void setChecked(boolean checked) {
            this.checked = checked;
        }
    }

    /** Every so many weeks, on the days ticked. */
    class WeeklyView extends AbstractView {

        private final EditText everyWeeks = numericEditText(activity);
        private TextView chosenDaysText;
        private final EnumSet<DayOfWeek> chosenDays = EnumSet.allOf(DayOfWeek.class);

        WeeklyView() {
            super(RecurrenceFrequency.WEEKLY);
            everyWeeks.setText("1");
            // Monday to Friday to begin with: most things that repeat weekly
            // are working days.
            chosenDays.remove(DayOfWeek.SAT);
            chosenDays.remove(DayOfWeek.SUN);
        }

        private String daysToString() {
            StringBuilder said = new StringBuilder();
            for (DayOfWeek d : chosenDays) {
                if (said.length() > 0) {
                    said.append(", ");
                }
                said.append(activity.getString(d.titleId));
            }
            return said.length() > 0 ? said.toString() : activity.getString(R.string.no_recur);
        }

        @Override
        public void createNodes(LinearLayout layout) {
            removeAllViewsFromParent(everyWeeks);
            x.addEditNode(layout, R.string.recur_interval_every_x_week, everyWeeks);
            chosenDaysText = x.addListNode(layout, R.id.recurrence_pattern,
                    R.string.recurrence_weekly_days, daysToString());
        }

        @Override
        protected void onClick(View v, int id) {
            if (id != R.id.recurrence_pattern) {
                return;
            }
            List<MultiChoiceItem> items = new ArrayList<>();
            for (DayOfWeek d : DayOfWeek.values()) {
                DayOfWeekItem item = new DayOfWeekItem(d);
                item.setChecked(chosenDays.contains(d));
                items.add(item);
            }
            x.selectMultiChoice(activity, R.id.recurrence_pattern,
                    R.string.recur_interval_every_x_week, items);
        }

        @Override
        public void onSelected(int id, List<? extends MultiChoiceItem> items) {
            if (id != R.id.recurrence_pattern) {
                return;
            }
            chosenDays.clear();
            for (MultiChoiceItem item : items) {
                if (item.isChecked()) {
                    chosenDays.add(((DayOfWeekItem) item).d);
                }
            }
            chosenDaysText.setText(daysToString());
            chosenDaysText.setError(null);
        }

        @Override
        public boolean validateState() {
            if (!insist(everyWeeks)) {
                return false;
            }
            if (chosenDays.isEmpty()) {
                chosenDaysText.setError(activity.getString(R.string.specify_value));
                return false;
            }
            return true;
        }

        @Override
        protected void stateToMap(HashMap<String, String> answers) {
            answers.put(P_INTERVAL, everyWeeks.getText().toString());
            StringBuilder days = new StringBuilder();
            for (DayOfWeek d : chosenDays) {
                if (days.length() > 0) {
                    days.append(',');
                }
                days.append(d.name());
            }
            answers.put(P_DAYS, days.toString());
        }

        @Override
        protected void stateFromMap(HashMap<String, String> answers) {
            everyWeeks.setText(answers.get(P_INTERVAL));
            chosenDays.clear();
            for (String d : answers.get(P_DAYS).split(",")) {
                chosenDays.add(DayOfWeek.valueOf(d));
            }
        }
    }

    // ----------------------------------------------------------------- monthly

    /** The two ways a monthly repetition can name its day. */
    public enum MonthlyPattern implements LocalizableEnum {
        EVERY_NTH_DAY(R.string.recurrence_monthly_every_nth_day),
        SPECIFIC_DAY(R.string.recurrence_monthly_specific_day);

        private final int titleId;

        MonthlyPattern(int titleId) {
            this.titleId = titleId;
        }

        @Override
        public int getTitleId() {
            return titleId;
        }
    }

    /** Which one: the first, the second... or the last. */
    public enum SpecificDayPrefix implements LocalizableEnum {
        FIRST(R.string.first),
        SECOND(R.string.second),
        THIRD(R.string.third),
        FOURTH(R.string.fourth),
        LAST(R.string.last);

        private final int titleId;

        SpecificDayPrefix(int titleId) {
            this.titleId = titleId;
        }

        @Override
        public int getTitleId() {
            return titleId;
        }
    }

    /**
     * Of what: a day, a working day, a weekend day, or a named day of the week.
     * <p>
     * The order matters - the three general ones come first, and the seven
     * named days follow in the order the calendar standard uses.
     */
    public enum SpecificDayPostfix implements LocalizableEnum {
        DAY(R.string.day),
        WEEKDAY(R.string.weekday),
        WEEKEND_DAY(R.string.weekend_day),
        SUNDAY(R.string.sunday),
        MONDAY(R.string.monday),
        TUESDAY(R.string.tuesday),
        WEDNESDAY(R.string.wednesday),
        THURSDAY(R.string.thursday),
        FRIDAY(R.string.friday),
        SATURDAY(R.string.saturday);

        private final int titleId;

        SpecificDayPostfix(int titleId) {
            this.titleId = titleId;
        }

        @Override
        public int getTitleId() {
            return titleId;
        }
    }

    /** Titles used when a panel asks about two days in the same month. */
    private static final int[] DAY_TITLES = {
            R.string.recur_interval_semi_monthly_1,
            R.string.recur_interval_semi_monthly_2};

    /**
     * Every so many months, on a day named in one of two ways - and possibly on
     * more than one day of the same month.
     */
    abstract class AbstractMonthlyView extends AbstractView {

        /** How many days of the month this panel asks about. */
        private int days;

        public final MonthlyPattern[] pattern;
        public final SpecificDayPrefix[] prefix;
        public final SpecificDayPostfix[] postfix;

        private final EditText everyMonths = numericEditText(activity);
        private final EditText[] dayOfMonth;
        private final TextView[] patternText;
        private final TextView[] specificDayText;

        AbstractMonthlyView(RecurrenceFrequency frequency, int days) {
            super(frequency);
            this.days = days;
            pattern = new MonthlyPattern[days];
            prefix = new SpecificDayPrefix[days];
            postfix = new SpecificDayPostfix[days];
            dayOfMonth = new EditText[days];
            for (int i = 0; i < days; i++) {
                pattern[i] = MonthlyPattern.EVERY_NTH_DAY;
                prefix[i] = SpecificDayPrefix.FIRST;
                postfix[i] = SpecificDayPostfix.DAY;
                dayOfMonth[i] = numericEditText(activity);
            }
            patternText = new TextView[days];
            specificDayText = new TextView[days];
            everyMonths.setText("1");
        }

        @Override
        public void createNodes(LinearLayout layout) {
            for (int i = 0; i < days; i++) {
                if (days > 1) {
                    x.addTitleNodeNoDivider(layout, DAY_TITLES[i]);
                }
                patternText[i] = x.addListNode(layout, KIND_PICKER + i,
                        R.string.recurrence_monthly_pattern,
                        activity.getString(pattern[i].titleId));
                if (pattern[i] == MonthlyPattern.EVERY_NTH_DAY) {
                    removeAllViewsFromParent(dayOfMonth[i]);
                    x.addEditNode(layout, R.string.recurrence_monthly_every_nth_day, dayOfMonth[i]);
                    dayOfMonth[i].setText("15");
                } else {
                    specificDayText[i] = x.addListNode(layout, PLACE_PICKER + i,
                            R.string.recurrence_monthly_specific_day, specificDay(i));
                }
            }
            removeAllViewsFromParent(everyMonths);
            x.addEditNode(layout, R.string.recur_interval_every_x_month, everyMonths);
        }

        private String specificDay(int i) {
            return activity.getString(prefix[i].titleId) + " " + activity.getString(postfix[i].titleId);
        }

        @Override
        protected void onClick(View v, int id) {
            if (id >= PLACE_PICKER) {
                offerPlaces(id, id - PLACE_PICKER);
            } else {
                offerKinds(id, id - KIND_PICKER);
            }
        }

        /** "First day", "second Tuesday", "last working day" - every combination. */
        private void offerPlaces(int id, int which) {
            String[] places = EnumUtils.getLocalizedValues(activity, SpecificDayPrefix.values());
            String[] kinds = EnumUtils.getLocalizedValues(activity, SpecificDayPostfix.values());
            String[] items = new String[places.length * kinds.length];
            for (int i = 0; i < places.length; i++) {
                for (int j = 0; j < kinds.length; j++) {
                    items[i * kinds.length + j] = places[i] + " " + kinds[j];
                }
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(activity,
                    android.R.layout.simple_spinner_dropdown_item, items);
            int standingAt = prefix[which].ordinal() * kinds.length + postfix[which].ordinal();
            x.selectPosition(activity, id, R.string.recurrence_period, adapter, standingAt);
        }

        private void offerKinds(int id, int which) {
            ArrayAdapter<String> adapter =
                    EnumUtils.createDropDownAdapter(activity, MonthlyPattern.values());
            x.selectPosition(activity, id, R.string.recurrence_period, adapter,
                    pattern[which].ordinal());
        }

        @Override
        public void onSelectedPos(int id, int selectedPos) {
            if (id >= PLACE_PICKER) {
                int which = id - PLACE_PICKER;
                int kinds = SpecificDayPostfix.values().length;
                prefix[which] = SpecificDayPrefix.values()[selectedPos / kinds];
                postfix[which] = SpecificDayPostfix.values()[selectedPos % kinds];
                specificDayText[which].setText(specificDay(which));
            } else {
                int which = id - KIND_PICKER;
                pattern[which] = MonthlyPattern.values()[selectedPos];
                // The other kind asks for something else entirely, so the panel
                // is drawn again from scratch.
                activity.createNodes();
            }
        }

        @Override
        public boolean validateState() {
            for (int i = 0; i < days; i++) {
                if (pattern[i] == MonthlyPattern.EVERY_NTH_DAY && !insist(dayOfMonth[i])) {
                    return false;
                }
            }
            return insist(everyMonths);
        }

        @Override
        protected void stateToMap(HashMap<String, String> answers) {
            answers.put(P_INTERVAL, everyMonths.getText().toString());
            answers.put(P_COUNT, String.valueOf(days));
            for (int i = 0; i < days; i++) {
                String which = "_" + i;
                answers.put(P_MONTHLY_PATTERN + which, pattern[i].name());
                answers.put(P_MONTHLY_PATTERN_PARAMS + which,
                        pattern[i] == MonthlyPattern.EVERY_NTH_DAY
                                ? dayOfMonth[i].getText().toString()
                                : prefix[i].name() + "-" + postfix[i].name());
            }
        }

        @Override
        protected void stateFromMap(HashMap<String, String> answers) {
            everyMonths.setText(answers.get(P_INTERVAL));
            days = Integer.parseInt(answers.get(P_COUNT));
            for (int i = 0; i < days; i++) {
                String which = "_" + i;
                pattern[i] = MonthlyPattern.valueOf(answers.get(P_MONTHLY_PATTERN + which));
                patternText[i].setText(pattern[i].titleId);
                String chosen = answers.get(P_MONTHLY_PATTERN_PARAMS + which);
                if (pattern[i] == MonthlyPattern.EVERY_NTH_DAY) {
                    dayOfMonth[i].setText(chosen);
                } else {
                    String[] halves = chosen.split("-");
                    prefix[i] = SpecificDayPrefix.valueOf(halves[0]);
                    postfix[i] = SpecificDayPostfix.valueOf(halves[1]);
                    specificDayText[i].setText(specificDay(i));
                }
            }
        }
    }

    /** One day a month. */
    class MonthlyView extends AbstractMonthlyView {

        MonthlyView(String written) {
            super(RecurrenceFrequency.MONTHLY, 1);
            HashMap<String, String> answers = parseState(written);
            if (!answers.isEmpty()) {
                pattern[0] = MonthlyPattern.valueOf(answers.get(P_MONTHLY_PATTERN + "_0"));
            }
        }
    }

    // -------------------------------------------------------- the way out

    /**
     * The rule written out by hand, for everything the panels above cannot say.
     */
    class GeekyView extends AbstractView {

        private final EditText rule = new EditText(activity);

        GeekyView() {
            super(RecurrenceFrequency.GEEKY);
            rule.setText("FREQ=MONTHLY;BYDAY=FR;BYMONTHDAY=13");
            rule.setMinLines(3);
            rule.setMaxLines(5);
        }

        @Override
        public void createNodes(LinearLayout layout) {
            removeAllViewsFromParent(rule);
            x.addEditNode(layout, R.string.recur_rrule, rule);
        }

        @Override
        protected void onClick(View v, int id) {
        }

        @Override
        public boolean validateState() {
            return insist(rule);
        }

        @Override
        protected void stateToMap(HashMap<String, String> answers) {
            answers.put(P_INTERVAL, rule.getText().toString().toUpperCase());
        }

        @Override
        protected void stateFromMap(HashMap<String, String> answers) {
            String written = answers.get(P_INTERVAL);
            rule.setText(written != null ? written.toUpperCase() : "");
        }
    }

    // ------------------------------------------------------- when it stops

    /** After so many times. */
    class ExactlyTimesView extends AbstractView {

        private final EditText howManyTimes = numericEditText(activity);

        ExactlyTimesView() {
            super(RecurrenceUntil.EXACTLY_TIMES);
            howManyTimes.setText("10");
        }

        @Override
        public void createNodes(LinearLayout layout) {
            removeAllViewsFromParent(howManyTimes);
            x.addEditNode(layout, R.string.recur_exactly_n_times, howManyTimes);
        }

        @Override
        protected void onClick(View v, int id) {
        }

        @Override
        public boolean validateState() {
            return insist(howManyTimes);
        }

        @Override
        protected void stateToMap(HashMap<String, String> answers) {
            answers.put(P_COUNT, howManyTimes.getText().toString());
        }

        @Override
        protected void stateFromMap(HashMap<String, String> answers) {
            howManyTimes.setText(answers.get(P_COUNT));
        }
    }

    /** On a chosen day. */
    class StopsOnDateView extends AbstractView {

        private TextView lastDayText;
        private final Calendar lastDay = Calendar.getInstance();

        StopsOnDateView() {
            super(RecurrenceUntil.STOPS_ON_DATE);
            // Six months out, so the field opens on something plausible rather
            // than on today, which would mean stopping at once.
            DateUtils.startOfDay(lastDay);
            lastDay.add(Calendar.MONTH, 6);
        }

        @Override
        public void createNodes(LinearLayout layout) {
            lastDayText = x.addInfoNode(layout, R.id.date, R.string.recur_repeat_stops_on, written());
        }

        private String written() {
            return DateUtils.getMediumDateFormat(activity).format(lastDay.getTime());
        }

        @Override
        protected void onClick(View v, int id) {
            new DatePickerDialog(activity,
                    (picker, year, month, day) -> {
                        lastDay.set(Calendar.YEAR, year);
                        lastDay.set(Calendar.MONTH, month);
                        lastDay.set(Calendar.DAY_OF_MONTH, day);
                        lastDayText.setText(written());
                    },
                    lastDay.get(Calendar.YEAR),
                    lastDay.get(Calendar.MONTH),
                    lastDay.get(Calendar.DAY_OF_MONTH)).show();
        }

        @Override
        public boolean validateState() {
            return true;
        }

        @Override
        protected void stateToMap(HashMap<String, String> answers) {
            DateUtils.startOfDay(lastDay);
            answers.put(P_DATE, DateUtils.FORMAT_DATE_RFC_2445.format(lastDay.getTime()));
        }

        @Override
        protected void stateFromMap(HashMap<String, String> answers) {
            String chosen = answers.get(P_DATE);
            Date date;
            try {
                date = DateUtils.FORMAT_DATE_RFC_2445.parse(chosen);
            } catch (ParseException unreadable) {
                throw new IllegalArgumentException(chosen);
            }
            lastDay.setTime(date);
            DateUtils.startOfDay(lastDay);
            lastDayText.setText(written());
        }
    }

    // ----------------------------------------------------------------- helpers

    /** A field that only takes figures. */
    private static EditText numericEditText(Context context) {
        EditText field = new EditText(context);
        field.setInputType(InputType.TYPE_CLASS_NUMBER);
        return field;
    }

    /**
     * Takes a field out of wherever it was last put.
     * <p>
     * The panels are drawn again whenever a choice changes, and the same field
     * objects are reused; a view still attached to its old row cannot be added
     * to a new one.
     */
    public void removeAllViewsFromParent(View v) {
        if (v.getParent() != null) {
            ((ViewGroup) v.getParent()).removeAllViews();
        }
    }
}
