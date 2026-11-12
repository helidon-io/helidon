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

import io.helidon.config.Config;
import io.helidon.tests.integration.data.jdbc.contract.AbstractJdbcCallableContract;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class MySqlDeclarativeCallableTest extends AbstractJdbcCallableContract {
    @Container
    static final GenericContainer<?> DATABASE = MySqlDeclarativeTestSupport.MYSQL
            .withCommand("--log-bin-trust-function-creators=1");

    @Override
    protected Config configuration() {
        return MySqlDeclarativeTestSupport.config();
    }

    @Override
    protected String vendor() {
        return "mysql";
    }

    @Override
    protected boolean generated() {
        return true;
    }
}
