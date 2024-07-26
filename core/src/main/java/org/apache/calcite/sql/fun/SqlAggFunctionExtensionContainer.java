package org.apache.calcite.sql.fun;

import java.util.Map;

import org.apache.calcite.sql.SqlAggFunctionExtension;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;

public class SqlAggFunctionExtensionContainer {

    private Map<Class<SqlAggFunctionExtension>, SqlAggFunctionExtension> extensionMap;

    public static final SqlAggFunctionExtensionContainer EMPTY = new SqlAggFunctionExtensionContainer();

    public SqlAggFunctionExtensionContainer(Builder<Object, Object> newMap) {
        extensionMap = (Map<Class<SqlAggFunctionExtension>, SqlAggFunctionExtension>) newMap;
    }

    public <T extends SqlAggFunctionExtension> SqlAggFunctionExtensionContainer with(Class<T> clazz, T extension) {
        Builder<Object, Object> newMap = ImmutableMap.builder()
                .putAll(extensionMap);

        return new SqlAggFunctionExtensionContainer(newMap);
    }

}
