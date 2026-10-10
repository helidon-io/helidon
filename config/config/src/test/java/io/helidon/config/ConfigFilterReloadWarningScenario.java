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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.helidon.config.spi.ChangeWatcher;
import io.helidon.config.spi.ConfigContent.NodeContent;
import io.helidon.config.spi.ConfigFilter;
import io.helidon.config.spi.ConfigFilterFactory;
import io.helidon.config.spi.ConfigFilterProvider;
import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.spi.EventConfigSource;
import io.helidon.config.spi.NodeConfigSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

/**
 * Each scenario runs in a fresh JVM without resetting or inspecting the once-only guard.
 */
@SuppressWarnings("removal")
public final class ConfigFilterReloadWarningScenario {
    private static final String WARNING = "Reloading configuration with shared ConfigFilter instances may create "
            + "inconsistent filter state. Use ConfigFilterProvider for configuration-dependent filters.";
    private static final ConfigFilter IDENTITY = (_, value) -> value;

    private ConfigFilterReloadWarningScenario() {
    }

    public static void main(String[] args) throws Exception {
        var warnings = new WarningHandler();
        Logger logger = Logger.getLogger(ConfigFactory.class.getName());
        logger.setLevel(Level.WARNING);
        logger.setUseParentHandlers(false);
        logger.addHandler(warnings);
        try {
            switch (args[0]) {
            case "unused" -> unused(warnings);
            case "factories" -> factories(warnings);
            case "manual" -> manual(warnings);
            case "polling" -> automatic(warnings, pollingBuilder());
            case "watching" -> automatic(warnings, watchingBuilder(Path.of(args[1])));
            case "events" -> automatic(warnings, builder().sources(new EventSource()));
            case "overrides" -> automatic(warnings, overridesBuilder(Path.of(args[1])));
            case "provider" -> automatic(warnings, builder().addFilterProvider(monitoredProvider()));
            case "concurrent" -> concurrent(warnings);
            default -> throw new IllegalArgumentException("Unknown scenario: " + args[0]);
            }
        } finally {
            logger.removeHandler(warnings);
        }
    }

    private static Config.Builder builder() {
        return Config.builder()
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableFilterServices()
                .sources(ConfigSources.create(Map.of("private.password", "secret-original")));
    }

    private static Config.Builder pollingBuilder() {
        return builder().sources(TestingConfigSource.builder()
                                         .testingPollingStrategy()
                                         .objectNode(ObjectNode.builder().addValue("private.password", "secret-original").build())
                                         .build());
    }

    private static ConfigFilterProvider monitoredProvider() {
        return _ -> new ConfigFilterFactory() {
            @Override
            public ConfigFilter create(Config config) {
                return (_, value) -> value;
            }

            @Override
            public boolean startChangeSupport(Runnable requestReload) {
                return true;
            }
        };
    }

    private static void unused(WarningHandler warnings) {
        Config config = builder().addFilter(IDENTITY).build();
        try {
            config.onChange(_ -> { });
            assertThat(config.get("private.password").asString().get(), is("secret-original"));
            warnings.assertCount(0);
        } finally {
            config.context().stopChangeSupport();
        }
    }

    private static void factories(WarningHandler warnings) {
        Config config = pollingBuilder()
                .addFilter(_ -> (_, value) -> value)
                .addFilter(() -> _ -> (_, value) -> value)
                .addFilterProvider(monitoredProvider())
                .build();
        try {
            config.context().reload();
            warnings.assertCount(0);
        } finally {
            config.context().stopChangeSupport();
        }
    }

    private static void manual(WarningHandler warnings) {
        var initializations = new AtomicInteger();
        ConfigFilter filter = new ConfigFilter() {
            @Override
            public void init(Config config) {
                initializations.incrementAndGet();
            }

            @Override
            public String apply(Config.Key key, String value) {
                return value + initializations.get();
            }
        };
        Config config = builder().disableCaching().addFilter(filter).build();
        try {
            assertThat(config.get("private.password").asString().get(), is("secret-original1"));
            warnings.assertCount(0);
            Config updated = config.context().reload();
            assertThat(updated.get("private.password").asString().get(), is("secret-original2"));
            // Direct-instance behavior is retained during migration, including the old uncached snapshot.
            assertThat(config.get("private.password").asString().get(), is("secret-original2"));
            config.context().reload();
            assertThat(initializations.get(), is(3));
            warnings.assertCount(1);
        } finally {
            config.context().stopChangeSupport();
        }
    }

    private static void automatic(WarningHandler warnings, Config.Builder builder) {
        Config config = builder.addFilter(IDENTITY).build();
        try {
            warnings.assertCount(1);
            config.context().reload();
            warnings.assertCount(1);
        } finally {
            config.context().stopChangeSupport();
        }
    }

    private static Config.Builder watchingBuilder(Path directory) throws Exception {
        Path source = directory.resolve("private-source.properties");
        Files.writeString(source, "private.password=secret-original\n");
        ChangeWatcher<Path> watcher = new ChangeWatcher<>() {
            @Override
            public void start(Path target, Consumer<ChangeEvent<Path>> listener) {
            }

            @Override
            public Class<Path> type() {
                return Path.class;
            }
        };
        return builder().sources(ConfigSources.file(source).changeWatcher(watcher));
    }

    private static Config.Builder overridesBuilder(Path directory) throws Exception {
        Path source = directory.resolve("private-overrides.properties");
        Files.writeString(source, "private.password=secret-replacement\n");
        return builder().overrides(OverrideSources.file(source.toString()).pollingStrategy(_ -> { }));
    }

    private static void concurrent(WarningHandler warnings) throws Exception {
        int tasks = 24;
        var ready = new CountDownLatch(tasks);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(tasks)) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < tasks; i++) {
                boolean automatic = i % 2 == 0;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertThat("Release concurrent setup", start.await(10, TimeUnit.SECONDS), is(true));
                    Config config = (automatic ? pollingBuilder() : builder()).addFilter(IDENTITY).build();
                    try {
                        config.context().reload();
                    } finally {
                        config.context().stopChangeSupport();
                    }
                    return null;
                }));
            }
            try {
                assertThat("All setup workers ready", ready.await(10, TimeUnit.SECONDS), is(true));
            } finally {
                start.countDown();
            }
            for (Future<?> future : futures) {
                future.get(20, TimeUnit.SECONDS);
            }
        }
        warnings.assertCount(1);
    }

    private static final class EventSource implements NodeConfigSource, EventConfigSource {
        @Override
        public Optional<NodeContent> load() {
            return Optional.of(NodeContent.builder()
                                       .node(ObjectNode.builder().addValue("private.password", "secret-original").build())
                                       .build());
        }

        @Override
        public void onChange(BiConsumer<String, ConfigNode> changedNode) {
        }
    }

    private static final class WarningHandler extends Handler {
        private final List<LogRecord> records = new CopyOnWriteArrayList<>();

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

        void assertCount(int count) {
            assertThat("Legacy filter reload warnings", records, hasSize(count));
            for (LogRecord record : records) {
                assertThat(record.getLevel(), is(Level.WARNING));
                assertThat("No source keys, values, paths or filter names", record.getMessage(), is(WARNING));
                assertThat("No potentially sensitive exception", record.getThrown(), nullValue());
            }
        }
    }
}
