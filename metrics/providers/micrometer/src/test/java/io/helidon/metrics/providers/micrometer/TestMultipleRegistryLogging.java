/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.metrics.providers.micrometer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.helidon.metrics.api.Metrics;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;

class TestMultipleRegistryLogging {

    private static final Logger mmeterRegisteryLogger = Logger.getLogger(MMeterRegistry.class.getName());
    private static TestHandler testHandler;

    @BeforeAll
    static void beforeAll() {
        testHandler = TestHandler.create();
        mmeterRegisteryLogger.addHandler(testHandler);
    }

    @AfterAll
    static void afterAll() {
        MMeterRegistry.clearMultipleInstantiationInfo();
        mmeterRegisteryLogger.removeHandler(testHandler);
    }

    @BeforeEach
    void setUp() {
        MetricsFactory.closeAll();
    }

    @AfterEach
    void tearDown() {
        testHandler.clear();
    }

    @Test
    void testSingleRegistry() {
        Metrics.globalRegistry();
        assertThat("Single meter registry", testHandler.messages(), hasSize(0));
    }

    @Test
    void testTwoRegistries() {
        Metrics.globalRegistry();
        Metrics.createMeterRegistry();

        assertThat("Two meter registries", testHandler.messages(),
                   hasItem(allOf(
                           containsString("Unexpected duplicate"),
                           containsString("Original instantiation"),
                           containsString("Additional instantiation"))));
    }

    @Test
    void testThreeRegistries() {
        Metrics.globalRegistry();
        Metrics.createMeterRegistry();
        Metrics.createMeterRegistry();

        assertThat("Three meter registries",
                   testHandler.messages(),
                   allOf(
                           hasItem(allOf(
                                   containsString("Unexpected duplicate"),
                                   containsString("Original instantiation"),
                                   containsString("Additional instantiation"))),
                           hasItem(
                                   containsString("Unexpected additional instantiation"))));
    }

    @Test
    void testTwoRegistriesWithWarningDisabled() {
        MetricsConfig configWithWarningsSuppressed = MetricsConfig.builder().warnOnMultipleRegistries(false).build();
        Metrics.createMeterRegistry(configWithWarningsSuppressed);
        Metrics.createMeterRegistry(configWithWarningsSuppressed);

        assertThat("Two meter registrations with warnings suppressed", testHandler.messages, hasSize(0));

    }

    private static class TestHandler extends Handler {

        private final List<String> messages = Collections.synchronizedList(new ArrayList<>());

        // For testing.
        static TestHandler create() {
            return new TestHandler();
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

        }

        List<String> messages() {
            return List.copyOf(messages);
        }

        void clear() {
            messages.clear();
        }
    }

}
