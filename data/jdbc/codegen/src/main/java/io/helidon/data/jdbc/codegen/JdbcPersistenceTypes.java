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
import io.helidon.common.types.TypeNames;

/**
 * Type names referenced by JDBC persistence code generation without a runtime dependency.
 */
final class JdbcPersistenceTypes {
    /** Default service-registry name. */
    static final String DEFAULT_NAME = "@default";
    /** Public generated-code bridge. */
    static final TypeName JDBC_CLIENT = TypeName.create("io.helidon.data.jdbc.JdbcClient");
    /** Public statement stage. */
    static final TypeName JDBC_CLIENT_STATEMENT = TypeName.create("io.helidon.data.jdbc.JdbcClient.Statement");
    /** Public row-mapper contract. */
    static final TypeName ROW_MAPPER = TypeName.create("io.helidon.data.jdbc.JdbcClient.RowMapper");
    /** SQL statement annotation. */
    static final TypeName JDBC_STATEMENT = TypeName.create("io.helidon.data.jdbc.Jdbc.Statement");
    /** Execution annotation. */
    static final TypeName JDBC_EXECUTION = TypeName.create("io.helidon.data.jdbc.Jdbc.Execution");
    /** Generated-key annotation. */
    static final TypeName JDBC_GENERATED_KEYS = TypeName.create("io.helidon.data.jdbc.Jdbc.GeneratedKeys");
    /** Explicit mapper annotation. */
    static final TypeName JDBC_ROW_MAPPER = TypeName.create("io.helidon.data.jdbc.Jdbc.RowMapper");
    /** Persistence-unit annotation. */
    static final TypeName DATA_PERSISTENCE_UNIT = TypeName.create("io.helidon.data.Data.PersistenceUnit");
    /** Provider qualifier. */
    static final TypeName DATA_PROVIDER_TYPE = TypeName.create("io.helidon.data.Data.ProviderType");
    /** Singleton service annotation. */
    static final TypeName SERVICE_SINGLETON = TypeName.create("io.helidon.service.registry.Service.Singleton");
    /** Named service qualifier. */
    static final TypeName SERVICE_NAMED = TypeName.create("io.helidon.service.registry.Service.Named");
    /** Optional generic type. */
    static final TypeName OPTIONAL = TypeNames.OPTIONAL;
    /** Standard JDBC type enum. */
    static final TypeName JDBC_TYPE = TypeName.create("java.sql.JDBCType");
    /** Supplier generic type. */
    static final TypeName SUPPLIER = TypeNames.SUPPLIER;
    /** Mandatory transaction annotation. */
    static final TypeName TX_MANDATORY = TypeName.create("io.helidon.transaction.Tx.Mandatory");
    /** New transaction annotation. */
    static final TypeName TX_NEW = TypeName.create("io.helidon.transaction.Tx.New");
    /** Never transaction annotation. */
    static final TypeName TX_NEVER = TypeName.create("io.helidon.transaction.Tx.Never");
    /** Required transaction annotation. */
    static final TypeName TX_REQUIRED = TypeName.create("io.helidon.transaction.Tx.Required");
    /** Supported transaction annotation. */
    static final TypeName TX_SUPPORTED = TypeName.create("io.helidon.transaction.Tx.Supported");
    /** Unsupported transaction annotation. */
    static final TypeName TX_UNSUPPORTED = TypeName.create("io.helidon.transaction.Tx.Unsupported");

    /** Transaction annotations copied to generated methods. */
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
    private JdbcPersistenceTypes() {
    }
}
