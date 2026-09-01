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
package io.helidon.data.jdbc.tests.chaos.declarative.pgsql;

import io.helidon.data.jdbc.tests.chaos.application.ChaosContactOperations;
import io.helidon.data.jdbc.tests.chaos.declarative.DeclarativeChaosContactOperations;
import io.helidon.data.jdbc.tests.chaos.support.AbstractPostgreSqlConnectionLossChaosTest;

import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Runs native PostgreSQL session loss through generated declarative repository operations.
 */
@Testcontainers(disabledWithoutDocker = true)
class PostgreSqlDeclarativeConnectionLossChaosTest extends AbstractPostgreSqlConnectionLossChaosTest {
    @Override
    protected Class<? extends ChaosContactOperations> operationsType() {
        return DeclarativeChaosContactOperations.class;
    }
}
