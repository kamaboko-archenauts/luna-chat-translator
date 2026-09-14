package dev.lunachat;

import com.google.gson.*;
import java.util.*;

/** Pure text boundary: Minecraft messages are data, never shell arguments or instructions. */
public final class TranslationProtocol {
    public static JsonObject object(Object... fields) {
        JsonObject result = new JsonObject();
        Gson gson = new Gson();
        for (int i = 0; i < fields.length; i += 2)
            result.add((String) fields[i], gson.toJsonTree(fields[i + 1]));
        return result;
    }

    public static JsonObject schema() {
        return JsonParser.parseString("""
            {"type":"object","properties":{"translations":{"type":"array","items":{
              "type":"object","properties":{"id":{"type":"integer"},"text":{"type":"string"}},
              "required":["id","text"],"additionalProperties":false}}},
              "required":["translations"],"additionalProperties":false}
            """).getAsJsonObject();
    }

    public static List<String> parse(String output, int expected) {
        JsonArray rows = JsonParser.parseString(output).getAsJsonObject().getAsJsonArray("translations");
        if (rows == null || rows.size() != expected) throw new IllegalArgumentException("Wrong translation count");
        String[] result = new String[expected];
        for (JsonElement element : rows) {
            JsonObject row = element.getAsJsonObject();
            int id = row.get("id").getAsBigDecimal().intValueExact();
            if (id < 0 || id >= expected || result[id] != null) throw new IllegalArgumentException("Invalid translation id");
            String value = row.get("text").getAsString();
            if (value.isBlank() || value.length() > 8192) throw new IllegalArgumentException("Invalid translation length");
            // Translation cannot inject Minecraft formatting or control characters.
            result[id] = value.replaceAll("[\\p{Cc}§]", " ");
        }
        return List.of(result);
    }
}
