/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.helidon.data.jdbc.codegen;

import java.util.List;

import io.helidon.codegen.classmodel.Method;
import io.helidon.common.types.TypeName;

/**
 * Emits the client mapping stage for fixed scalar results.
 */
final class JdbcScalarMapperGenerator {

    /**
     * Prevents construction of the generator utility.
     */
    private JdbcScalarMapperGenerator() {
    }

    /**
     * Adds column-one scalar mapping to a query statement.
     *
     * @param method generated method
     * @param mappedType scalar type
     */
    static void addQueryMapping(Method.Builder method, TypeName mappedType) {
        method.addContent(".map(")
                // Preserve a primitive class literal so JdbcClient can enforce SQL NULL semantics before unboxing.
                .addContent(mappedType)
                .addContent(".class)");
    }

    /**
     * Adds scalar mapping for the first column of a generated key statement.
     *
     * @param method generated method
     * @param mappedType scalar type
     * @param columnNames requested generated columns
     * @param nullable whether SQL {@code NULL} maps to an empty optional
     */
    static void addGeneratedKeyMapping(Method.Builder method,
                                       TypeName mappedType,
                                       List<String> columnNames,
                                       boolean nullable) {
        method.addContent(".generatedKeys()");
        addGeneratedKeyColumns(method, columnNames);
        method.addContent(nullable ? ".map(row -> row.optional(1, " : ".map(row -> row.required(1, ")
                .addContent(mappedType.boxed())
                .addContent(".class))");
    }

    /**
     * Adds generated column builder calls in declaration order.
     *
     * @param method generated method
     * @param columnNames requested generated columns
     */
    static void addGeneratedKeyColumns(Method.Builder method, List<String> columnNames) {
        // Use one builder call for each column to avoid arrays, collections, and varargs in generated source.
        for (String columnName : columnNames) {
            method.addContent(".addColumn(")
                    .addContentLiteral(columnName)
                    .addContent(")");
        }
    }
}
