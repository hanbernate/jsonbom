package io.github.hanbernate.jsonbom.spring;

import com.google.common.base.CaseFormat;
import jakarta.persistence.Column;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.BeanUtils;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.util.ReflectionUtils;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * 将 ResultSet 行映射为 Java 对象的 RowMapper 实现。
 * 通过字段名（或 @Column 注解指定的列名）与 ResultSet 列名匹配自动填充。
 * 列名查找规则：优先使用 @Column(name) → 否则将字段名从 UpperCamel 转为 lower_underscore。
 *
 * @param <T> 映射的目标类型
 * @author grinding
 */
@Slf4j
public class CamelRowMapper<T> implements RowMapper<T> {

    private final Class<T> mappedClass;
    private final Map<String, FieldSetter<T>> mappedFields = new HashMap<>();

    /**
     * 静态工厂方法，创建 CamelRowMapper 实例。
     *
     * @param mappedClass 映射的目标类型
     * @param <T>         目标类型泛型
     * @return CamelRowMapper 实例
     */
    public static <T> CamelRowMapper<T> newInstance(Class<T> mappedClass) {
        return new CamelRowMapper<>(mappedClass);
    }

    private CamelRowMapper(Class<T> mappedClass) {
        this.mappedClass = mappedClass;

        Arrays.stream(mappedClass.getDeclaredFields()).forEach(field -> {
            Method writeMethod = null;
            try {
                writeMethod = new PropertyDescriptor(field.getName(), mappedClass).getWriteMethod();
            } catch (IntrospectionException ignored) {
                //如果不存在writeMethod=null
            }

            Column columnAnnotation = field.getAnnotation(Column.class);
            String columnName;
            if (columnAnnotation != null && columnAnnotation.name() != null && !columnAnnotation.name().isEmpty()) {
                columnName = columnAnnotation.name();
            } else {
                columnName = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, field.getName());
            }

            mappedFields.put(columnName, new FieldSetter<>(field, writeMethod));
        });
    }

    @Override
    public T mapRow(ResultSet rs, int rowNum) {
        try {
            var rsmd = rs.getMetaData();
            int columnCount = rsmd.getColumnCount();
            T result = BeanUtils.instantiate(mappedClass);
            for (int index = 1; index <= columnCount; index++) {
                String column = JdbcUtils.lookupColumnName(rsmd, index).toLowerCase();
                FieldSetter<T> setter = this.mappedFields.get(column);
                if (setter != null) {
                    Object value = JdbcUtils.getResultSetValue(rs, index, setter.field.getType());
                    setter.set(result, value);
                }
            }
            return result;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return null;
        }
    }

    static class FieldSetter<T> {

        final Field field;
        private final Method setter;

        FieldSetter(Field field, Method setter) {
            this.field = field;
            this.setter = setter;
        }

        T set(T result, Object value) {
            if (setter != null) {
                setter.setAccessible(true);
                ReflectionUtils.invokeMethod(setter, result, value);
            } else {
                field.setAccessible(true);
                ReflectionUtils.setField(field, result, value);
            }
            return result;
        }
    }
}
