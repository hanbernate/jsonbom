package io.github.hanbernate.jsonbom.jackson;

import io.github.hanbernate.jsonbom.api.Bom;
import io.github.hanbernate.jsonbom.api.BomOrValue;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

public class JacksonDeserializer extends JsonDeserializer<Bom> {
    /**
     * Deserializes a JSON object into a {@link Bom} instance.
     * <p>
     * The method processes JSON field names and their corresponding values,
     * building a BOM tree structure. Empty objects and null/empty string values
     * are represented by {@link BomOrValue#EMPTY}.
     *
     * @param p the JsonParser for reading JSON content
     * @param ctxt the deserialization context
     * @return a Bom instance representing the parsed JSON structure
     * @throws IOException if an I/O error occurs during parsing
     * @throws JsonProcessingException if JSON parsing fails
     * @since 0.0.1
     */
    @Override
    public Bom deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        Bom result = new Bom();

        String key;
        if (p.isExpectedStartObjectToken()) {
            key = p.nextFieldName();
        } else {
            JsonToken t = p.getCurrentToken();
            if (t != JsonToken.FIELD_NAME) {
                if (t == JsonToken.END_OBJECT) {
                    return result;
                }

                ctxt.reportWrongTokenException(this, JsonToken.FIELD_NAME, (String)null, new Object[0]);
            }

            key = p.currentName();
        }
        for(; key != null; key = p.nextFieldName()) {

            result.put(key, parseValue(p, ctxt));
        }
        return result;
    }

    /**
     * Parses a JSON value and converts it to a {@link BomOrValue}.
     * <p>
     * The parsing logic handles:
     * <ul>
     *     <li>{@code null} values -> {@link BomOrValue#EMPTY}</li>
     *     <li>Empty string values -> {@link BomOrValue#EMPTY}</li>
     *     <li>Nested JSON objects -> recursively deserialize into a BOM child</li>
     *     <li>Primitive string values -> wrap as a VALUE type</li>
     * </ul>
     *
     * @param p the JsonParser positioned at the start of a value
     * @param ctxt the deserialization context
     * @return a BomOrValue representing the parsed value
     * @throws IOException if an I/O error occurs during parsing
     * @since 0.0.1
     */
    private BomOrValue parseValue(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonToken t = p.nextToken();
        if (t == JsonToken.VALUE_NULL) {

            return BomOrValue.EMPTY;
        }

        if(t.isStructStart()) {
            Bom child = this.deserialize(p, ctxt);
            return new BomOrValue(null, child);
        }

        String valueString = p.getValueAsString();
        if("".equals(valueString)){
            return BomOrValue.EMPTY;
        }
        return new BomOrValue(valueString, null);
    }
}
