/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.calcite.sql.fun;

import org.apache.calcite.schema.Wrapper;
import org.apache.calcite.sql.SqlAggFunctionExtension;
import org.apache.calcite.sql.SqlFunctionExtension;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

/**
 * Provides set level functions for {@link SqlFunctionExtension}.
 */
public class SqlAggFunctionExtensionContainer implements Wrapper {

    private Map<Class<? extends SqlAggFunctionExtension>, SqlAggFunctionExtension> extensionMap;

    public static final SqlAggFunctionExtensionContainer EMPTY =
            new SqlAggFunctionExtensionContainer(ImmutableMap.of());

    public SqlAggFunctionExtensionContainer(
            ImmutableMap<Class<? extends SqlAggFunctionExtension>, SqlAggFunctionExtension> newMap) {
        extensionMap = newMap;
    }

    public <T extends SqlAggFunctionExtension> SqlAggFunctionExtensionContainer with(Class<T> clazz, T extension) {
        ImmutableMap<Class<? extends SqlAggFunctionExtension>, SqlAggFunctionExtension> newMap = ImmutableMap
                .<Class<? extends SqlAggFunctionExtension>, SqlAggFunctionExtension>builder()
                .putAll(extensionMap)
                .put(clazz, extension)
                .build();

        return new SqlAggFunctionExtensionContainer(newMap);
    }

    @SuppressWarnings("unchecked")
    public <T extends Object> T unwrap(Class<T> clazz) {
        return (T) extensionMap.get(clazz);
    }
}
