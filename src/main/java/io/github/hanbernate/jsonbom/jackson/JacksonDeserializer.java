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

            key = p.getCurrentName();
        }
        for(; key != null; key = p.nextFieldName()) {

            result.put(key, parseValue(p, ctxt));
        }
        return result;
    }

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
