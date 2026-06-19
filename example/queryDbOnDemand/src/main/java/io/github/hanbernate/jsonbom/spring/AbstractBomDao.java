package io.github.hanbernate.jsonbom.spring;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.google.common.base.CaseFormat;

import io.github.hanbernate.jsonbom.api.Bom;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import lombok.extern.slf4j.Slf4j;
import lombok.Getter;
import lombok.Setter;

@Slf4j
public class AbstractBomDao<T> {
    private Class<T> mappedClass;
    private RowMapper<T> defaultRowMapper;
    private String tableName;
    private Map<String, String> bom2column = new HashMap<>();
    @Getter
    @Setter
    private JdbcTemplate jdbcTemplate;
    protected AbstractBomDao(){
        this.mappedClass = (Class<T>) ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        this.defaultRowMapper = CamelRowMapper.newInstance(this.mappedClass);
        this.tableName = getTableName(this.mappedClass);
        this.bom2column = Arrays.stream(this.mappedClass.getDeclaredFields())
            .collect(Collectors.toMap(f -> f.getName(), f -> getColumnName(f)));
    }

    private static String getTableName(Class<?> clazz){
        return Optional.ofNullable(clazz.getAnnotation(Table.class))
            .map(Table::name)
            .orElse(CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, clazz.getSimpleName()));
    }

    private static String getColumnName(Field f){
        return Optional.ofNullable(f.getAnnotation(Column.class))
            .map(Column::name)
            .orElse(CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, f.getName()));
    }

    protected String getSelectSql(Bom bom){
        String columns = bom.keySet().stream().map(bomName -> bom2column.get(bomName))
            .filter(Objects::nonNull)
            .collect(Collectors.joining(","));
        return "select " + columns + " from " + this.tableName;
    }

    protected RowMapper<T> getRowDefaultMapper(){
        return this.defaultRowMapper;
    }

    protected JdbcTemplate jdbcTemplate() {
        return this.jdbcTemplate;
    }
    
    protected Optional<T> findOne(String sql, Map.Entry<?,Integer>[] argsWithType, RowMapper<T> rowMapper) {

        Object[] args = Arrays.stream(argsWithType)
            .map(Entry::getKey)
            .toArray();
        int[] types = Arrays.stream(argsWithType)
            .mapToInt(Entry<?,Integer>::getValue)
            .toArray();

        List<T> results = jdbcTemplate.query(sql, args, types, rowMapper);
        if (results == null || results.isEmpty()) {
            return Optional.empty();
        }
        if (results.size() > 1) {
            log.warn("findOne with args [{}] returned {} rows, expected at most 1", formatArgs(argsWithType), results.size());
        }
        return Optional.of(results.get(0));
    }

    private static String formatArgs(Map.Entry<?, Integer>[] argsWithType) {
        return Arrays.stream(argsWithType)
                .map(e -> e.getKey() + ":" + e.getValue())
                .collect(Collectors.joining(", "));
    }
    

}
