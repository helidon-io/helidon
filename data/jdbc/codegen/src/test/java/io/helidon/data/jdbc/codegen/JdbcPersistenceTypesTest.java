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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.helidon.common.types.TypeName;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

class JdbcPersistenceTypesTest {
    private static final Map<String, Class<?>> EXPECTED_TYPES = Map.ofEntries(
            Map.entry("JDBC_CLIENT", load("io.helidon.data.jdbc.JdbcClient")),
            Map.entry("JDBC_CLIENT_STATEMENT", load("io.helidon.data.jdbc.JdbcClient$Statement")),
            Map.entry("ROW_MAPPER", load("io.helidon.data.jdbc.JdbcClient$RowMapper")),
            Map.entry("JDBC_STATEMENT", load("io.helidon.data.jdbc.Jdbc$Statement")),
            Map.entry("JDBC_EXECUTION", load("io.helidon.data.jdbc.Jdbc$Execution")),
            Map.entry("JDBC_GENERATED_KEYS", load("io.helidon.data.jdbc.Jdbc$GeneratedKeys")),
            Map.entry("JDBC_ROW_MAPPER", load("io.helidon.data.jdbc.Jdbc$RowMapper")),
            Map.entry("DATA_PERSISTENCE_UNIT", load("io.helidon.data.Data$PersistenceUnit")),
            Map.entry("DATA_PROVIDER_TYPE", load("io.helidon.data.Data$ProviderType")),
            Map.entry("SERVICE_SINGLETON", load("io.helidon.service.registry.Service$Singleton")),
            Map.entry("SERVICE_NAMED", load("io.helidon.service.registry.Service$Named")),
            Map.entry("OPTIONAL", load("java.util.Optional")),
            Map.entry("JDBC_TYPE", load("java.sql.JDBCType")),
            Map.entry("SUPPLIER", load("java.util.function.Supplier")),
            Map.entry("TX_MANDATORY", load("io.helidon.transaction.Tx$Mandatory")),
            Map.entry("TX_NEW", load("io.helidon.transaction.Tx$New")),
            Map.entry("TX_NEVER", load("io.helidon.transaction.Tx$Never")),
            Map.entry("TX_REQUIRED", load("io.helidon.transaction.Tx$Required")),
            Map.entry("TX_SUPPORTED", load("io.helidon.transaction.Tx$Supported")),
            Map.entry("TX_UNSUPPORTED", load("io.helidon.transaction.Tx$Unsupported")));

    @Test
    void validatesEveryTypeNameAgainstItsRuntimeType() throws IllegalAccessException {
        Set<String> checked = new HashSet<>();
        for (Field field : JdbcPersistenceTypes.class.getDeclaredFields()) {
            if (field.getType() != TypeName.class) {
                continue;
            }
            String name = field.getName();
            Class<?> expectedType = EXPECTED_TYPES.get(name);
            assertThat("Missing expected runtime type for " + name, expectedType, notNullValue());
            assertThat(name + " must be static", Modifier.isStatic(field.getModifiers()), is(true));
            assertThat(name + " must be final", Modifier.isFinal(field.getModifiers()), is(true));
            assertThat(name + " must be package private",
                       Modifier.isPublic(field.getModifiers())
                               || Modifier.isProtected(field.getModifiers())
                               || Modifier.isPrivate(field.getModifiers()),
                       is(false));

            TypeName actualType = (TypeName) field.get(null);
            assertThat(name, actualType.fqName(), is(expectedType.getCanonicalName()));
            checked.add(name);
        }
        assertThat("Every expected type must have a TypeName constant", checked, is(EXPECTED_TYPES.keySet()));
    }

    @Test
    void validatesRelatedConstants() throws ReflectiveOperationException {
        String defaultName = (String) load("io.helidon.service.registry.Service$Named")
                .getField("DEFAULT_NAME")
                .get(null);
        assertThat(JdbcPersistenceTypes.DEFAULT_NAME, is(defaultName));
        assertThat(JdbcPersistenceTypes.TX_ANNOTATIONS,
                   is(List.of(JdbcPersistenceTypes.TX_MANDATORY,
                              JdbcPersistenceTypes.TX_NEW,
                              JdbcPersistenceTypes.TX_NEVER,
                              JdbcPersistenceTypes.TX_REQUIRED,
                              JdbcPersistenceTypes.TX_SUPPORTED,
                              JdbcPersistenceTypes.TX_UNSUPPORTED)));
    }

    private static Class<?> load(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new AssertionError("Missing type represented by JdbcPersistenceTypes: " + className, e);
        }
    }
}
