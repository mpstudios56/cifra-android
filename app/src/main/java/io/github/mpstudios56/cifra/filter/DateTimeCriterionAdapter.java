package io.github.mpstudios56.cifra.filter;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;

/**
 * Writes a period down.
 * <p>
 * Four things in a row: the number 1, which says "this is a period and not an
 * ordinary condition", then the period's name and its two moments. The name is
 * the part that matters - without it "this month" would come back from the
 * settings as the fortnight it happened to be when it was written.
 */
public class DateTimeCriterionAdapter implements JsonSerializer<DateTimeCriterion> {

    @Override
    public JsonElement serialize(DateTimeCriterion source, Type type, JsonSerializationContext context) {
        JsonArray written = new JsonArray();
        written.add(1);
        written.add(source.getPeriod().type.name());
        written.add(source.getPeriod().start);
        written.add(source.getPeriod().end);
        return written;
    }
}
