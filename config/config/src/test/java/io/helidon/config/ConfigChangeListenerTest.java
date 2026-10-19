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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.config.spi.ConfigFilterContext;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigChangeListenerTest {
    @Test
    void listenerRemainsAFunctionalConsumer() {
        AtomicReference<Config> received = new AtomicReference<>();
        Config.ChangeListener listener = received::set;
        Config config = Config.empty();

        listener.accept(config);
        listener.onStop();

        assertThat(received.get(), sameInstance(config));
    }

    @Test
    void oldSnapshotsAndMappedNodesShareChangesAndStopNotification() {
        AtomicInteger generation = new AtomicInteger();
        Config config = builder().addFilterProvider(_ -> _ -> {
            int current = generation.incrementAndGet();
            return (_, value) -> value + current;
        }).build();
        RecordingListener root = new RecordingListener();
        RecordingListener child = new RecordingListener();
        RecordingListener detached = new RecordingListener();
        AtomicReference<Config> consumer = new AtomicReference<>();
        try {
            config.onChange(root);
            config.get("nested").onChange(child);
            config.get("nested").detach().onChange(detached);
            config.onChange(consumer::set);

            Config second = config.context().reload();
            assertThat(root.changed.get(), sameInstance(second));
            assertThat(consumer.get(), sameInstance(second));
            assertThat(child.changed.get().key().toString(), is("nested"));
            assertThat(child.changed.get().get("value").asString().get(), is("raw2"));
            assertThat(detached.changed.get().key().isRoot(), is(true));
            assertThat(detached.changed.get().get("value").asString().get(), is("raw2"));
            assertThat("Original snapshot retains its filter", config.get("nested.value").asString().get(), is("raw1"));

            config.get("nested").detach().context().stopChangeSupport();
            second.context().stopChangeSupport();
            assertThat(root.stopped.get(), is(1));
            assertThat(child.stopped.get(), is(1));
            assertThat(detached.stopped.get(), is(1));

            Config third = config.context().reload();
            assertThat("Manual changes are still delivered after stop", root.changed.get(), sameInstance(third));
            assertThat(consumer.get(), sameInstance(third));
            assertThat(detached.changed.get().get("value").asString().get(), is("raw3"));
            assertThat(root.stopped.get(), is(1));
        } finally {
            config.context().stopChangeSupport();
        }
    }

    @Test
    void rawSnapshotListenersObserveFilteredChangesAndStopWithoutChangingRawViews() {
        AtomicInteger generation = new AtomicInteger();
        AtomicReference<ConfigFilterContext> context = new AtomicReference<>();
        RecordingListener root = new RecordingListener();
        RecordingListener child = new RecordingListener();
        RecordingListener detached = new RecordingListener();
        Config config = builder().addFilterProvider(initial -> {
            context.set(initial);
            Config raw = initial.initialConfig();
            raw.onChange(root);
            raw.get("nested").onChange(child);
            raw.get("nested").detach().onChange(detached);
            return _ -> {
                int current = generation.incrementAndGet();
                return (_, value) -> value + current;
            };
        }).build();
        try {
            assertThat("Building the initial config does not publish a change", root.changes.get(), is(0));
            assertThat(child.changes.get(), is(0));
            assertThat(detached.changes.get(), is(0));
            Config raw = context.get().initialConfig();
            Config rawChild = raw.get("nested");
            Config rawDetached = rawChild.detach();
            assertThat(raw.context().reload(), sameInstance(raw));
            assertThat(rawChild.context().reload(), sameInstance(rawChild));
            assertThat(rawDetached.context().reload(), sameInstance(rawDetached));
            raw.context().stopChangeSupport();
            rawChild.context().stopChangeSupport();
            rawDetached.context().stopChangeSupport();
            Config next = config.context().reload();
            assertThat(root.changed.get(), sameInstance(next));
            assertThat(root.changed.get().get("nested.value").asString().get(), is("raw2"));
            assertThat(child.changed.get().key().toString(), is("nested"));
            assertThat(child.changed.get().get("value").asString().get(), is("raw2"));
            assertThat(detached.changed.get().key().isRoot(), is(true));
            assertThat(detached.changed.get().get("value").asString().get(), is("raw2"));
            assertThat(raw.get("nested.value").asString().get(), is("raw"));
            assertThat(rawChild.get("value").asString().get(), is("raw"));
            assertThat(rawDetached.get("value").asString().get(), is("raw"));
            assertThat("Stopping raw views did not stop runtime listeners", root.stopped.get(), is(0));
            assertThat(child.stopped.get(), is(0));
            assertThat(detached.stopped.get(), is(0));
            config.context().stopChangeSupport();
            config.context().stopChangeSupport();
            assertThat(root.stopped.get(), is(1));
            assertThat(child.stopped.get(), is(1));
            assertThat(detached.stopped.get(), is(1));
        } finally {
            config.context().stopChangeSupport();
        }
    }

    @Test
    void rawSnapshotListenersOnlyReceiveEffectiveChangesToTheirNode() {
        AtomicReference<String> nested = new AtomicReference<>("first");
        AtomicReference<String> other = new AtomicReference<>("first");
        RecordingListener root = new RecordingListener();
        RecordingListener child = new RecordingListener();
        RecordingListener detached = new RecordingListener();
        Config config = builder().addSource(ConfigSources.create(Map.of("other.value", "raw")))
                .addFilterProvider(initial -> {
                    Config raw = initial.initialConfig();
                    raw.onChange(root);
                    raw.get("nested").onChange(child);
                    raw.get("nested").detach().onChange(detached);
                    return _ -> {
                        String nestedValue = nested.get();
                        String otherValue = other.get();
                        return (key, value) -> key.toString().startsWith("nested.") ? nestedValue : otherValue;
                    };
                }).build();
        try {
            config.context().reload();
            assertThat("Equivalent filtered generation does not publish a root change", root.changes.get(), is(0));
            assertThat(child.changes.get(), is(0));
            assertThat(detached.changes.get(), is(0));
            other.set("second");
            config.context().reload();
            assertThat(root.changes.get(), is(1));
            assertThat("Unrelated changes do not notify a raw child registration", child.changes.get(), is(0));
            assertThat(detached.changes.get(), is(0));
            nested.set("second");
            config.context().reload();
            assertThat(root.changes.get(), is(2));
            assertThat(child.changes.get(), is(1));
            assertThat(detached.changes.get(), is(1));
            assertThat(child.changed.get().get("value").asString().get(), is("second"));
            assertThat(detached.changed.get().get("value").asString().get(), is("second"));
        } finally {
            config.context().stopChangeSupport();
        }
    }

    @Test
    void registrationAfterStopImmediatelyNotifiesEachRegistrationOnce() {
        AtomicReference<ConfigFilterContext> context = new AtomicReference<>();
        Config config = builder().addFilterProvider(initial -> {
            context.set(initial);
            return _ -> (_, value) -> value;
        }).build();
        config.context().stopChangeSupport();
        RecordingListener listener = new RecordingListener();

        config.onChange(listener);
        assertThat(listener.stopped.get(), is(1));
        context.get().initialConfig().onChange(listener);
        assertThat(listener.stopped.get(), is(2));
        config.context().stopChangeSupport();
        assertThat(listener.stopped.get(), is(2));
    }

    @Test
    void concurrentRegistrationAndStopNeverLoseOrRepeatStopNotification() throws Exception {
        Config config = builder().build();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<RecordingListener> listeners = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            listeners.add(new RecordingListener());
        }
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> registering = executor.submit(() -> {
                ready.countDown();
                await(start);
                listeners.forEach(config::onChange);
            });
            Future<?> stopping = executor.submit(() -> {
                ready.countDown();
                await(start);
                config.context().stopChangeSupport();
            });
            assertThat("Both operations are ready", ready.await(10, TimeUnit.SECONDS), is(true));
            start.countDown();
            registering.get(10, TimeUnit.SECONDS);
            stopping.get(10, TimeUnit.SECONDS);
            config.context().stopChangeSupport();
            for (int i = 0; i < listeners.size(); i++) {
                assertThat("Stop count for registration " + i, listeners.get(i).stopped.get(), is(1));
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat("Concurrent operations terminated", executor.awaitTermination(10, TimeUnit.SECONDS), is(true));
            config.context().stopChangeSupport();
        }
    }

    @Test
    void stopCallbacksCanWaitForAnotherThreadToReadAndReloadTheRuntime() throws Exception {
        AtomicReference<ConfigFilterContext> context = new AtomicReference<>();
        Config config = builder().addFilterProvider(initial -> {
            context.set(initial);
            return _ -> (_, value) -> value;
        }).build();
        var executor = Executors.newSingleThreadExecutor();
        AtomicInteger callbacks = new AtomicInteger();
        Config.ChangeListener listener = new Config.ChangeListener() {
            @Override
            public void onChange(Config changed) {
            }

            @Override
            public void onStop() {
                try {
                    executor.submit(() -> {
                        assertThat(config.context().last().get("nested.value").asString().get(), is("raw"));
                        context.get().reload();
                        assertThat(config.context().reload().get("nested.value").asString().get(), is("raw"));
                    }).get(10, TimeUnit.SECONDS);
                    callbacks.incrementAndGet();
                } catch (Exception e) {
                    throw new IllegalStateException("Stop callback blocked another thread's context access", e);
                }
            }
        };
        try {
            context.get().initialConfig().onChange(listener);
            config.context().stopChangeSupport();
            assertThat(callbacks.get(), is(1));
            context.get().initialConfig().onChange(listener);
            assertThat("Late stop callback also runs outside runtime lock", callbacks.get(), is(2));
        } finally {
            executor.shutdownNow();
            assertThat("Context reader terminated", executor.awaitTermination(10, TimeUnit.SECONDS), is(true));
            config.context().stopChangeSupport();
        }
    }

    @Test
    void reloadRequestedDuringRebuildPublishesTheLatestGeneration() throws Exception {
        AtomicReference<ConfigFilterContext> context = new AtomicReference<>();
        AtomicReference<String> desired = new AtomicReference<>("first");
        AtomicInteger generations = new AtomicInteger();
        CountDownLatch rebuilding = new CountDownLatch(1);
        CountDownLatch releaseRebuild = new CountDownLatch(1);
        CountDownLatch latestPublished = new CountDownLatch(1);
        Config config = builder().addFilterProvider(initial -> {
            context.set(initial);
            return _ -> {
                String captured = desired.get();
                if (generations.incrementAndGet() == 2) {
                    rebuilding.countDown();
                    await(releaseRebuild);
                }
                return (_, value) -> captured;
            };
        }).build();
        var executor = Executors.newSingleThreadExecutor();
        try {
            config.onChange(changed -> {
                if (changed.get("nested.value").asString().get().equals("latest")) {
                    latestPublished.countDown();
                }
            });
            context.get().reload();
            assertThat("Second generation reached filter creation", rebuilding.await(10, TimeUnit.SECONDS), is(true));

            executor.submit(() -> {
                desired.set("latest");
                context.get().reload();
            }).get(10, TimeUnit.SECONDS);
            assertThat("Reload request returned while filter creation remained blocked", releaseRebuild.getCount(), is(1L));

            releaseRebuild.countDown();
            assertThat("Pending request published the latest generation", latestPublished.await(10, TimeUnit.SECONDS), is(true));
            assertThat(config.context().last().get("nested.value").asString().get(), is("latest"));
            assertThat("Request during rebuild created another generation", generations.get(), is(3));
            assertThat("Original snapshot retains its captured value", config.get("nested.value").asString().get(), is("first"));
        } finally {
            releaseRebuild.countDown();
            executor.shutdownNow();
            assertThat("Reload requester terminated", executor.awaitTermination(10, TimeUnit.SECONDS), is(true));
            config.context().stopChangeSupport();
        }
    }

    @Test
    void stopAttemptsAllListenersAndPreservesTheirFailures() {
        Config config = builder().build();
        IllegalStateException first = new IllegalStateException("first cleanup");
        IllegalStateException second = new IllegalStateException("second cleanup");
        AtomicInteger attempted = new AtomicInteger();
        config.onChange(new RecordingListener() {
            @Override
            public void onStop() {
                attempted.incrementAndGet();
                throw first;
            }
        });
        config.onChange(new RecordingListener() {
            @Override
            public void onStop() {
                attempted.incrementAndGet();
                throw second;
            }
        });
        RecordingListener last = new RecordingListener();
        config.onChange(last);

        assertThat(assertThrows(IllegalStateException.class, () -> config.context().stopChangeSupport()), sameInstance(first));
        assertThat(first.getSuppressed().length, is(1));
        assertThat(first.getSuppressed()[0], sameInstance(second));
        assertThat(attempted.get(), is(2));
        assertThat(last.stopped.get(), is(1));
        config.context().stopChangeSupport();
        assertThat(attempted.get(), is(2));
    }

    @Test
    void failedProviderConstructionCleansUpItsAlreadyRegisteredListener() {
        IllegalStateException initialization = new IllegalStateException("Cannot construct provider");
        IllegalStateException cleanup = new IllegalStateException("Cannot stop provider");
        AtomicReference<ConfigFilterContext> context = new AtomicReference<>();
        AtomicInteger stopped = new AtomicInteger();
        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> builder().addFilterProvider(initial -> {
            context.set(initial);
            initial.initialConfig().onChange(new RecordingListener() {
                @Override
                public void onStop() {
                    stopped.incrementAndGet();
                    throw cleanup;
                }
            });
            throw initialization;
        }).build());

        assertThat(thrown, sameInstance(initialization));
        assertThat(thrown.getSuppressed().length, is(1));
        assertThat(thrown.getSuppressed()[0], sameInstance(cleanup));
        assertThat(stopped.get(), is(1));
        RecordingListener late = new RecordingListener();
        context.get().initialConfig().onChange(late);
        assertThat("Failed runtime immediately stops late listeners", late.stopped.get(), is(1));
        context.get().reload();
        assertThat(stopped.get(), is(1));
    }

    @Test
    void failedFilterCreationPreservesInitializationFailureAndStopsEveryProvider() {
        IllegalStateException initialization = new IllegalStateException("Cannot construct filter");
        IllegalStateException cleanup = new IllegalStateException("Cannot stop first provider");
        AtomicInteger stopped = new AtomicInteger();
        RecordingListener second = new RecordingListener();
        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> builder()
                .addFilterProvider(initial -> {
                    initial.initialConfig().onChange(new RecordingListener() {
                        @Override
                        public void onStop() {
                            stopped.incrementAndGet();
                            throw cleanup;
                        }
                    });
                    return _ -> {
                        throw initialization;
                    };
                })
                .addFilterProvider(initial -> {
                    initial.initialConfig().onChange(second);
                    return _ -> (_, value) -> value;
                })
                .build());

        assertThat(thrown, sameInstance(initialization));
        assertThat(thrown.getSuppressed().length, is(1));
        assertThat(thrown.getSuppressed()[0], sameInstance(cleanup));
        assertThat(stopped.get(), is(1));
        assertThat("Later provider was stopped even though its filter was never created", second.stopped.get(), is(1));
        assertThat("Failed initialization never publishes a configuration", second.changes.get(), is(0));
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat("Concurrent operation was released", latch.await(10, TimeUnit.SECONDS), is(true));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while awaiting concurrent operation", e);
        }
    }

    private Config.Builder builder() {
        return Config.builder(ConfigSources.create(Map.of("nested.value", "raw")))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableFilterServices()
                .changesExecutor(Runnable::run);
    }

    private static class RecordingListener implements Config.ChangeListener {
        private final AtomicReference<Config> changed = new AtomicReference<>();
        private final AtomicInteger changes = new AtomicInteger();
        private final AtomicInteger stopped = new AtomicInteger();

        @Override
        public void onChange(Config config) {
            changed.set(config);
            changes.incrementAndGet();
        }

        @Override
        public void onStop() {
            stopped.incrementAndGet();
        }
    }
}
