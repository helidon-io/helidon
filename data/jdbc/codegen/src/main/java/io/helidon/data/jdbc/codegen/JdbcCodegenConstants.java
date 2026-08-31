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

/**
 * Stable names used by JDBC repository code generation.
 */
final class JdbcCodegenConstants {

    static final String PROVIDER = "jdbc";
    static final String DEFAULT_CLIENT_NAME = "@default";
    static final String REPOSITORY_SUFFIX = "__Jdbc";
    static final String GENERATED_VERSION = "1";
    static final String ANNOTATION_VALUE_PROPERTY = "value";
    static final String JDBC_CLIENT_NAME = "jdbcClient";
    static final String JDBC_STATEMENT_NAME = "jdbcStatement";
    static final String BIND_PARAMETER_METHOD_NAME = "bindParameter";
    static final String SQL_FIELD_PREFIX = "SQL_";
    static final String MAPPER_FIELD_PREFIX = "MAPPER_";
    static final String ROW_MAPPER_SUFFIX = "RowMapper";

    private JdbcCodegenConstants() {
    }
}
