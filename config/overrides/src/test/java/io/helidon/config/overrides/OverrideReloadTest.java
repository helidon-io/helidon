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

package io.helidon.config.overrides;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.spi.ConfigContent;
import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.NodeConfigSource;
import io.helidon.config.spi.PollableSource;
import io.helidon.config.spi.PollingStrategy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

class OverrideReloadTest {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void connectedRulesReloadTargetAndPreserveOldSnapshots(boolean caching) throws Exception {
        var source = new MutableSource(Map.of("service.*.level", "first"));
        Config definitions = baseBuilder().addSource(source).build();
        Config.Builder builder = baseBuilder()
                .addSource(ConfigSources.create(targetValues()))
                .addFilter(OverrideConfigFilter.fromConfig(definitions));
        if (!caching) {
            builder.disableCaching();
        }
        Config target = builder.build();
        var changes = new LinkedBlockingQueue<Config>();
        target.onChange(changes::add);
        try (var support = OverrideConfigFilter.connect(definitions, target)) {
            assertThat(target.get("service.alpha.level").asString().get(), is("first"));
            source.values(Map.of("service.*.level", "second"));
            source.poll();
            Config updated = awaitValue(changes, "second");
            assertThat(updated.get("service.beta.level").asString().get(), is("second"));
            assertThat(target.context().last().get("service.alpha.level").asString().get(), is("second"));
            assertThat(target.get("service.alpha.level").asString().get(), is("first"));
            assertThat("even an unread key on the old snapshot keeps its original rules",
                       target.get("service.beta.level").asString().get(), is("first"));
        }
    }

    @Test
    void connectReconcilesChangeBetweenConstructionAndRegistration() throws Exception {
        var source = new MutableSource(Map.of("service.*.level", "first"));
        Config definitions = baseBuilder().addSource(source).build();
        Config target = baseBuilder().disableCaching()
                .addSource(ConfigSources.create(targetValues()))
                .addFilter(OverrideConfigFilter.fromConfig(definitions))
                .build();
        var changes = new LinkedBlockingQueue<Config>();
        target.onChange(changes::add);
        definitions.onChange(_ -> { });
        source.values(Map.of("service.*.level", "second", "missing.*.level", "unused"));
        source.poll();
        try (var support = OverrideConfigFilter.connect(definitions, target)) {
            Config updated = awaitValue(changes, "second");
            assertThat(updated.get("missing.gamma.level").exists(), is(false));
            assertThat(target.get("service.alpha.level").asString().get(), is("first"));
        }
    }

    @Test
    void inlineRegistrationIsIsolatedAcrossTargetRuntimes() {
        var filter = OverrideConfigFilter.fromConfig();
        Config first = baseBuilder().disableCaching()
                .addSource(ConfigSources.create(Map.of("service.level", "original",
                                                      "overrides.expressions.service.level", "first")))
                .addFilter(filter)
                .build();
        assertThat(first.get("service.level").asString().get(), is("first"));

        Config second = baseBuilder().disableCaching()
                .addSource(ConfigSources.create(Map.of("service.level", "original",
                                                      "overrides.expressions.service.level", "second")))
                .addFilter(filter)
                .build();

        assertThat(second.get("service.level").asString().get(), is("second"));
        assertThat("sharing the registration must not cross target runtime boundaries",
                   first.get("service.level").asString().get(), is("first"));
    }

    @Test
    void concurrentRuleUpdatesConvergeToLatestSnapshot() throws Exception {
        var source = new MutableSource(Map.of("service.*.level", "first"));
        Config definitions = baseBuilder().addSource(source).build();
        Config target = baseBuilder().addSource(ConfigSources.create(targetValues()))
                .addFilter(OverrideConfigFilter.fromConfig(definitions)).build();
        var changes = new LinkedBlockingQueue<Config>();
        target.onChange(changes::add);
        try (var support = OverrideConfigFilter.connect(definitions, target);
             var executor = Executors.newFixedThreadPool(4)) {
            var start = new CountDownLatch(1);
            var tasks = new ArrayList<Future<?>>();
            for (int i = 0; i < 12; i++) {
                String value = "intermediate-" + i;
                tasks.add(executor.submit(() -> {
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrent updates did not start");
                    }
                    source.values(Map.of("service.*.level", value));
                    source.poll();
                    return null;
                }));
            }
            start.countDown();
            for (var task : tasks) {
                task.get(10, TimeUnit.SECONDS);
            }
            source.values(Map.of("service.*.level", "last"));
            source.poll();
            assertThat(awaitValue(changes, "last").get("service.beta.level").asString().get(), is("last"));
            assertThat(target.context().last().get("service.alpha.level").asString().get(), is("last"));
            assertThat(target.get("service.beta.level").asString().get(), is("first"));
        }
    }

    @Test
    void closingBridgeLeavesBorrowedDefinitionsUsable() throws Exception {
        var source = new MutableSource(Map.of("service.*.level", "first"));
        Config definitions = baseBuilder().addSource(source).build();
        Config target = baseBuilder().addSource(ConfigSources.create(targetValues()))
                .addFilter(OverrideConfigFilter.fromConfig(definitions)).build();
        var changes = new LinkedBlockingQueue<Config>();
        var releaseNotification = new CountDownLatch(1);
        target.onChange(config -> {
            changes.add(config);
            try {
                if (!releaseNotification.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Close test did not release its target callback");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Target callback interrupted", e);
            }
        });
        var definitionChanges = new AtomicInteger();
        definitions.onChange(_ -> definitionChanges.incrementAndGet());
        var support = OverrideConfigFilter.connect(definitions, target);
        try {
            source.values(Map.of("service.*.level", "second"));
            source.poll();
            awaitValue(changes, "second");
            // An admitted reload is paused in its callback. Closing must not wait for that reload.
            support.close();
            source.values(Map.of("service.*.level", "third"));
            source.poll();
        } finally {
            releaseNotification.countDown();
            support.close();
        }
        assertThat(definitionChanges.get(), is(2));
        assertThat(definitions.context().last().get("service.*.level").asString().get(), is("third"));
        assertThat(target.context().last().get("service.alpha.level").asString().get(), is("second"));
    }

    @Test
    void rulesForMissingNodesDoNotCreateValuesOrChangeEvents() {
        var source = new MutableSource(Map.of("missing.*.level", "first"));
        Config definitions = baseBuilder().addSource(source).build();
        Config target = baseBuilder().addSource(ConfigSources.create(targetValues()))
                .addFilter(OverrideConfigFilter.fromConfig(definitions)).build();
        var notifications = new AtomicInteger();
        target.onChange(_ -> notifications.incrementAndGet());
        try (var support = OverrideConfigFilter.connect(definitions, target)) {
            source.values(Map.of("missing.*.level", "second"));
            source.poll();
            // Also rebuild synchronously, so this assertion does not depend on the bridge's scheduling.
            Config rebuilt = target.context().reload();
            assertThat(rebuilt.get("missing.gamma.level").exists(), is(false));
            assertThat(rebuilt.get("service.alpha.level").asString().get(), is("original-alpha"));
            assertThat(notifications.get(), is(0));
        }
    }

    @Test
    void invalidRulesLeaveLastTargetUsableAndLaterValidRulesRecover() throws Exception {
        var source = new MutableSource(Map.of("service.*.level", "first"));
        Config definitions = baseBuilder().addSource(source).build();
        Config target = baseBuilder().addSource(ConfigSources.create(targetValues()))
                .addFilter(OverrideConfigFilter.fromConfig(definitions)).build();
        var changes = new LinkedBlockingQueue<Config>();
        target.onChange(changes::add);
        var warnings = new LinkedBlockingQueue<LogRecord>();
        Logger logger = Logger.getLogger("io.helidon.config.overrides.OverrideChangeSupport");
        Level previousLevel = logger.getLevel();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                warnings.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        logger.addHandler(handler);
        logger.setLevel(Level.ALL);
        try (var support = OverrideConfigFilter.connect(definitions, target)) {
            source.values(Map.of("[", "private-replacement-value"));
            source.poll();
            LogRecord warning = warnings.poll(10, TimeUnit.SECONDS);
            assertThat("Invalid rules must produce a bounded observable failure", warning, notNullValue());
            assertThat(warning.getLevel(), is(Level.WARNING));
            assertThat(warning.getMessage(), is("Could not reload configuration after overrides changed"));
            assertThat("Failure diagnostics must not expose exception details", warning.getThrown(), nullValue());
            assertThat(target.context().last().get("service.alpha.level").asString().get(), is("first"));

            source.values(Map.of("service.*.level", "recovered"));
            source.poll();
            assertThat(awaitValue(changes, "recovered").get("service.beta.level").asString().get(), is("recovered"));
            assertThat(target.get("service.beta.level").asString().get(), is("first"));
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(previousLevel);
        }
    }

    private static Config awaitValue(LinkedBlockingQueue<Config> changes, String value) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (true) {
            long remaining = Math.max(0, deadline - System.nanoTime());
            Config changed = changes.poll(remaining, TimeUnit.NANOSECONDS);
            assertThat("Expected target change to " + value, changed, notNullValue());
            if (changed.get("service.alpha.level").asString().get().equals(value)) {
                return changed;
            }
        }
    }

    private static Config.Builder baseBuilder() {
        return Config.builder()
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableFilterServices()
                .changesExecutor(Runnable::run);
    }

    private static Map<String, String> targetValues() {
        return Map.of("service.alpha.level", "original-alpha", "service.beta.level", "original-beta");
    }

    private static final class MutableSource implements NodeConfigSource, PollableSource<Map<String, String>> {
        private volatile Map<String, String> values;
        private PollingStrategy.Polled polled;

        private MutableSource(Map<String, String> values) {
            values(values);
        }

        void values(Map<String, String> values) {
            this.values = Map.copyOf(values);
        }

        void poll() {
            if (polled == null) {
                throw new IllegalStateException("Change support has not started");
            }
            polled.poll(Instant.now());
        }

        @Override
        public boolean isModified(Map<String, String> stamp) {
            return !values.equals(stamp);
        }

        @Override
        public Optional<PollingStrategy> pollingStrategy() {
            return Optional.of(callback -> polled = callback);
        }

        @Override
        public Optional<ConfigContent.NodeContent> load() {
            Map<String, String> snapshot = values;
            var node = ConfigNode.ObjectNode.builder();
            snapshot.forEach(node::addValue);
            return Optional.of(ConfigContent.NodeContent.builder().node(node.build()).stamp(snapshot).build());
        }
    }
}
