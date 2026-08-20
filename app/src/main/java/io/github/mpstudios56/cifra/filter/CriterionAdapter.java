package io.github.mpstudios56.cifra.filter;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;

import io.github.mpstudios56.cifra.datetime.PeriodType;

/**
 * Writes a condition down, and reads it back.
 * <p>
 * The shape is a short array whose first place says which kind it is: 0 for an
 * ordinary condition, 1 for a period. Everything the app writes - filters kept
 * in the settings, filters handed from one screen to another - is written in
 * this shape, so it has to keep reading what earlier versions wrote.
 */
public class CriterionAdapter implements JsonSerializer<Criterion>, JsonDeserializer<Criterion> {

    /** Marks an ordinary condition. */
    private static final int PLAIN = 0;
    /** Marks a period, which carries its name as well as its two moments. */
    private static final int PERIOD = 1;

    @Override
    public Criterion deserialize(JsonElement json, Type type, JsonDeserializationContext context)
            throws JsonParseException {
        JsonArray written = json.getAsJsonArray();
        if (written.get(0).getAsInt() == PLAIN) {
            return new Criterion(
                    written.get(1).getAsString(),
                    WhereFilter.Operation.valueOf(written.get(2).getAsString()),
                    context.deserialize(written.get(3), String[].class),
                    context.deserialize(written.get(4), Criterion[].class));
        }
        PeriodType kind = PeriodType.valueOf(written.get(1).getAsString());
        if (kind == PeriodType.CUSTOM) {
            // Picked by hand: the two moments are the answer.
            return new DateTimeCriterion(written.get(2).getAsLong(), written.get(3).getAsLong());
        }
        // Named: worked out again as of now, because "this month" moves.
        return new DateTimeCriterion(kind);
    }

    @Override
    public JsonElement serialize(Criterion source, Type type, JsonSerializationContext context) {
        JsonArray written = new JsonArray();
        written.add(PLAIN);
        written.add(source.columnName);
        written.add(source.operation.name());
        written.add(context.serialize(source.getValues()));
        written.add(context.serialize(source.getChildren()));
        return written;
    }
}
