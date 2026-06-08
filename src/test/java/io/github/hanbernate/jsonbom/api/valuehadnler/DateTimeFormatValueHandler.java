package io.github.hanbernate.jsonbom.api.valuehadnler;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import io.github.hanbernate.jsonbom.api.ValueHandler;

public class DateTimeFormatValueHandler implements ValueHandler<String> {
    @Override
    public String apply(Object model, String bomValue) {
        LocalDateTime datetime = (LocalDateTime) model;
        return datetime.format(DateTimeFormatter.ofPattern(bomValue));
    }
}
