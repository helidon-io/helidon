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

package io.helidon.logging.log4j;

import io.helidon.logging.common.spi.LogLevelManager;
import io.helidon.logging.common.spi.LoggerLevel;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

class Log4jLogLevelManagerTest {
    private static final String LOGGER_NAME = "io.helidon.logging.log4j.test.issue12066";

    @Test
    void shouldManageSlf4jLoggerBackedByLog4j() {
        LoggerFactory.getLogger(LOGGER_NAME).info("Create Log4j-backed SLF4J logger");

        LogLevelManager manager = new Log4jLogLevelManager();

        manager.setLevel(LOGGER_NAME, "DEBUG");
        try {
            LoggerLevel logger = manager.logger(LOGGER_NAME).orElse(null);

            assertThat(logger, notNullValue());
            assertThat(logger.name(), is(LOGGER_NAME));
            assertThat(logger.level(), is("DEBUG"));
            assertThat(logger.configuredLevel().orElse(null), is("DEBUG"));
            assertThat(manager.loggers().get(LOGGER_NAME), notNullValue());
        } finally {
            manager.unsetLevel(LOGGER_NAME);
        }

        LoggerLevel logger = manager.logger(LOGGER_NAME).orElseThrow();
        assertThat(logger.configuredLevel().isEmpty(), is(true));
    }
}
