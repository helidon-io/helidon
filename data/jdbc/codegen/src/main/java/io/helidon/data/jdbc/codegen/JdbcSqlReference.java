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

import io.helidon.common.types.TypedElementInfo;

/**
 * Separates one complete configuration reference from its optional SQL default.
 *
 * @param key The key identifies an application configuration path.
 * @param defaultSql The value supplies fallback SQL, or is {@code null} when the key is required.
 */
record JdbcSqlReference(String key, String defaultSql) {

    static JdbcSqlReference parse(String value, TypedElementInfo method) {
        String expression = value.strip();
        if (!expression.startsWith("${")) {
            return null;
        }
        if (!expression.endsWith("}") || expression.indexOf("${", 2) >= 0) {
            throw JdbcMethodPlan.failure(method, "The SQL configuration reference must be one complete expression.");
        }
        String body = expression.substring(2, expression.length() - 1);
        int separator = body.indexOf(':');
        String key = separator < 0 ? body : body.substring(0, separator);
        String defaultSql = separator < 0 ? null : body.substring(separator + 1);
        if (key.isBlank() || key.indexOf('{') >= 0 || key.indexOf('}') >= 0) {
            throw JdbcMethodPlan.failure(method, "The SQL configuration key must be nonblank and contain no braces.");
        }
        if (defaultSql != null && defaultSql.isBlank()) {
            throw JdbcMethodPlan.failure(method, "The SQL configuration default must not be blank.");
        }
        return new JdbcSqlReference(key, defaultSql);
    }
}
