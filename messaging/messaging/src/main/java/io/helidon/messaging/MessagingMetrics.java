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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.common.Wrapper;
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
    private static final int MAX_PENDING_REGISTRATIONS = 64;
    private static final int MAX_PENDING_SAMPLES = 1024;
    private static final Sample UNTIMED = new Sample(null, 0, false);
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
        state.acquire(channel, recorder, failures);
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

    private static Object meterIdentity(Meter meter) {
        Object current = meter;
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        while (current instanceof Wrapper wrapper && seen.add(current)) {
            Object delegate;
            try {
                delegate = Objects.requireNonNull(wrapper.unwrap(Object.class));
            } catch (UnsupportedOperationException _) {
                break;
            }
            if (delegate == current) {
                break;
            }
            current = delegate;
        }
        return current;
    }

    static final class Sample {
        private final Clock clock;
        private final long started;
        private final boolean timed;

        private Sample(Clock clock, long started, boolean timed) {
            this.clock = clock;
            this.started = started;
            this.timed = timed;
        }

        private long elapsed() {
            return timed ? (clock == null ? System.nanoTime() : clock.monotonicTime()) - started : 0;
        }
    }

    static final class Recorder {
        private final FailureReporter failures;
        private final ReentrantLock lock = new ReentrantLock();
        private final ArrayDeque<Observation> pending = new ArrayDeque<>();
        private Registration registration;
        private int activeCalls;
        private boolean initialized;
        private boolean retired;
        private boolean disabled;

        private Recorder(FailureReporter failures) {
            this.failures = failures;
        }

        Sample start() {
            if (this == NOOP_RECORDER) {
                return UNTIMED;
            }
            Registration current;
            lock.lock();
            try {
                if (retired || disabled) {
                    return UNTIMED;
                }
                if (!initialized) {
                    // A pending registration must not call a provider that might be waiting for this graph.
                    return new Sample(null, System.nanoTime(), true);
                }
                current = registration;
                if (current == null || current.timer == null) {
                    return UNTIMED;
                }
                activeCalls++;
            } finally {
                lock.unlock();
            }
            try {
                return new Sample(current.clock, current.clock.monotonicTime(), true);
            } catch (RuntimeException e) {
                disable(e);
                return UNTIMED;
            } finally {
                end();
            }
        }

        void finish(Sample started, boolean failed) {
            if (this == NOOP_RECORDER) {
                return;
            }
            Registration current;
            boolean overflow = false;
            lock.lock();
            try {
                if (retired || disabled) {
                    return;
                }
                if (!initialized) {
                    if (pending.size() == MAX_PENDING_SAMPLES) {
                        disabled = true;
                        pending.clear();
                        overflow = true;
                    } else {
                        // Pending samples always start on the system clock; initialization and this append are atomic.
                        pending.addLast(new Observation(started.timed ? System.nanoTime() - started.started : 0, failed));
                    }
                    current = null;
                } else {
                    current = registration;
                    if (current != null) {
                        activeCalls++;
                    }
                }
            } finally {
                lock.unlock();
            }
            if (overflow) {
                failures.report(new IllegalStateException("Messaging metrics pending sample buffer is full"));
            }
            if (current == null) {
                return;
            }
            try {
                current.record(started.elapsed(), failed);
            } catch (RuntimeException e) {
                disable(e);
            } finally {
                end();
            }
        }

        private boolean needsInitialization() {
            lock.lock();
            try {
                return !disabled && (!retired || !pending.isEmpty());
            } finally {
                lock.unlock();
            }
        }

        private void initialize(Registration acquired) {
            Registration release = null;
            List<Observation> replay = List.of();
            lock.lock();
            try {
                initialized = true;
                if (disabled || acquired == null) {
                    pending.clear();
                    release = acquired;
                } else {
                    registration = acquired;
                    replay = List.copyOf(pending);
                    pending.clear();
                    if (!replay.isEmpty()) {
                        activeCalls++;
                    } else if (retired) {
                        release = registration;
                        registration = null;
                    }
                }
            } finally {
                lock.unlock();
            }
            if (release != null) {
                release.close();
            }
            if (!replay.isEmpty()) {
                try {
                    for (Observation observation : replay) {
                        acquired.record(observation.nanos(), observation.failed());
                    }
                } catch (RuntimeException e) {
                    disable(e);
                } finally {
                    end();
                }
            }
        }

        private void cancel() {
            lock.lock();
            try {
                initialized = true;
                disabled = true;
                pending.clear();
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
                pending.clear();
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
                // Preserve completed pending observations until initialization can publish them to shared meters.
            } finally {
                lock.unlock();
            }
            if (release != null) {
                release.close();
            }
        }
    }

    private record Observation(long nanos, boolean failed) {
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

        private void record(long nanos, boolean failed) {
            if (timer != null) {
                ((Timer) timer.meter).record(nanos, TimeUnit.NANOSECONDS);
            }
            if (failed && counter != null) {
                ((Counter) counter.meter).increment();
            }
        }

        private void close() {
            state.release(timer, counter);
        }
    }

    private static final class OwnedMeter {
        private final MeterKey key;
        private final Meter meter;
        private final Object identity;
        private final boolean owned;
        private final FailureReporter failures;
        private int owners;
        private boolean queued;

        private OwnedMeter(MeterKey key, Meter meter, Object identity, boolean owned, FailureReporter failures) {
            this.key = key;
            this.meter = meter;
            this.identity = identity;
            this.owned = owned;
            this.failures = failures;
        }
    }

    private record Acquisition(String channel, Recorder recorder, FailureReporter failures) {
    }

    private static final class RegistryState {
        private final MeterRegistry registry;
        private final ReentrantLock lock = new ReentrantLock();
        private final Map<MeterKey, OwnedMeter> meters = new HashMap<>();
        private final ArrayDeque<Acquisition> acquisitions = new ArrayDeque<>();
        private final ArrayDeque<OwnedMeter> removals = new ArrayDeque<>();
        // Graphs and pending acquisitions, guarded by REGISTRIES_LOCK; provider work uses this state's lock instead.
        private int clients;
        private volatile boolean idle = true;
        private Thread operationOwner;

        private RegistryState(MeterRegistry registry) {
            this.registry = registry;
        }

        private void acquire(String channel, Recorder recorder, FailureReporter failures) {
            if (Boolean.TRUE.equals(PROVIDER_OPERATION.get())) {
                recorder.cancel();
                releaseRegistry(this);
                failures.report(new IllegalStateException("Reentrant messaging metrics registration "
                                                                 + "from a metrics provider callback"));
                return;
            }
            Acquisition acquisition = new Acquisition(channel, recorder, failures);
            boolean run = false;
            boolean overflow = false;
            lock.lock();
            try {
                if (operationOwner == null) {
                    operationOwner = Thread.currentThread();
                    idle = false;
                    run = true;
                } else if (acquisitions.size() == MAX_PENDING_REGISTRATIONS) {
                    overflow = true;
                } else {
                    acquisitions.addLast(acquisition);
                }
            } finally {
                lock.unlock();
            }
            if (overflow) {
                recorder.cancel();
                releaseRegistry(this);
                failures.report(new IllegalStateException("Messaging metrics pending registration queue is full"));
            } else if (run) {
                runOperation(acquisition);
            }
        }

        private void completeAcquisition(Acquisition acquisition) {
            try {
                Registration registration = acquisition.recorder().needsInitialization()
                        ? acquireNow(acquisition.channel(), acquisition.failures())
                        : null;
                acquisition.recorder().initialize(registration);
            } catch (RuntimeException | Error e) {
                acquisition.recorder().cancel();
                throw e;
            } finally {
                releaseRegistry(this);
            }
        }

        private Registration acquireNow(String channel, FailureReporter failures) {
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
            } catch (Error e) {
                release(timer, null);
                throw e;
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
            } catch (RuntimeException | Error e) {
                try {
                    recoverRegistration(factory, builder, before, failures);
                } catch (RuntimeException | Error recoveryFailure) {
                    if (recoveryFailure != e) {
                        e.addSuppressed(recoveryFailure);
                    }
                }
                throw e;
            }
            MeterKey key = MeterKey.create(meter);
            Object identity = meterIdentity(meter);
            lock.lock();
            try {
                OwnedMeter shared = meters.get(key);
                if (shared == null || shared.identity != identity) {
                    shared = new OwnedMeter(key, meter, identity, !before.contains(key), failures);
                    meters.put(key, shared);
                }
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
                    Object identity = meterIdentity(meter);
                    lock.lock();
                    try {
                        OwnedMeter existing = meters.get(key);
                        if (existing == null || existing.identity != identity) {
                            OwnedMeter orphan = new OwnedMeter(key, meter, identity, true, failures);
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
                idle = operationOwner == null && meters.isEmpty() && acquisitions.isEmpty();
            } finally {
                lock.unlock();
            }
            if (cleanup) {
                runOperation(null);
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

        private void runOperation(Acquisition first) {
            Boolean previous = PROVIDER_OPERATION.get();
            PROVIDER_OPERATION.set(true);
            try {
                if (first != null) {
                    completeAcquisition(first);
                }
                finishOperation(null);
            } catch (RuntimeException | Error e) {
                // Cancel pending acquisitions and clean up partial registrations before propagating a fatal failure.
                finishOperation(e);
                throw e;
            } finally {
                if (previous == null) {
                    PROVIDER_OPERATION.remove();
                } else {
                    PROVIDER_OPERATION.set(previous);
                }
                removeIdleRegistry(this);
            }
        }

        private void finishOperation(Throwable failure) {
            while (true) {
                Acquisition acquisition;
                OwnedMeter meter = null;
                lock.lock();
                try {
                    acquisition = acquisitions.pollFirst();
                    if (acquisition == null) {
                        meter = removals.pollFirst();
                        if (meter == null) {
                            operationOwner = null;
                            idle = meters.isEmpty();
                            return;
                        }
                        meter.queued = false;
                        if (meter.owners != 0 || !meters.remove(meter.key, meter)) {
                            continue;
                        }
                    }
                } finally {
                    lock.unlock();
                }
                if (acquisition != null) {
                    if (failure == null) {
                        completeAcquisition(acquisition);
                    } else {
                        acquisition.recorder().cancel();
                        releaseRegistry(this);
                    }
                } else {
                    try {
                        removeCurrent(meter);
                    } catch (RuntimeException | Error cleanupFailure) {
                        if (failure == null) {
                            throw cleanupFailure;
                        }
                        if (cleanupFailure != failure) {
                            failure.addSuppressed(cleanupFailure);
                        }
                    }
                }
            }
        }

        private void removeCurrent(OwnedMeter meter) {
            try {
                var factory = registry.metricsFactory();
                List<Tag> tags = meter.key.tags().entrySet().stream()
                        .map(entry -> factory.tagCreate(entry.getKey(), entry.getValue()))
                        .toList();
                var current = registry.meter(Meter.class, meter.key.name(), tags);
                if (current.isPresent()) {
                    if (meterIdentity(current.get()) == meter.identity) {
                        registry.remove(current.get());
                    }
                } else if (!registry.isDeleted(meter.meter)) {
                    // Disabled meters are absent from meter lookups, but the registry tracks their deletion state.
                    registry.remove(meter.meter);
                }
            } catch (RuntimeException e) {
                meter.failures.report(e);
            }
        }
    }
}
