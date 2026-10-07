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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.OverrideSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

/**
 * Fresh-JVM scenarios for the observable runtime warning. No production state is reset or inspected.
 */
@SuppressWarnings("removal")
public final class OverrideDeprecationScenario {
    private static final String WARNING = "Legacy Helidon Config overrides APIs are deprecated in favor of the optional "
            + "overrides filter module. "
            + "If you use this feature, please report your usage at https://github.com/helidon-io/helidon/issues/10415. "
            + "Removal timing has not been decided.";

    private OverrideDeprecationScenario() {
    }

    public static void main(String[] args) throws Exception {
        var warnings = new WarningHandler();
        Logger logger = Logger.getLogger(OverrideConfigFilter.class.getName());
        logger.setLevel(Level.ALL);
        logger.setUseParentHandlers(false);
        logger.addHandler(warnings);
        Path overrides = Path.of(args[1], "private-overrides.properties");
        Files.writeString(overrides, "private.password=secret-replacement\n");
        try {
            switch (args[0]) {
            case "unused" -> unused(warnings, overrides);
            case "builder" -> preservedBehavior();
            case "meta" -> meta(overrides);
            case "direct" -> direct();
            case "concurrent" -> concurrent(overrides);
            case "reload" -> reload(warnings);
            case "late-definitions" -> lateDefinitions(warnings);
            default -> throw new IllegalArgumentException("Unknown scenario: " + args[0]);
            }
            if (!args[0].equals("unused")) {
                warnings.assertCount(1);
                // Exercise all entry points after the first application, checking the shared runtime limit.
                preservedBehavior();
                meta(overrides);
                direct();
                warnings.assertCount(1);
            }
        } finally {
            logger.removeHandler(warnings);
        }
    }

    private static Config.Builder builder(Map<String, String> values) {
        return Config.builder()
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableFilterServices()
                .sources(ConfigSources.create(values));
    }

    private static void unused(WarningHandler warnings, Path overrides) throws Exception {
        OverrideSources.create(Map.of("private.password", "secret-replacement"));
        OverrideSources.file(overrides.toString()).build();
        OverrideSources.url(overrides.toUri().toURL()).build();
        OverrideSources.classpath("unused.properties").build();
        assertThat(builder(Map.of("private.password", "secret-original")).build()
                           .get("private.password").asString().get(), is("secret-original"));
        Config empty = builder(Map.of("private.password", "secret-original")).overrides(OverrideSources.empty()).build();
        assertThat(empty.get("private.password").asString().get(), is("secret-original"));
        Config unmatched = builder(Map.of("private.password", "secret-original"))
                .overrides(OverrideSources.create(Map.of("missing.*", "secret-replacement"))).build();
        assertThat(unmatched.get("private.password").asString().get(), is("secret-original"));
        assertThat(unmatched.get("missing.password").exists(), is(false));
        assertThat(new OverrideConfigFilter(List::of).apply(Config.Key.create("private.password"), "secret-original"),
                   is("secret-original"));
        assertThat(new OverrideConfigFilter(() -> null).apply(Config.Key.create("private.password"), "secret-original"),
                   is("secret-original"));
        warnings.assertCount(0);
    }

    private static void preservedBehavior() {
        Map<String, String> rules = new LinkedHashMap<>();
        rules.put("test.first.password", "secret-first");
        rules.put("test.*.password", "secret-wildcard");
        rules.put("missing.*", "secret-missing");
        Config config = builder(Map.of("env", "test", "${env}.first.password", "secret-original",
                                       "${env}.second.password", "secret-original", "untouched", "ordinary"))
                .overrides(OverrideSources.create(rules))
                .addFilter((key, value) -> key.toString().endsWith("password") ? value + "-filtered" : value)
                .build();
        assertThat(config.get("test.first.password").asString().get(), is("secret-first-filtered"));
        assertThat(config.get("test.second.password").asString().get(), is("secret-wildcard-filtered"));
        assertThat(config.get("untouched").asString().get(), is("ordinary"));
        assertThat(config.get("test.third.password").exists(), is(false));
        assertThat(config.get("missing.password").exists(), is(false));
        assertThat(config.asMap().get().size(), is(4));
    }

    private static void meta(Path overrides) {
        Config metadata = builder(Map.of("override-source.type", "file",
                                         "override-source.properties.path", overrides.toString())).build();
        Config config = Config.builder().config(metadata).disableFilterServices()
                .sources(ConfigSources.create(Map.of("private.password", "secret-original"))).build();
        assertThat(config.get("private.password").asString().get(), is("secret-replacement"));
    }

    private static void direct() {
        OverrideConfigFilter filter = new OverrideConfigFilter(() -> OverrideSource.OverrideData.createFromWildcards(
                List.of(Map.entry("private.*", "secret-original"))).data());
        // Matching an equal value still uses the feature.
        assertThat(filter.apply(Config.Key.create("private.password"), "secret-original"), is("secret-original"));
    }

    private static void concurrent(Path overrides) throws Exception {
        int tasks = 24;
        CountDownLatch ready = new CountDownLatch(tasks);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(tasks)) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < tasks; i++) {
                int entryPoint = i % 3;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertThat("Concurrent workers start together", start.await(10, TimeUnit.SECONDS), is(true));
                    switch (entryPoint) {
                    case 0 -> preservedBehavior();
                    case 1 -> meta(overrides);
                    case 2 -> direct();
                    default -> throw new AssertionError(entryPoint);
                    }
                    return null;
                }));
            }
            try {
                assertThat("All concurrent workers ready", ready.await(10, TimeUnit.SECONDS), is(true));
            } finally {
                start.countDown();
            }
            for (Future<?> future : futures) {
                future.get(20, TimeUnit.SECONDS);
            }
        }
    }

    private static void reload(WarningHandler warnings) throws Exception {
        TestingConfigSource source = TestingConfigSource.builder().testingPollingStrategy()
                .objectNode(ConfigNode.ObjectNode.builder().addValue("ordinary", "initial").build()).build();
        Config config = Config.builder().disableEnvironmentVariablesSource().disableSystemPropertiesSource()
                .disableFilterServices().sources(source)
                .overrides(OverrideSources.create(Map.of("private.*", "secret-replacement"))).build();
        assertThat(config.get("ordinary").asString().get(), is("initial"));
        warnings.assertCount(0);
        CompletableFuture<Config> changed = new CompletableFuture<>();
        config.onChange(changed::complete);
        source.changeLoadedObjectNode(ConfigNode.ObjectNode.builder().addValue("private.password", "secret-original").build());
        Config updated = changed.get(10, TimeUnit.SECONDS);
        assertThat(updated.get("private.password").asString().get(), is("secret-replacement"));
        warnings.assertCount(1);
        CompletableFuture<Config> next = new CompletableFuture<>();
        updated.onChange(next::complete);
        source.changeLoadedObjectNode(ConfigNode.ObjectNode.builder()
                                              .addValue("private.password", "another-secret")
                                              .addValue("ordinary", "second")
                                              .build());
        assertThat(next.get(10, TimeUnit.SECONDS).get("private.password").asString().get(), is("secret-replacement"));
        warnings.assertCount(1);
    }

    private static void lateDefinitions(WarningHandler warnings) {
        AtomicReference<List<Map.Entry<Predicate<Config.Key>, String>>> definitions = new AtomicReference<>(List.of());
        OverrideConfigFilter filter = new OverrideConfigFilter(definitions::get);
        assertThat(filter.apply(Config.Key.create("private.password"), "secret-original"), is("secret-original"));
        warnings.assertCount(0);
        definitions.set(OverrideSource.OverrideData.createFromWildcards(List.of(Map.entry("private.*", "secret-first"))).data());
        assertThat(filter.apply(Config.Key.create("private.password"), "secret-original"), is("secret-first"));
        warnings.assertCount(1);
        definitions.set(OverrideSource.OverrideData.createFromWildcards(List.of(Map.entry("private.*", "secret-second"))).data());
        assertThat(filter.apply(Config.Key.create("private.password"), "secret-original"), is("secret-second"));
        warnings.assertCount(1);
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
            assertThat("Override warning records", records, hasSize(count));
            for (LogRecord record : records) {
                assertThat(record.getLevel(), is(Level.WARNING));
                assertThat("Warning includes no user keys, values or paths", record.getMessage(), is(WARNING));
            }
        }
    }
}
