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
package io.helidon.tests.integration.data.jdbc.declarative.mysql;

import io.helidon.tests.integration.data.jdbc.application.transaction.FocusedTransactionOperations;
import io.helidon.tests.integration.data.jdbc.contract.AbstractJdbcFocusedTransactionContract;
import io.helidon.tests.integration.data.jdbc.declarative.DeclarativeFocusedTransactionOperations;
import io.helidon.tests.integration.data.jdbc.support.TestConfigFactory;

import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Executes focused generated-repository transaction behavior against MySQL.
 */
@Testcontainers(disabledWithoutDocker = true)
class MySqlDeclarativeFocusedTransactionTest extends AbstractJdbcFocusedTransactionContract {
    @Container
    static final MySQLContainer<?> MYSQL = MySqlDeclarativeTestSupport.MYSQL;

    @Override
    protected void beforeStartApplication() {
        TestConfigFactory.config(MySqlDeclarativeTestSupport.config());
    }

    @Override
    protected Class<? extends FocusedTransactionOperations> operationsType() {
        return DeclarativeFocusedTransactionOperations.class;
    }
}
