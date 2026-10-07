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
import io.helidon.config.spi.ConfigFilter;
import io.helidon.config.spi.ConfigFilterFactory;
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
    void definitionChangesReloadTargetAndPreserveOldSnapshots(boolean caching) throws Exception {
        var source = new MutableSource(Map.of("service.*.level", "first"));
        Config.Builder builder = targetBuilder(source);
        if (!caching) {
            builder.disableCaching();
        }
        Config target = builder.build();
        var changes = new LinkedBlockingQueue<Config>();
        target.onChange(changes::add);
        try {
            assertThat(target.get("service.alpha.level").asString().get(), is("first"));
            source.values(Map.of("service.*.level", "second"));
            source.poll();
            Config updated = awaitValue(changes, "second");
            assertThat(updated.get("service.beta.level").asString().get(), is("second"));
            assertThat(target.get("service.alpha.level").asString().get(), is("first"));
            assertThat("Unread old nodes retain their generation's rules",
                       target.get("service.beta.level").asString().get(), is("first"));
            assertThat("Rebuilding does not initialize definition monitoring again", source.starts.get(), is(1));
        } finally {
            target.context().stopChangeSupport();
        }
    }

    @Test
    void startupReconcilesChangesBeforeCallbackRegistration() throws Exception {
        var source = new MutableSource(Map.of("service.*.level", "first"));
        var reconciled = new CountDownLatch(1);
        var provider = OverrideConfigFilter.builder().addConfigSource(source).buildProvider();
        Config target = baseBuilder().disableCaching().addSource(ConfigSources.create(targetValues()))
                .addFilterProvider(initial -> {
                    ConfigFilterFactory factory = provider.create(initial);
                    return new ConfigFilterFactory() {
                        @Override
                        public ConfigFilter create(Config raw) {
                            ConfigFilter filter = factory.create(raw);
                            if (filter.apply(Config.Key.create("service.alpha.level"), "original").equals("second")) {
                                reconciled.countDown();
                            }
                            return filter;
                        }

                        @Override
                        public boolean startChangeSupport(Runnable callback) {
                            source.values(Map.of("service.*.level", "second"));
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
            assertThat("Startup reconciliation rebuilds automatically", reconciled.await(10, TimeUnit.SECONDS), is(true));
            assertThat(target.context().reload().get("service.alpha.level").asString().get(), is("second"));
            assertThat(target.get("service.beta.level").asString().get(), is("first"));
        } finally {
            target.context().stopChangeSupport();
        }
    }

    @Test
    void sharedProviderCreatesIndependentDefinitionSourcesForEachRuntime() throws Exception {
        var created = new ArrayList<MutableSource>();
        var provider = OverrideConfigFilter.builder().addConfigSource(() -> {
            var source = new MutableSource(Map.of("service.*.level", "first"));
            created.add(source);
            return source;
        }).buildProvider();
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
            created.getFirst().values(Map.of("service.*.level", "updated-first"));
            created.getFirst().poll();
            awaitValue(changes, "updated-first");
            assertThat(second.context().last().get("service.alpha.level").asString().get(), is("first"));
            first.context().stopChangeSupport();
            assertThat(created.getFirst().stops.get(), is(1));
            assertThat("Stopping one runtime leaves the other monitor alive", created.getLast().stops.get(), is(0));
            created.getLast().values(Map.of("service.*.level", "updated-second"));
            created.getLast().poll();
            awaitValue(secondChanges, "updated-second");
            assertThat(second.context().reload().get("service.alpha.level").asString().get(), is("updated-second"));
        } finally {
            first.context().stopChangeSupport();
            second.context().stopChangeSupport();
        }
    }

    @Test
    void concurrentRuleUpdatesConvergeToLatestSnapshot() throws Exception {
        var source = new MutableSource(Map.of("service.*.level", "first"));
        Config target = targetBuilder(source).build();
        var changes = new LinkedBlockingQueue<Config>();
        target.onChange(changes::add);
        try (var executor = Executors.newFixedThreadPool(4)) {
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
            assertThat(target.get("service.beta.level").asString().get(), is("first"));
            assertThat(source.starts.get(), is(1));
        } finally {
            target.context().stopChangeSupport();
        }
    }

    @Test
    void stopKeepsManualReloadUsableWithLastKnownRulesWithoutRestartingMonitors() throws Exception {
        var source = new MutableSource(Map.of("service.*.level", "first"));
        Config target = targetBuilder(source).build();
        var changes = new LinkedBlockingQueue<Config>();
        target.onChange(changes::add);
        try {
            source.values(Map.of("service.*.level", "known-before-stop"));
            source.poll();
            awaitValue(changes, "known-before-stop");
            target.context().stopChangeSupport();
            source.values(Map.of("service.*.level", "unobserved-after-stop"));
            Config updated = target.context().reload();
            assertThat(updated.get("service.alpha.level").asString().get(), is("known-before-stop"));
            assertThat(target.get("service.beta.level").asString().get(), is("first"));
            assertThat(source.starts.get(), is(1));
            assertThat(source.stops.get(), is(1));
        } finally {
            target.context().stopChangeSupport();
        }
    }

    @Test
    void initialInlineSettingsRemainFixedAcrossTargetReloads() throws Exception {
        var targetSource = new MutableSource(Map.of("service.alpha.level", "original",
                                                   "overrides.expressions.service.alpha.level", "first"));
        Config target = baseBuilder().addSource(targetSource).addFilterProvider(new OverrideConfigFilterProvider()).build();
        var changes = new LinkedBlockingQueue<Config>();
        target.onChange(changes::add);
        try {
            targetSource.values(Map.of("service.alpha.level", "changed-original",
                                       "overrides.expressions.service.alpha.level", "second"));
            targetSource.poll();
            Config changed = changes.poll(10, TimeUnit.SECONDS);
            assertThat("Target-source changes must be observed", changed, notNullValue());
            assertThat(changed.get("overrides.expressions.service.alpha.level").asString().get(), is("second"));
            assertThat(target.context().reload().get("service.alpha.level").asString().get(), is("first"));
            assertThat(target.get("service.alpha.level").asString().get(), is("first"));
        } finally {
            target.context().stopChangeSupport();
        }
    }

    @Test
    void missingAndUnchangedRulesDoNotCreateNodesOrChangeEvents() {
        var source = new MutableSource(Map.of("missing.*.level", "first"));
        Config target = targetBuilder(source).build();
        var notifications = new AtomicInteger();
        target.onChange(_ -> notifications.incrementAndGet());
        try {
            source.values(Map.of("missing.*.level", "second"));
            source.poll();
            Config rebuilt = target.context().reload();
            assertThat(rebuilt.get("missing.gamma.level").exists(), is(false));
            assertThat(rebuilt.get("service.alpha.level").asString().get(), is("original-alpha"));
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
        var source = new MutableSource(Map.of("service.*.level", "first"));
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
            source.values(Map.of("[", "private-replacement-value"));
            source.poll();
            LogRecord warning = warnings.poll(10, TimeUnit.SECONDS);
            assertThat("Invalid rules must produce an observable failure", warning, notNullValue());
            assertThat(warning.getLevel(), is(Level.WARNING));
            assertThat(warning.getMessage(),
                       is("Cannot reload configuration after a config filter change; the previous configuration remains available."));
            assertThat("Failure diagnostics must not expose exception details", warning.getThrown(), nullValue());
            assertThat(target.context().last().get("service.alpha.level").asString().get(), is("first"));
            source.values(Map.of("service.*.level", "recovered"));
            source.poll();
            assertThat(awaitValue(changes, "recovered").get("service.beta.level").asString().get(), is("recovered"));
            assertThat(target.get("service.beta.level").asString().get(), is("first"));
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
            if (changed.get("service.alpha.level").asString().get().equals(value)) {
                return changed;
            }
        }
    }

    private static Config.Builder targetBuilder(MutableSource source) {
        return baseBuilder().addSource(ConfigSources.create(targetValues()))
                .addFilterProvider(OverrideConfigFilter.builder().addConfigSource(source).buildProvider());
    }

    private static Config.Builder baseBuilder() {
        return Config.builder().disableEnvironmentVariablesSource().disableSystemPropertiesSource()
                .disableFilterServices().changesExecutor(Runnable::run);
    }

    private static Map<String, String> targetValues() {
        return Map.of("service.alpha.level", "original-alpha", "service.beta.level", "original-beta");
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
