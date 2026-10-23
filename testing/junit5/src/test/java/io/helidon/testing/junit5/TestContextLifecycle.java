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

package io.helidon.testing.junit5;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.helidon.service.registry.Service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.platform.testkit.engine.EngineTestKit;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

@Execution(ExecutionMode.SAME_THREAD)
class TestContextLifecycle {
    private static final Logger STORE_LOGGER = Logger.getLogger("org.junit.jupiter.engine.descriptor.AbstractExtensionContext");
    private static final List<String> EVENTS = new CopyOnWriteArrayList<>();

    @Test
    void perClassCleanup() {
        assertCleanup(PerClassTestCase.class, List.of("start", "test", "test", "stop"));
    }

    @Test
    void perMethodCleanup() {
        assertCleanup(PerMethodTestCase.class, List.of("start", "test", "stop", "start", "test", "stop"));
    }

    private void assertCleanup(Class<?> testClass, List<String> expectedEvents) {
        EVENTS.clear();
        try (var warnings = new StoreWarnings()) {
            var result = EngineTestKit.engine("junit-jupiter")
                    .selectors(selectClass(testClass))
                    .execute();

            result.testEvents().assertStatistics(stats -> stats.started(2).succeeded(2).failed(0));
            result.containerEvents().assertStatistics(stats -> stats.started(2).succeeded(2).failed(0));
            assertThat(EVENTS, is(expectedEvents));
            assertThat(warnings.messages, is(List.of()));
        }
    }

    @Testing.Test
    @Execution(ExecutionMode.SAME_THREAD)
    static class PerClassTestCase {
        @Test
        void first(LifecycleService service) {
            service.use();
        }

        @Test
        void second(LifecycleService service) {
            service.use();
        }
    }

    @Testing.Test(perMethod = true)
    @Execution(ExecutionMode.SAME_THREAD)
    static class PerMethodTestCase {
        @Test
        void first(LifecycleService service) {
            service.use();
        }

        @Test
        void second(LifecycleService service) {
            service.use();
        }
    }

    @Service.Singleton
    static class LifecycleService {
        @Service.PostConstruct
        void start() {
            EVENTS.add("start");
        }

        @Service.PreDestroy
        void stop() {
            EVENTS.add("stop");
        }

        void use() {
            EVENTS.add("test");
        }
    }

    private static class StoreWarnings extends Handler implements AutoCloseable {
        private final List<String> messages = new CopyOnWriteArrayList<>();
        private final Level previousLevel = STORE_LOGGER.getLevel();

        private StoreWarnings() {
            STORE_LOGGER.setLevel(Level.ALL);
            STORE_LOGGER.addHandler(this);
        }

        @Override
        public void publish(LogRecord record) {
            if (record.getMessage().contains("Type implements CloseableResource but not AutoCloseable")) {
                messages.add(record.getMessage());
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
            STORE_LOGGER.removeHandler(this);
            STORE_LOGGER.setLevel(previousLevel);
        }
    }
}
