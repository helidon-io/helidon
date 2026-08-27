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
package io.helidon.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

class DeprecatedConfigTest {
    private static final String DEPRECATED_KEY = "deprecated-key";

    private final List<LogRecord> records = new ArrayList<>();
    private final Logger logger = Logger.getLogger(DeprecatedConfig.class.getName());
    private final Handler handler = new Handler() {
        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };

    @BeforeEach
    void addHandler() {
        logger.addHandler(handler);
    }

    @AfterEach
    void removeHandler() {
        logger.removeHandler(handler);
    }

    @Test
    void warnsWhenDeprecatedKeyIsPresent() {
        Config config = Config.create(ConfigSources.create(Map.of(DEPRECATED_KEY, "true")));

        assertThat(DeprecatedConfig.get(config, DEPRECATED_KEY).asBoolean().orElseThrow(), is(true));
        assertThat(records.size(), is(1));
        assertThat(records.getFirst().getMessage(), containsString(DEPRECATED_KEY));
    }

    @Test
    void doesNotWarnWhenDeprecatedKeyIsAbsent() {
        Config config = Config.create(ConfigSources.empty());

        assertThat(DeprecatedConfig.get(config, DEPRECATED_KEY).exists(), is(false));
        assertThat(records.isEmpty(), is(true));
    }
}
