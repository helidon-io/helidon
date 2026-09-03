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
package io.helidon.data.jdbc.tests;

import io.helidon.data.Data;
import io.helidon.data.jdbc.Jdbc;
import io.helidon.data.jdbc.JdbcClient;
import io.helidon.service.registry.Service;

/**
 * Generated-source fixture for mapper names that overlap Java or JDBC codegen names.
 */
@Data.Repository
@Data.Provider("jdbc")
@Jdbc.Client("mapper-name-collision")
public interface MapperNameCollisionRepository {

    /**
     * Uses a mapper whose lower-camel simple name would be the Java keyword {@code class}.
     *
     * @return mapped value
     */
    @Jdbc.Statement("SELECT VALUE FROM TEST_VALUE")
    @Jdbc.RowMapper(Class.class)
    String keywordMapper();

    /**
     * Uses a mapper whose lower-camel simple name overlaps the removed optional named-client parameter.
     *
     * @return mapped value
     */
    @Jdbc.Statement("SELECT VALUE FROM TEST_VALUE")
    @Jdbc.RowMapper(NamedJdbcClient.class)
    String namedClientMapper();

    /**
     * Uses an explicit mapper whose simple name already describes its role.
     *
     * @return mapped value
     */
    @Jdbc.Statement("SELECT VALUE FROM TEST_VALUE")
    @Jdbc.RowMapper(ExplicitRowMapper.class)
    String explicitlyNamedMapper();

    /**
     * Uses the marker mapper contract for one parameterization of a nested generic type.
     *
     * @return mapped box
     */
    @Jdbc.Statement("SELECT VALUE FROM TEST_VALUE")
    @Jdbc.RowMapper
    Box<String> stringBox();

    /**
     * Uses a distinct marker mapper contract whose simple mapped type name is otherwise identical.
     *
     * @return mapped box
     */
    @Jdbc.Statement("SELECT VALUE FROM TEST_VALUE")
    @Jdbc.RowMapper
    Box<Integer> integerBox();

    /**
     * Explicit mapper with a simple name that exposes keyword-derived identifier handling.
     */
    @Service.Singleton
    final class Class implements JdbcClient.RowMapper<String> {
        @Override
        public String map(JdbcClient.Row row) {
            return row.get(1, String.class);
        }
    }

    /**
     * Explicit mapper with a simple name that overlaps generated JDBC client infrastructure.
     */
    @Service.Singleton
    final class NamedJdbcClient implements JdbcClient.RowMapper<String> {
        @Override
        public String map(JdbcClient.Row row) {
            return row.get(1, String.class);
        }
    }

    /**
     * Explicit mapper used to verify that an existing mapper suffix is not repeated.
     */
    @Service.Singleton
    final class ExplicitRowMapper implements JdbcClient.RowMapper<String> {
        @Override
        public String map(JdbcClient.Row row) {
            return row.get(1, String.class);
        }
    }

    /**
     * Generic mapper result used to verify structural type arguments remain part of dependency identity.
     *
     * @param value mapped value
     * @param <T> mapped value type
     */
    record Box<T>(T value) {
    }
}
