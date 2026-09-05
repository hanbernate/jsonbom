package io.github.hanbernate.jsonbom.jackson;

import io.github.hanbernate.jsonbom.api.Bom;
import io.github.hanbernate.jsonbom.api.BomOrValue;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
public class Jackson3Deserializer extends ValueDeserializer<Bom>{
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
     * @since 0.1.0
     */
    @Override
    public Bom deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
        Bom result = new Bom();

        String key;
        if (p.isExpectedStartObjectToken()) {
            key = p.nextName();
        } else {
            JsonToken t = p.currentToken();
            if (t != JsonToken.PROPERTY_NAME) {
                if (t == JsonToken.END_OBJECT) {
                    return result;
                }

                ctxt.reportWrongTokenException(this, JsonToken.PROPERTY_NAME, (String)null, new Object[0]);
            }

            key = p.currentName();
        }
        for(; key != null; key = p.nextName()) {

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
     * @since 0.1.0
     */
    private BomOrValue parseValue(JsonParser p, DeserializationContext ctxt){
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
