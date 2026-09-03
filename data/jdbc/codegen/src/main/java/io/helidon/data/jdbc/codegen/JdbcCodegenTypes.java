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

import io.helidon.common.types.TypeName;

/**
 * Type names referenced by JDBC repository code generation without a runtime dependency.
 */
final class JdbcCodegenTypes {

    static final TypeName JDBC_CLIENT = TypeName.create("io.helidon.data.jdbc.JdbcClient");
    static final TypeName JDBC_CLIENT_STATEMENT = TypeName.create("io.helidon.data.jdbc.JdbcClient.Statement");
    static final TypeName GENERATED_JDBC_DATA = TypeName.create("io.helidon.data.jdbc.GeneratedJdbcData");
    static final TypeName ROW_MAPPER = TypeName.create("io.helidon.data.jdbc.JdbcClient.RowMapper");
    static final TypeName JDBC_CLIENT_ANNOTATION = TypeName.create("io.helidon.data.jdbc.Jdbc.Client");
    static final TypeName JDBC_STATEMENT = TypeName.create("io.helidon.data.jdbc.Jdbc.Statement");
    static final TypeName JDBC_EXECUTION = TypeName.create("io.helidon.data.jdbc.Jdbc.Execution");
    static final TypeName JDBC_GENERATED_KEYS = TypeName.create("io.helidon.data.jdbc.Jdbc.GeneratedKeys");
    static final TypeName JDBC_ROW_MAPPER = TypeName.create("io.helidon.data.jdbc.Jdbc.RowMapper");
    static final TypeName DATA_PERSISTENCE_UNIT = TypeName.create("io.helidon.data.Data.PersistenceUnit");
    static final TypeName DATA_PROVIDER_TYPE = TypeName.create("io.helidon.data.Data.ProviderType");
    static final TypeName SERVICE_SINGLETON = TypeName.create("io.helidon.service.registry.Service.Singleton");
    static final TypeName SERVICE_NAMED = TypeName.create("io.helidon.service.registry.Service.Named");
    static final TypeName JDBC_TYPE = TypeName.create("java.sql.JDBCType");
    static final TypeName TX_MANDATORY = TypeName.create("io.helidon.transaction.Tx.Mandatory");
    static final TypeName TX_NEW = TypeName.create("io.helidon.transaction.Tx.New");
    static final TypeName TX_NEVER = TypeName.create("io.helidon.transaction.Tx.Never");
    static final TypeName TX_REQUIRED = TypeName.create("io.helidon.transaction.Tx.Required");
    static final TypeName TX_SUPPORTED = TypeName.create("io.helidon.transaction.Tx.Supported");
    static final TypeName TX_UNSUPPORTED = TypeName.create("io.helidon.transaction.Tx.Unsupported");

    // Generated methods retain transaction annotations declared by the repository.
    static final List<TypeName> TX_ANNOTATIONS = List.of(
            TX_MANDATORY,
            TX_NEW,
            TX_NEVER,
            TX_REQUIRED,
            TX_SUPPORTED,
            TX_UNSUPPORTED);

    /**
     * Prevents construction of the constants holder.
     */
    private JdbcCodegenTypes() {
    }
}
