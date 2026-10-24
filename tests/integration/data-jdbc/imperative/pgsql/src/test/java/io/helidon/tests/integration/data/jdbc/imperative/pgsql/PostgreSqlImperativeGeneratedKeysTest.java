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
package io.helidon.tests.integration.data.jdbc.imperative.pgsql;

import io.helidon.tests.integration.data.jdbc.application.GeneratedKeyOperations;
import io.helidon.tests.integration.data.jdbc.contract.AbstractJdbcGeneratedKeysContract;
import io.helidon.tests.integration.data.jdbc.imperative.ImperativeGeneratedKeyOperations;
import io.helidon.tests.integration.data.jdbc.support.TestConfigFactory;

import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Executes the shared generated-key contract through imperative PostgreSQL operations.
 */
@Testcontainers(disabledWithoutDocker = true)
class PostgreSqlImperativeGeneratedKeysTest extends AbstractJdbcGeneratedKeysContract {
    @Container
    static final JdbcDatabaseContainer<?> POSTGRESQL = PostgreSqlImperativeTestSupport.POSTGRESQL;

    @Override
    protected void beforeStartApplication() {
        TestConfigFactory.config(PostgreSqlImperativeTestSupport.config());
    }

    @Override
    protected Class<? extends GeneratedKeyOperations> operationsType() {
        return ImperativeGeneratedKeyOperations.class;
    }
}
