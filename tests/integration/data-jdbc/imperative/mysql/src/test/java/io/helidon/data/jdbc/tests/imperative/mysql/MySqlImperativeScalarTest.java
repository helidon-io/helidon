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

import java.time.OffsetDateTime;

import io.helidon.data.jdbc.tests.contract.AbstractJdbcScalarContract;
import io.helidon.data.jdbc.tests.support.TestConfigFactory;

import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Executes the imperative scalar contract against MySQL.
 */
@Testcontainers(disabledWithoutDocker = true)
class MySqlImperativeScalarTest extends AbstractJdbcScalarContract {
    @Container
    static final MySQLContainer<?> MYSQL = MySqlImperativeTestSupport.MYSQL;

    /**
     * Compares the expected instant with internal epoch value of MySQL so matching bind and read
     * time-zone conversions cannot hide an incorrectly stored {@code TIMESTAMP} instant.
     *
     * @param expected expected offset date-time
     */
    @Override
    protected void assertStoredOffsetDateTimeInstant(OffsetDateTime expected) {
        long storedEpochSecond = client().create("""
                        SELECT UNIX_TIMESTAMP(OFFSET_DATE_TIME_VALUE)
                        FROM SCALAR_VALUE
                        WHERE ID = 1
                        """)
                .map(row -> row.required(1, Long.class))
                .one();
        assertThat(storedEpochSecond, is(expected.toEpochSecond()));
    }

    @Override
    protected void beforeStartApplication() {
        TestConfigFactory.config(MySqlImperativeTestSupport.config());
    }
}
