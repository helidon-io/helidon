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
package io.helidon.data.jdbc.tests.declarative.mysql;

import java.util.List;
import java.util.Optional;

import io.helidon.data.jdbc.tests.application.ContactView;
import io.helidon.data.jdbc.tests.application.GeneratedKeyOperations;
import io.helidon.data.jdbc.tests.contract.AbstractJdbcGeneratedKeysContract;
import io.helidon.data.jdbc.tests.declarative.DeclarativeGeneratedKeyOperations;
import io.helidon.data.jdbc.tests.support.TestConfigFactory;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Executes the shared generated-key contract through declarative MySQL operations.
 */
@Testcontainers(disabledWithoutDocker = true)
class MySqlDeclarativeGeneratedKeysTest extends AbstractJdbcGeneratedKeysContract {
    @Container
    static final org.testcontainers.containers.MySQLContainer<?> MYSQL = MySqlDeclarativeTestSupport.MYSQL;

    @Override
    protected void beforeStartApplication() {
        TestConfigFactory.config(MySqlDeclarativeTestSupport.config());
    }

    @Override
    protected Class<? extends GeneratedKeyOperations> operationsType() {
        return DeclarativeGeneratedKeyOperations.class;
    }

    /**
     * Proves MySQL's generated-key result contains the single Connector/J
     * auto-increment key instead of the requested projection columns.
     */
    @Override
    @Test
    protected void mapsGeneratedColumnsIntoARecordProjection() {
        // MySQL Connector/J returns exactly one auto-increment generated-key value named GENERATED_KEY.
        // It does not return the requested ID, NAME, and EMAIL columns from getGeneratedKeys(), so the
        // MySQL expectation is the single key value and the committed row identified by it.
        List<Long> keys = generatedKeys().insertScalarList("generated-record");

        assertThat(keys.size(), is(1));
        assertThat(database().committedByName("generated-record"),
                   is(Optional.of(new ContactView(keys.getFirst(), "generated-record", Optional.empty()))));
    }

    /**
     * Proves MySQL's generated-key result cannot supply mapper-only requested
     * columns and therefore asserts the supported single-key result instead.
     */
    @Override
    @Test
    protected void mapsGeneratedColumnsThroughAnApplicationMapper() {
        // MySQL Connector/J exposes only the generated auto-increment value, not the requested NAME column
        // needed by the mapper. Assert the supported single-key result explicitly for MySQL.
        List<Long> keys = generatedKeys().insertScalarList("generated-mapped");

        assertThat(keys.size(), is(1));
        assertThat(database().committedByName("generated-mapped"),
                   is(Optional.of(new ContactView(keys.getFirst(), "generated-mapped", Optional.empty()))));
    }

    /**
     * Proves MySQL ignores an unknown generated-key column request and returns
     * its default auto-increment generated key.
     */
    @Override
    @Test
    protected void recoversAfterInvalidGeneratedKeyColumnRequest() {
        // MySQL Connector/J ignores an unknown generated-key column name and still returns its single default
        // auto-increment key.
        long id = generatedKeys().insertWithInvalidGeneratedKeyColumn("private-invalid-generated-key-canary");

        assertThat(database().committedByName("private-invalid-generated-key-canary"),
                   is(Optional.of(new ContactView(id, "private-invalid-generated-key-canary", Optional.empty()))));
    }
}
