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
package io.helidon.tests.integration.data.jdbc.imperative.oracle;

import java.util.Map;

import io.helidon.config.Config;
import io.helidon.tests.integration.data.jdbc.contract.AbstractJdbcHealthContract;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class OracleJdbcHealthTest extends AbstractJdbcHealthContract {
    @Container
    static final GenericContainer<?> DATABASE = OracleImperativeTestSupport.ORACLE;

    @Override
    protected Config databaseConfig() {
        return OracleImperativeTestSupport.config();
    }

    @Override
    protected Map<String, String> poolProperties() {
        // Use ordinary network data for cancellation instead of urgent data.
        return Map.of("oracle.net.disableOob", "true");
    }

    @Override
    protected String query() {
        return "SELECT 1 FROM DUAL";
    }

    @Override
    protected String slowQuery() {
        // A bounded Cartesian product keeps execution busy without a stored function or a large hierarchy.
        return "SELECT SUM(a.r * b.r) FROM (SELECT LEVEL r FROM DUAL CONNECT BY LEVEL <= 10000) a "
                + "CROSS JOIN (SELECT LEVEL r FROM DUAL CONNECT BY LEVEL <= 10000) b";
    }
}
