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
package io.helidon.data.jdbc.tests.imperative.mysql;

import io.helidon.data.jdbc.tests.application.SqlInjectionOperations;
import io.helidon.data.jdbc.tests.contract.AbstractJdbcSqlInjectionContract;
import io.helidon.data.jdbc.tests.imperative.ImperativeSqlInjectionOperations;
import io.helidon.data.jdbc.tests.support.TestConfigFactory;

import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Executes the shared SQL-injection contract through imperative MySQL operations.
 */
@Testcontainers(disabledWithoutDocker = true)
class MySqlImperativeSqlInjectionTest extends AbstractJdbcSqlInjectionContract {
    @Container
    static final MySQLContainer<?> MYSQL = MySqlImperativeTestSupport.MYSQL;

    @Override
    protected void beforeStartApplication() {
        TestConfigFactory.config(MySqlImperativeTestSupport.config());
    }

    @Override
    protected Class<? extends SqlInjectionOperations> operationsType() {
        return ImperativeSqlInjectionOperations.class;
    }
}
