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

package io.helidon.messaging;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.metrics.api.Clock;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.api.Timer;
import io.helidon.service.registry.GlobalServiceRegistry;

import static java.lang.System.Logger.Level.WARNING;

final class MessagingMetrics {
    private static final String DURATION = "messaging.processing.duration";
    private static final String FAILURES = "messaging.processing.failures";
    private static final String ORIGIN = MessagingGraph.class.getName();
    private static final System.Logger LOGGER = System.getLogger(MessagingMetrics.class.getName());
    private static final ReentrantLock REGISTRIES_LOCK = new ReentrantLock();
    private static final Map<MeterRegistry, RegistryState> REGISTRIES = new IdentityHashMap<>();
    private static final ThreadLocal<Boolean> PROVIDER_OPERATION = new ThreadLocal<>();
    private static final Recorder NOOP_RECORDER = new Recorder(null);
    private static final MessagingMetrics NOOP = new MessagingMetrics(null, null);

    private final RegistryState state;
    private final FailureReporter failures;
    private final ReentrantLock lock = new ReentrantLock();
    private final Map<String, Recorder> recorders = new HashMap<>();
    private boolean closed;

    private MessagingMetrics(RegistryState state, FailureReporter failures) {
        this.state = state;
        this.failures = failures;
    }

    static MessagingMetrics create(MessagingConfig config) {
        if (!config.enableMetrics()) {
            return NOOP;
        }
        FailureReporter failures = new FailureReporter();
        try {
            var registry = config.serviceRegistry().orElseGet(GlobalServiceRegistry::registry);
            return registry.first(MeterRegistry.class)
                    .map(meterRegistry -> new MessagingMetrics(acquireRegistry(meterRegistry), failures))
                    .orElse(NOOP);
        } catch (RuntimeException e) {
            failures.report(e);
            return NOOP;
        }
    }

    Recorder channel(String channel) {
        if (state == null) {
            return NOOP_RECORDER;
        }
        Recorder recorder;
        lock.lock();
        try {
            if (closed) {
                return NOOP_RECORDER;
            }
            recorder = recorders.get(channel);
            if (recorder != null) {
                return recorder;
            }
            recorder = new Recorder(failures);
            retainRegistry(state);
            recorders.put(channel, recorder);
        } finally {
            lock.unlock();
        }
        // Publish the pending recorder before invoking providers, so reentrant close can retire it.
        try {
            recorder.initialize(state.acquire(channel, failures));
        } finally {
            releaseRegistry(state);
        }
        return recorder;
    }

    void close() {
        if (state == null) {
            return;
        }
        List<Recorder> retiring;
        lock.lock();
        try {
            if (closed) {
                return;
            }
            closed = true;
            retiring = List.copyOf(recorders.values());
            recorders.clear();
        } finally {
            lock.unlock();
        }
        try {
            retiring.forEach(Recorder::close);
        } finally {
            releaseRegistry(state);
        }
    }

    private static RegistryState acquireRegistry(MeterRegistry registry) {
        REGISTRIES_LOCK.lock();
        try {
            RegistryState state = REGISTRIES.computeIfAbsent(registry, RegistryState::new);
            state.clients++;
            return state;
        } finally {
            REGISTRIES_LOCK.unlock();
        }
    }

    private static void releaseRegistry(RegistryState state) {
        REGISTRIES_LOCK.lock();
        try {
            state.clients--;
            if (state.clients == 0 && state.idle) {
                REGISTRIES.remove(state.registry, state);
            }
        } finally {
            REGISTRIES_LOCK.unlock();
        }
    }

    private static void retainRegistry(RegistryState state) {
        REGISTRIES_LOCK.lock();
        try {
            state.clients++;
        } finally {
            REGISTRIES_LOCK.unlock();
        }
    }

    private static void removeIdleRegistry(RegistryState state) {
        REGISTRIES_LOCK.lock();
        try {
            if (state.clients == 0 && state.idle) {
                REGISTRIES.remove(state.registry, state);
            }
        } finally {
            REGISTRIES_LOCK.unlock();
        }
    }

    static final class Recorder {
        private final FailureReporter failures;
        private final ReentrantLock lock = new ReentrantLock();
        private Registration registration;
        private int activeCalls;
        private boolean retired;
        private boolean disabled;

        private Recorder(FailureReporter failures) {
            this.failures = failures;
        }

        long start() {
            if (this == NOOP_RECORDER) {
                return 0;
            }
            Registration current = begin(true);
            if (current == null) {
                return 0;
            }
            try {
                return current.clock.monotonicTime();
            } catch (RuntimeException e) {
                disable(e);
                return 0;
            } finally {
                end();
            }
        }

        void finish(long started, boolean failed) {
            if (this == NOOP_RECORDER) {
                return;
            }
            Registration current = begin(false);
            if (current == null) {
                return;
            }
            try {
                if (current.timer != null) {
                    ((Timer) current.timer.meter).record(current.clock.monotonicTime() - started, TimeUnit.NANOSECONDS);
                }
                if (failed && current.counter != null) {
                    ((Counter) current.counter.meter).increment();
                }
            } catch (RuntimeException e) {
                disable(e);
            } finally {
                end();
            }
        }

        private void initialize(Registration acquired) {
            boolean release;
            lock.lock();
            try {
                release = retired;
                if (!release) {
                    registration = acquired;
                }
            } finally {
                lock.unlock();
            }
            if (release && acquired != null) {
                acquired.close();
            }
        }

        private Registration begin(boolean timed) {
            lock.lock();
            try {
                if (retired || disabled || registration == null || timed && registration.timer == null) {
                    return null;
                }
                activeCalls++;
                return registration;
            } finally {
                lock.unlock();
            }
        }

        private void end() {
            Registration release = null;
            lock.lock();
            try {
                activeCalls--;
                if (retired && activeCalls == 0) {
                    release = registration;
                    registration = null;
                }
            } finally {
                lock.unlock();
            }
            if (release != null) {
                release.close();
            }
        }

        private void disable(RuntimeException failure) {
            lock.lock();
            try {
                disabled = true;
            } finally {
                lock.unlock();
            }
            failures.report(failure);
        }

        private void close() {
            Registration release = null;
            lock.lock();
            try {
                if (retired) {
                    return;
                }
                retired = true;
                if (activeCalls == 0) {
                    release = registration;
                    registration = null;
                }
            } finally {
                lock.unlock();
            }
            if (release != null) {
                release.close();
            }
        }
    }

    private record MeterKey(String name, Map<String, String> tags) {
        private static MeterKey create(Meter meter) {
            Meter.Id id = meter.id();
            return new MeterKey(id.name(), Map.copyOf(id.tagsMap()));
        }
    }

    private static final class FailureReporter {
        private final AtomicBoolean reported = new AtomicBoolean();

        private void report(RuntimeException failure) {
            if (reported.compareAndSet(false, true)) {
                try {
                    LOGGER.log(WARNING,
                               "Messaging metrics failed; further metrics failures for this graph are suppressed",
                               failure);
                } catch (RuntimeException _) {
                    // Logging must not replace a messaging result either.
                }
            }
        }
    }

    private static final class Registration {
        private final RegistryState state;
        private final Clock clock;
        private final OwnedMeter timer;
        private final OwnedMeter counter;

        private Registration(RegistryState state, Clock clock, OwnedMeter timer, OwnedMeter counter) {
            this.state = state;
            this.clock = clock;
            this.timer = timer;
            this.counter = counter;
        }

        private void close() {
            state.release(timer, counter);
        }
    }

    private static final class OwnedMeter {
        private final MeterKey key;
        private final Meter meter;
        private final boolean owned;
        private final FailureReporter failures;
        private int owners;
        private boolean queued;

        private OwnedMeter(MeterKey key, Meter meter, boolean owned, FailureReporter failures) {
            this.key = key;
            this.meter = meter;
            this.owned = owned;
            this.failures = failures;
        }
    }

    private static final class RegistryState {
        private final MeterRegistry registry;
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition operationComplete = lock.newCondition();
        private final Map<MeterKey, OwnedMeter> meters = new HashMap<>();
        private final ArrayDeque<OwnedMeter> removals = new ArrayDeque<>();
        // Graphs and pending acquisitions, guarded by REGISTRIES_LOCK; provider work uses this state's lock instead.
        private int clients;
        private volatile boolean idle = true;
        private Thread operationOwner;

        private RegistryState(MeterRegistry registry) {
            this.registry = registry;
        }

        private Registration acquire(String channel, FailureReporter failures) {
            if (Boolean.TRUE.equals(PROVIDER_OPERATION.get())) {
                failures.report(new IllegalStateException("Reentrant messaging metrics registration "
                                                                 + "from a metrics provider callback"));
                return null;
            }
            lock.lock();
            try {
                while (operationOwner != null) {
                    operationComplete.awaitUninterruptibly();
                }
                operationOwner = Thread.currentThread();
                idle = false;
            } finally {
                lock.unlock();
            }
            PROVIDER_OPERATION.set(true);
            OwnedMeter timer = null;
            try {
                Map<String, String> tags = Map.of("channel", channel);
                boolean timed = registry.isMeterEnabled(DURATION) && registry.isMeterEnabled(DURATION, tags);
                boolean counted = registry.isMeterEnabled(FAILURES) && registry.isMeterEnabled(FAILURES, tags);
                if (!timed && !counted) {
                    return null;
                }
                MetricsFactory factory = registry.metricsFactory();
                List<Tag> meterTags = List.of(factory.tagCreate("channel", channel));
                Clock clock = timed ? registry.clock() : null;
                if (timed) {
                    timer = register(factory, factory.timerBuilder(DURATION)
                            .tags(meterTags)
                            .description("Duration of completed channel batch processing attempts")
                            .origin(ORIGIN), failures);
                }
                OwnedMeter counter = counted
                        ? register(factory, factory.counterBuilder(FAILURES)
                                .tags(meterTags)
                                .description("Failed channel batch processing attempts")
                                .origin(ORIGIN), failures)
                        : null;
                return new Registration(this, clock, timer, counter);
            } catch (RuntimeException e) {
                release(timer, null);
                failures.report(e);
                return null;
            } finally {
                try {
                    finishOperation();
                } finally {
                    PROVIDER_OPERATION.remove();
                }
            }
        }

        private <B extends Meter.Builder<B, M>, M extends Meter> OwnedMeter register(MetricsFactory factory,
                                                                                    B builder,
                                                                                    FailureReporter failures) {
            Set<MeterKey> before = new HashSet<>();
            registry.meters().forEach(meter -> before.add(MeterKey.create(meter)));
            Meter meter;
            try {
                meter = registry.getOrCreate(builder);
            } catch (RuntimeException e) {
                recoverRegistration(factory, builder, before, failures);
                throw e;
            }
            MeterKey key = MeterKey.create(meter);
            lock.lock();
            try {
                OwnedMeter shared = meters.computeIfAbsent(key,
                        _ -> new OwnedMeter(key, meter, !before.contains(key), failures));
                shared.owners++;
                return shared;
            } finally {
                lock.unlock();
            }
        }

        private void recoverRegistration(MetricsFactory factory,
                                         Meter.Builder<?, ?> builder,
                                         Set<MeterKey> before,
                                         FailureReporter failures) {
            try {
                List<Tag> tags = builder.tags().entrySet().stream()
                        .map(entry -> factory.tagCreate(entry.getKey(), entry.getValue()))
                        .toList();
                // getOrCreate can throw either before registration or from a listener after inserting the meter.
                // Recover only a newly appearing identity; a pre-existing meter is never rollback-owned.
                registry.meter(Meter.class, builder.name(), tags).ifPresent(meter -> {
                    MeterKey key = MeterKey.create(meter);
                    if (before.contains(key)) {
                        return;
                    }
                    lock.lock();
                    try {
                        if (!meters.containsKey(key)) {
                            OwnedMeter orphan = new OwnedMeter(key, meter, true, failures);
                            meters.put(key, orphan);
                            enqueue(orphan);
                        }
                    } finally {
                        lock.unlock();
                    }
                });
            } catch (RuntimeException recoveryFailure) {
                failures.report(recoveryFailure);
            }
        }

        private void release(OwnedMeter timer, OwnedMeter counter) {
            boolean cleanup;
            lock.lock();
            try {
                releaseMeter(timer);
                releaseMeter(counter);
                cleanup = operationOwner == null && !removals.isEmpty();
                if (cleanup) {
                    operationOwner = Thread.currentThread();
                }
                idle = operationOwner == null && meters.isEmpty();
            } finally {
                lock.unlock();
            }
            if (cleanup) {
                // Closing a graph during a provider callback only queues its removals; it never waits for the callback.
                Boolean previous = PROVIDER_OPERATION.get();
                PROVIDER_OPERATION.set(true);
                try {
                    finishOperation();
                } finally {
                    if (previous == null) {
                        PROVIDER_OPERATION.remove();
                    } else {
                        PROVIDER_OPERATION.set(previous);
                    }
                }
            } else {
                removeIdleRegistry(this);
            }
        }

        private void releaseMeter(OwnedMeter meter) {
            if (meter == null || --meter.owners != 0) {
                return;
            }
            if (meter.owned) {
                enqueue(meter);
            } else {
                meters.remove(meter.key, meter);
            }
        }

        private void enqueue(OwnedMeter meter) {
            if (!meter.queued) {
                meter.queued = true;
                removals.addLast(meter);
            }
        }

        private void finishOperation() {
            try {
                while (true) {
                    OwnedMeter meter;
                    lock.lock();
                    try {
                        meter = removals.pollFirst();
                        if (meter == null) {
                            operationOwner = null;
                            idle = meters.isEmpty();
                            operationComplete.signalAll();
                            return;
                        }
                        meter.queued = false;
                        if (meter.owners != 0 || !meters.remove(meter.key, meter)) {
                            continue;
                        }
                    } finally {
                        lock.unlock();
                    }
                    try {
                        registry.remove(meter.meter);
                    } catch (RuntimeException e) {
                        meter.failures.report(e);
                    }
                }
            } finally {
                lock.lock();
                try {
                    if (operationOwner == Thread.currentThread()) {
                        operationOwner = null;
                        idle = meters.isEmpty();
                        operationComplete.signalAll();
                    }
                } finally {
                    lock.unlock();
                }
                removeIdleRegistry(this);
            }
        }
    }
}
