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
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.config.spi.ConfigFilter;
import io.helidon.config.spi.ConfigFilterContext;
import io.helidon.config.spi.ConfigFilterProvider;
import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.LazyConfigSource;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FilterFactoryLifecycleTest {
    @Test
    void constructionSignalsWaitForInitializationAndRecreateEveryFilter() throws InterruptedException {
        AtomicInteger firstFilters = new AtomicInteger();
        AtomicInteger secondFilters = new AtomicInteger();
        AtomicBoolean initialized = new AtomicBoolean();
        CountDownLatch reloaded = new CountDownLatch(1);
        Config config = builder().disableCaching().addFilterProvider(context -> {
            context.reload();
            assertThat("Provider construction did not recursively create filters", firstFilters.get(), is(0));
            return _ -> {
                int generation = firstFilters.incrementAndGet();
                if (generation == 1) {
                    context.reload();
                    assertThat("Filter construction did not recursively create another filter", firstFilters.get(), is(1));
                } else {
                    assertThat("Reload waited for every factory to initialize", initialized.get(), is(true));
                }
                return (_, value) -> value + ":first" + generation;
            };
        }).addFilterProvider(_ -> _ -> {
            int generation = secondFilters.incrementAndGet();
            initialized.set(true);
            if (generation == 2) {
                reloaded.countDown();
            }
            return (_, value) -> value + ":second" + generation;
        }).build();
        try {
            assertThat("Construction requests produced a reload", reloaded.await(10, TimeUnit.SECONDS), is(true));
            config.context().stopChangeSupport();
            assertThat("Pending construction requests were coalesced", firstFilters.get(), is(2));
            assertThat("Every factory recreated its filter", secondFilters.get(), is(2));
            assertThat(config.context().last().get("nested.value").asString().get(), is("raw:first2:second2"));
            assertThat("Original uncached snapshot retained both original filters",
                       config.get("nested.value").asString().get(), is("raw:first1:second1"));
        } finally {
            config.context().stopChangeSupport();
        }
    }

    @Test
    void retainedContextsReloadOnlyTheirOwnRuntime() throws InterruptedException {
        List<ConfigFilterContext> contexts = new ArrayList<>();
        Config.Builder builder = builder().addFilterProvider(context -> {
            contexts.add(context);
            AtomicInteger filters = new AtomicInteger();
            return _ -> {
                int captured = filters.incrementAndGet();
                return (_, value) -> value + captured;
            };
        });
        Config first = builder.build();
        Config second = builder.build();
        CountDownLatch changed = new CountDownLatch(1);
        try {
            first.onChange(_ -> changed.countDown());
            second.onChange(_ -> { throw new AssertionError("Other runtime received a context reload"); });
            contexts.getFirst().reload();
            assertThat("First runtime delivered its context reload", changed.await(10, TimeUnit.SECONDS), is(true));
            first.context().stopChangeSupport();
            second.context().stopChangeSupport();
            assertThat(first.context().last().get("nested.value").asString().get(), is("raw2"));
            assertThat("Second runtime retained its initial filter",
                       second.context().last().get("nested.value").asString().get(), is("raw1"));
            assertThat("First snapshot retained its original filter", first.get("nested.value").asString().get(), is("raw1"));
        } finally {
            first.context().stopChangeSupport();
            second.context().stopChangeSupport();
        }
    }

    @Test
    void rawViewAndGenerationIsolation() {
        AtomicReference<Config> raw = new AtomicReference<>();
        AtomicInteger generation = new AtomicInteger();
        Config config = builder().disableCaching().addFilterProvider(initial -> {
            raw.set(initial.initialConfig());
            return current -> {
                String captured = current.get("nested.value").asString().get() + generation.incrementAndGet();
                return (key, value) -> key.toString().equals("nested.value") ? captured : value;
            };
        }).build();
        assertThat(config.get("nested.value").asString().get(), is("raw1"));
        Config snapshot = raw.get();
        assertThat(snapshot.get("nested").detach().get("value").asString().get(), is("raw"));
        assertThat(snapshot.context().reload(), sameInstance(snapshot));
        assertThat(snapshot.get("nested").context().last(), sameInstance(snapshot.get("nested")));
        snapshot.onChange(_ -> { throw new AssertionError("Raw view received a change"); });
        snapshot.context().stopChangeSupport();
        Config next = config.context().reload();
        assertThat(next.get("nested.value").asString().get(), is("raw2"));
        assertThat(config.get("nested.value").asString().get(), is("raw1"));
        config.context().stopChangeSupport();
    }

    @Test
    void sharedProviderCreatesIndependentFactoriesAndNeverInitializesProducts() {
        AtomicInteger factories = new AtomicInteger();
        ConfigFilterProvider provider = _ -> {
            int runtime = factories.incrementAndGet();
            AtomicInteger generation = new AtomicInteger();
            return _ -> {
                String captured = runtime + ":" + generation.incrementAndGet();
                return new ConfigFilter() {
                    @Override
                    public String apply(Config.Key key, String value) {
                        return captured;
                    }

                    @Override
                    public void init(Config config) {
                        throw new AssertionError("Provider filter init invoked");
                    }
                };
            };
        };
        Config.Builder builder = builder().addFilterProvider(provider);
        Config first = builder.build();
        Config second = builder.build();
        assertThat(first.get("nested.value").asString().get(), is("1:1"));
        assertThat(second.get("nested.value").asString().get(), is("2:1"));
        assertThat(first.context().reload().get("nested.value").asString().get(), is("1:2"));
        assertThat(second.get("nested.value").asString().get(), is("2:1"));
        first.context().stopChangeSupport();
        second.context().stopChangeSupport();
    }

    @Test
    void startupSignalIsDeliveredAndStopLeavesManualReloadUsable() throws InterruptedException {
        AtomicInteger generation = new AtomicInteger();
        AtomicInteger starts = new AtomicInteger();
        AtomicInteger stops = new AtomicInteger();
        AtomicReference<ConfigFilterContext> signal = new AtomicReference<>();
        CountDownLatch reloaded = new CountDownLatch(1);
        Config config = builder().addFilterProvider(context -> {
            starts.incrementAndGet();
            signal.set(context);
            context.onChange(new Config.ChangeListener() {
                @Override
                public void onChange(Config config) {
                }

                @Override
                public void onStop() {
                    stops.incrementAndGet();
                }
            });
            context.reload();
            return _ -> {
                int current = generation.incrementAndGet();
                if (current == 2) {
                    reloaded.countDown();
                }
                return (_, value) -> value + current;
            };
        }).build();
        assertThat("Startup reload created a replacement filter", reloaded.await(10, TimeUnit.SECONDS), is(true));
        config.context().stopChangeSupport();
        assertThat(config.context().last().get("nested.value").asString().get(), is("raw2"));
        signal.get().reload();
        assertThat(config.context().reload().get("nested.value").asString().get(), is("raw3"));
        config.context().stopChangeSupport();
        assertThat(starts.get(), is(1));
        assertThat(stops.get(), is(1));
        assertThat("Stopped context did not schedule another reload", generation.get(), is(3));
    }

    @Test
    void failedConstructionCleansUpEveryCreatedFactory() {
        AtomicInteger stopped = new AtomicInteger();
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                                                    () -> builder().addFilterProvider(context -> {
                                                        context.onChange(new Config.ChangeListener() {
                                                            @Override
                                                            public void onChange(Config config) {
                                                            }

                                                            @Override
                                                            public void onStop() {
                                                                stopped.incrementAndGet();
                                                            }
                                                        });
                                                        context.reload();
                                                        return _ -> {
                                                            throw new IllegalStateException("Cannot create filter");
                                                        };
                                                    }).build());
        assertThat("Pending reload did not replace the construction failure", failure.getMessage(), is("Cannot create filter"));
        assertThat(stopped.get(), is(1));
    }

    @Test
    void concurrentReloadsSerializeFactoryCreation() throws Exception {
        AtomicInteger generations = new AtomicInteger();
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        Config config = builder().disableCaching().addFilterProvider(_ -> _ -> {
            maximum.accumulateAndGet(active.incrementAndGet(), Math::max);
            try {
                int captured = generations.incrementAndGet();
                return (_, value) -> value + captured;
            } finally {
                active.decrementAndGet();
            }
        }).build();
        CountDownLatch gate = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(12)) {
            List<Future<Config>> results = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                results.add(executor.submit(() -> {
                    gate.await();
                    return config.context().reload();
                }));
            }
            gate.countDown();
            for (Future<Config> result : results) {
                result.get(10, TimeUnit.SECONDS);
            }
        } finally {
            config.context().stopChangeSupport();
        }
        assertThat(maximum.get(), is(1));
        assertThat(generations.get(), is(13));
        assertThat(config.get("nested.value").asString().get(), is("raw1"));
    }

    @Test
    void nullFilterOnReloadRetainsLastGoodGeneration() {
        AtomicInteger generations = new AtomicInteger();
        Config config = builder().addFilterProvider(_ -> _ -> {
            int generation = generations.incrementAndGet();
            return generation == 2 ? null : (_, value) -> value + generation;
        }).build();
        try {
            assertThrows(NullPointerException.class, () -> config.context().reload());
            assertThat(config.context().last(), sameInstance(config));
            assertThat(config.get("nested.value").asString().get(), is("raw1"));
            assertThat(config.context().reload().get("nested.value").asString().get(), is("raw3"));
        } finally {
            config.context().stopChangeSupport();
        }
    }

    @Test
    void rawViewAllowsLazySourceReadsWithoutFilteringThem() {
        AtomicReference<String> sourceValue = new AtomicReference<>("first");
        AtomicReference<Config> raw = new AtomicReference<>();
        Config config = builder().addSource(new LazySource(sourceValue)).addFilterProvider(initial -> {
            raw.set(initial.initialConfig());
            return current -> {
                String captured = current.get("lazy.value").asString().get();
                return (_, value) -> captured;
            };
        }).build();
        try {
            assertThat(config.get("nested.value").asString().get(), is("first"));
            assertThat(raw.get().get("lazy.value").asString().get(), is("first"));
            sourceValue.set("second");
            assertThat(raw.get().get("lazy.unread").asString().get(), is("second"));
            assertThat(config.context().reload().get("nested.value").asString().get(), is("first"));
            assertThat(config.get("nested.value").asString().get(), is("first"));
        } finally {
            config.context().stopChangeSupport();
        }
    }

    private Config.Builder builder() {
        return Config.builder(ConfigSources.create(Map.of("nested.value", "raw")))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableFilterServices();
    }

    private static class LazySource implements ConfigSource, LazyConfigSource {
        private final AtomicReference<String> value;

        private LazySource(AtomicReference<String> value) {
            this.value = value;
        }

        @Override
        public Optional<ConfigNode> node(String key) {
            return key.startsWith("lazy.") ? Optional.of(ConfigNode.ValueNode.create(value.get())) : Optional.empty();
        }
    }
}
