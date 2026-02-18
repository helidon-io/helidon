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

package io.helidon.webclient.telemetry.metrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.helidon.common.testing.junit5.MatcherWithRetry;

import static org.hamcrest.Matchers.hasSize;

class TestLogHandler extends Handler implements AutoCloseable {

    private final List<String> messages = Collections.synchronizedList(new ArrayList<>());
    private final Logger logger;

    private TestLogHandler(Logger logger) {
        this.logger = logger;
        logger.addHandler(this);
    }

    static TestLogHandler create(Logger logger) {
        return new TestLogHandler(logger);
    }

    @Override
    public void publish(LogRecord record) {
        messages.add(record.getMessage());
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
        logger.removeHandler(this);
    }

    List<String> messages(int expectedCount) {
        try {
            return MatcherWithRetry.assertThatWithRetry("Expected messages", () -> List.copyOf(messages), hasSize(expectedCount));
        } finally {
            messages.clear();
        }
    }
}
