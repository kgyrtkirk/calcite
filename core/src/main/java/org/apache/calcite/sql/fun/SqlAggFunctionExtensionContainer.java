package org.apache.calcite.sql.fun;

import java.util.Map;

import org.apache.calcite.sql.SqlAggFunctionExtension;

import com.google.common.collect.ImmutableMap;

public class SqlAggFunctionExtensionContainer {

    private Map<Class<SqlAggFunctionExtension>, SqlAggFunctionExtension> extensionMap;

    public static final SqlAggFunctionExtensionContainer EMPTY = new SqlAggFunctionExtensionContainer(
            ImmutableMap.of());

    public SqlAggFunctionExtensionContainer(
            ImmutableMap<Class<SqlAggFunctionExtension>, SqlAggFunctionExtension> newMap) {
        extensionMap = newMap;
    }

    public <T extends SqlAggFunctionExtension> SqlAggFunctionExtensionContainer with(Class<T> clazz, T extension) {
        ImmutableMap<Class<SqlAggFunctionExtension>, SqlAggFunctionExtension> newMap = ImmutableMap
                .<Class<SqlAggFunctionExtension>, SqlAggFunctionExtension>builder()
                .putAll(extensionMap)
                .build();

        return new SqlAggFunctionExtensionContainer(newMap);
    }

    @SuppressWarnings("unchecked")
    public <T extends Object> T unwrap(Class<T> clazz) {
        return (T) extensionMap.get(clazz);
    }

}
