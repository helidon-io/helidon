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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.spi.ConfigContent;
import io.helidon.config.spi.ConfigFilter;
import io.helidon.config.spi.ConfigFilterFactory;
import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.NodeConfigSource;
import io.helidon.config.spi.PollableSource;
import io.helidon.config.spi.PollingStrategy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OverrideReloadTest {
    @Test
    void completedDefinitionPollPublishesRulesBeforeReturning() throws Exception {
        var source = new MutableSource(Map.of("services.*.endpoint", "https://initial.example/api"));
        var provider = OverrideConfigFilterProvider.builder().addConfigSource(source).build();
        ConfigFilterFactory factory = provider.create(Config.empty());
        ConfigFilter original = factory.create(Config.empty());
        var callbackEntered = new CountDownLatch(1);
        var releaseCallback = new CountDownLatch(1);
        var notifications = new AtomicInteger();
        try {
            assertThat(factory.startChangeSupport(() -> {
                if (notifications.incrementAndGet() == 1) {
                    // Starting change support reconciles the initial snapshot before the source changes.
                    return;
                }
                callbackEntered.countDown();
                try {
                    assertThat("The definition callback must be released",
                               releaseCallback.await(10, TimeUnit.SECONDS), is(true));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while publishing definition changes", e);
                }
            }), is(true));
            assertThat("Startup reconciliation publishes the initial snapshot", notifications.get(), is(1));

            source.values(Map.of("services.*.endpoint", "https://updated.example/api"));
            try (var executor = Executors.newSingleThreadExecutor()) {
                Future<?> polling = executor.submit(source::poll);
                try {
                    assertThat("The definition change callback must begin",
                               callbackEntered.await(10, TimeUnit.SECONDS), is(true));
                    assertThrows(TimeoutException.class, () -> polling.get(100, TimeUnit.MILLISECONDS));
                } finally {
                    releaseCallback.countDown();
                    polling.get(10, TimeUnit.SECONDS);
                }
            }

            var key = Config.Key.create("services.orders.endpoint");
            ConfigFilter updated = factory.create(Config.empty());
            assertThat(updated.apply(key, "https://primary.example/orders"), is("https://updated.example/api"));
            assertThat(original.apply(key, "https://primary.example/orders"), is("https://initial.example/api"));
            assertThat("A completed poll publishes one definition change", notifications.get(), is(2));
        } finally {
            releaseCallback.countDown();
            factory.stopChangeSupport();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void definitionChangesReloadTargetAndPreserveOldSnapshots(boolean caching) throws Exception {
        var source = new MutableSource(Map.of("services.*.endpoint", "https://initial.example/api"));
        Config.Builder builder = targetBuilder(source);
        if (!caching) {
            builder.disableCaching();
        }
        Config target = builder.build();
        var changes = new LinkedBlockingQueue<Config>();
        target.onChange(changes::add);
        try {
            assertThat(target.get("services.orders.endpoint").asString().get(), is("https://initial.example/api"));
            source.values(Map.of("services.*.endpoint", "https://updated.example/api"));
            source.poll();
            Config updated = awaitValue(changes, "https://updated.example/api");
            assertThat(updated.get("services.payments.endpoint").asString().get(), is("https://updated.example/api"));
            assertThat(target.get("services.orders.endpoint").asString().get(), is("https://initial.example/api"));
            assertThat("Unread old nodes retain their generation's rules",
                       target.get("services.payments.endpoint").asString().get(), is("https://initial.example/api"));
            assertThat("Rebuilding does not initialize definition monitoring again", source.starts.get(), is(1));
        } finally {
            target.context().stopChangeSupport();
        }
    }

    @Test
    void startupReconcilesChangesBeforeCallbackRegistration() throws Exception {
        var source = new MutableSource(Map.of("services.*.endpoint", "https://initial.example/api"));
        var reconciled = new CountDownLatch(1);
        var provider = OverrideConfigFilterProvider.builder().addConfigSource(source).build();
        Config target = baseBuilder().disableCaching().addSource(ConfigSources.create(targetValues()))
                .addFilterProvider(initial -> {
                    ConfigFilterFactory factory = provider.create(initial);
                    return new ConfigFilterFactory() {
                        @Override
                        public ConfigFilter create(Config raw) {
                            ConfigFilter filter = factory.create(raw);
                            if (filter.apply(Config.Key.create("services.orders.endpoint"),
                                             "https://primary.example/api")
                                    .equals("https://updated.example/api")) {
                                reconciled.countDown();
                            }
                            return filter;
                        }

                        @Override
                        public boolean startChangeSupport(Runnable callback) {
                            source.values(Map.of("services.*.endpoint", "https://updated.example/api"));
                            source.poll();
                            return factory.startChangeSupport(callback);
                        }

                        @Override
                        public void stopChangeSupport() {
                            factory.stopChangeSupport();
                        }
                    };
                }).build();
        try {
            assertThat("Startup reconciliation rebuilds automatically", reconciled.await(10, TimeUnit.SECONDS),
                       is(true));
            assertThat(target.context().reload().get("services.orders.endpoint").asString().get(),
                       is("https://updated.example/api"));
            assertThat(target.get("services.payments.endpoint").asString().get(), is("https://initial.example/api"));
        } finally {
            target.context().stopChangeSupport();
        }
    }

    @Test
    void sharedProviderCreatesIndependentDefinitionSourcesForEachRuntime() throws Exception {
        var created = new ArrayList<MutableSource>();
        var provider = OverrideConfigFilterProvider.builder().addConfigSource(() -> {
            var source = new MutableSource(Map.of("services.*.endpoint", "https://initial.example/api"));
            created.add(source);
            return source;
        }).build();
        Config first = baseBuilder().disableCaching().addSource(ConfigSources.create(targetValues()))
                .addFilterProvider(provider).build();
        Config second = baseBuilder().disableCaching().addSource(ConfigSources.create(targetValues()))
                .addFilterProvider(provider).build();
        var changes = new LinkedBlockingQueue<Config>();
        var secondChanges = new LinkedBlockingQueue<Config>();
        first.onChange(changes::add);
        second.onChange(secondChanges::add);
        try {
            assertThat(created.size(), is(2));
            created.getFirst().values(Map.of("services.*.endpoint", "https://canary.example/api"));
            created.getFirst().poll();
            awaitValue(changes, "https://canary.example/api");
            assertThat(second.context().last().get("services.orders.endpoint").asString().get(),
                       is("https://initial.example/api"));
            first.context().stopChangeSupport();
            assertThat(created.getFirst().stops.get(), is(1));
            assertThat("Stopping one runtime leaves the other monitor alive", created.getLast().stops.get(), is(0));
            created.getLast().values(Map.of("services.*.endpoint", "https://stable.example/api"));
            created.getLast().poll();
            awaitValue(secondChanges, "https://stable.example/api");
            assertThat(second.context().reload().get("services.orders.endpoint").asString().get(),
                       is("https://stable.example/api"));
        } finally {
            first.context().stopChangeSupport();
            second.context().stopChangeSupport();
        }
    }

    @Test
    void configuredProviderCreatesIndependentDescriptorMonitors(@TempDir Path directory) throws Exception {
        Path definitions = directory.resolve("service-overrides.properties");
        Instant initialModification = Instant.parse("2026-01-01T00:00:00Z");
        Files.writeString(definitions, "services.*.endpoint=https://initial.example/api\n");
        Files.setLastModifiedTime(definitions, FileTime.from(initialModification));
        Config settings = baseBuilder().addSource(ConfigSources.create(Map.of(
                "overrides.sources.0.type", "file",
                "overrides.sources.0.properties.path", definitions.toString(),
                "overrides.sources.0.properties.polling-strategy.type", "regular",
                "overrides.sources.0.properties.polling-strategy.properties.interval", "PT0.05S"))).build();
        var provider = OverrideConfigFilterProvider.builder().config(settings.get("overrides")).build();
        Config first = baseBuilder().addSource(ConfigSources.create(targetValues()))
                .addFilterProvider(provider).build();
        Config second = null;
        try {
            second = baseBuilder().addSource(ConfigSources.create(targetValues())).addFilterProvider(provider).build();
            var firstChanges = new LinkedBlockingQueue<Config>();
            var secondChanges = new LinkedBlockingQueue<Config>();
            first.onChange(firstChanges::add);
            second.onChange(secondChanges::add);
            assertThat(first.get("services.orders.endpoint").asString().get(), is("https://initial.example/api"));
            assertThat(second.get("services.orders.endpoint").asString().get(), is("https://initial.example/api"));

            Files.writeString(definitions, "services.*.endpoint=https://updated.example/api\n");
            Files.setLastModifiedTime(definitions, FileTime.from(initialModification.plusSeconds(2)));
            awaitValue(firstChanges, "https://updated.example/api");
            awaitValue(secondChanges, "https://updated.example/api");
            first.context().stopChangeSupport();

            Files.writeString(definitions, "services.*.endpoint=https://active.example/api\n");
            Files.setLastModifiedTime(definitions, FileTime.from(initialModification.plusSeconds(4)));
            Config updatedSecond = awaitValue(secondChanges, "https://active.example/api");
            assertThat(updatedSecond.get("services.payments.endpoint").asString().get(),
                       is("https://active.example/api"));
            assertThat(first.context().reload().get("services.orders.endpoint").asString().get(),
                       is("https://updated.example/api"));
            assertThat(first.get("services.payments.endpoint").asString().get(), is("https://initial.example/api"));
        } finally {
            first.context().stopChangeSupport();
            if (second != null) {
                second.context().stopChangeSupport();
            }
            settings.context().stopChangeSupport();
        }
    }

    @Test
    void concurrentRuleUpdatesConvergeToLatestSnapshot() throws Exception {
        var source = new MutableSource(Map.of("services.*.endpoint", "https://initial.example/api"));
        Config target = targetBuilder(source).build();
        var changes = new LinkedBlockingQueue<Config>();
        target.onChange(changes::add);
        try (var executor = Executors.newFixedThreadPool(4)) {
            var start = new CountDownLatch(1);
            var tasks = new ArrayList<Future<?>>();
            for (int i = 0; i < 12; i++) {
                String value = "https://rollout-" + i + ".example/api";
                tasks.add(executor.submit(() -> {
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrent updates did not start");
                    }
                    source.values(Map.of("services.*.endpoint", value));
                    source.poll();
                    return null;
                }));
            }
            start.countDown();
            for (var task : tasks) {
                task.get(10, TimeUnit.SECONDS);
            }
            source.values(Map.of("services.*.endpoint", "https://completed.example/api"));
            source.poll();
            assertThat(awaitValue(changes, "https://completed.example/api")
                               .get("services.payments.endpoint").asString().get(),
                       is("https://completed.example/api"));
            assertThat(target.get("services.payments.endpoint").asString().get(), is("https://initial.example/api"));
            assertThat(source.starts.get(), is(1));
        } finally {
            target.context().stopChangeSupport();
        }
    }

    @Test
    void stopKeepsManualReloadUsableWithLastKnownRulesWithoutRestartingMonitors() throws Exception {
        var source = new MutableSource(Map.of("services.*.endpoint", "https://initial.example/api"));
        Config target = targetBuilder(source).build();
        var changes = new LinkedBlockingQueue<Config>();
        target.onChange(changes::add);
        try {
            source.values(Map.of("services.*.endpoint", "https://active.example/api"));
            source.poll();
            awaitValue(changes, "https://active.example/api");
            target.context().stopChangeSupport();
            source.values(Map.of("services.*.endpoint", "https://pending.example/api"));
            Config updated = target.context().reload();
            assertThat(updated.get("services.orders.endpoint").asString().get(), is("https://active.example/api"));
            assertThat(target.get("services.payments.endpoint").asString().get(), is("https://initial.example/api"));
            assertThat(source.starts.get(), is(1));
            assertThat(source.stops.get(), is(1));
        } finally {
            target.context().stopChangeSupport();
        }
    }

    @Test
    void initialInlineSettingsRemainFixedAcrossTargetReloads() throws Exception {
        var targetSource = new MutableSource(Map.of("services.orders.endpoint", "https://primary.example/api",
                                                   "overrides.expressions.services.orders.endpoint",
                                                   "https://initial.example/api"));
        Config target = baseBuilder().addSource(targetSource)
                .addFilterProvider(new OverrideConfigFilterService()).build();
        var changes = new LinkedBlockingQueue<Config>();
        target.onChange(changes::add);
        try {
            targetSource.values(Map.of("services.orders.endpoint", "https://alternate.example/orders",
                                       "overrides.expressions.services.orders.endpoint",
                                       "https://updated.example/api"));
            targetSource.poll();
            Config changed = changes.poll(10, TimeUnit.SECONDS);
            assertThat("Target-source changes must be observed", changed, notNullValue());
            assertThat(changed.get("overrides.expressions.services.orders.endpoint").asString().get(),
                       is("https://updated.example/api"));
            assertThat(target.context().reload().get("services.orders.endpoint").asString().get(),
                       is("https://initial.example/api"));
            assertThat(target.get("services.orders.endpoint").asString().get(), is("https://initial.example/api"));
        } finally {
            target.context().stopChangeSupport();
        }
    }

    @Test
    void missingAndUnchangedRulesDoNotCreateNodesOrChangeEvents() {
        var source = new MutableSource(Map.of("disabled-services.*.endpoint", "https://initial.example/api"));
        Config target = targetBuilder(source).build();
        var notifications = new AtomicInteger();
        target.onChange(_ -> notifications.incrementAndGet());
        try {
            source.values(Map.of("disabled-services.*.endpoint", "https://updated.example/api"));
            source.poll();
            Config rebuilt = target.context().reload();
            assertThat(rebuilt.get("disabled-services.shipping.endpoint").exists(), is(false));
            assertThat(rebuilt.get("services.orders.endpoint").asString().get(), is("https://primary.example/orders"));
            assertThat(notifications.get(), is(0));
            source.poll();
            target.context().reload();
            assertThat(notifications.get(), is(0));
        } finally {
            target.context().stopChangeSupport();
        }
    }

    @Test
    void invalidRulesPreserveLastTargetAndValidRulesRecover() throws Exception {
        var source = new MutableSource(Map.of("services.*.endpoint", "https://initial.example/api"));
        Config target = targetBuilder(source).build();
        var changes = new LinkedBlockingQueue<Config>();
        target.onChange(changes::add);
        var warnings = new LinkedBlockingQueue<LogRecord>();
        Logger logger = Logger.getLogger("io.helidon.config.ConfigFactory");
        Level previousLevel = logger.getLevel();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                    warnings.add(record);
                }
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
        try {
            source.values(Map.of("services.[.endpoint", "https://private.example/api"));
            source.poll();
            LogRecord warning = warnings.poll(10, TimeUnit.SECONDS);
            assertThat("Invalid rules must produce an observable failure", warning, notNullValue());
            assertThat(warning.getLevel(), is(Level.WARNING));
            assertThat(warning.getMessage(),
                       is("Cannot reload configuration after a config filter change; "
                                  + "the previous configuration remains available."));
            assertThat("Failure diagnostics must not expose exception details", warning.getThrown(), nullValue());
            assertThat(target.context().last().get("services.orders.endpoint").asString().get(),
                       is("https://initial.example/api"));
            source.values(Map.of("services.*.endpoint", "https://recovered.example/api"));
            source.poll();
            assertThat(awaitValue(changes, "https://recovered.example/api")
                               .get("services.payments.endpoint").asString().get(),
                       is("https://recovered.example/api"));
            assertThat(target.get("services.payments.endpoint").asString().get(), is("https://initial.example/api"));
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(previousLevel);
            target.context().stopChangeSupport();
        }
    }

    private static Config awaitValue(LinkedBlockingQueue<Config> changes, String value) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (true) {
            Config changed = changes.poll(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            assertThat("Expected target change to " + value, changed, notNullValue());
            if (changed.get("services.orders.endpoint").asString().get().equals(value)) {
                return changed;
            }
        }
    }

    private static Config.Builder targetBuilder(MutableSource source) {
        return baseBuilder().addSource(ConfigSources.create(targetValues()))
                .addFilterProvider(OverrideConfigFilterProvider.builder().addConfigSource(source).build());
    }

    private static Config.Builder baseBuilder() {
        return Config.builder().disableEnvironmentVariablesSource().disableSystemPropertiesSource()
                .disableFilterServices().changesExecutor(Runnable::run);
    }

    private static Map<String, String> targetValues() {
        return Map.of("services.orders.endpoint", "https://primary.example/orders",
                      "services.payments.endpoint", "https://primary.example/payments");
    }

    private static final class MutableSource implements NodeConfigSource, PollableSource<Map<String, String>> {
        private final AtomicInteger starts = new AtomicInteger();
        private final AtomicInteger stops = new AtomicInteger();
        private final PollingStrategy strategy = new PollingStrategy() {
            @Override
            public void start(Polled callback) {
                starts.incrementAndGet();
                polled = callback;
            }

            @Override
            public void stop() {
                stops.incrementAndGet();
                polled = null;
            }
        };
        private volatile Map<String, String> values;
        private volatile PollingStrategy.Polled polled;

        private MutableSource(Map<String, String> values) {
            values(values);
        }

        void values(Map<String, String> values) {
            this.values = Map.copyOf(values);
        }

        void poll() {
            PollingStrategy.Polled callback = polled;
            if (callback == null) {
                throw new IllegalStateException("Change support has not started");
            }
            callback.poll(Instant.now());
        }

        @Override
        public boolean isModified(Map<String, String> stamp) {
            return !values.equals(stamp);
        }

        @Override
        public Optional<PollingStrategy> pollingStrategy() {
            return Optional.of(strategy);
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
