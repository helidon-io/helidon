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
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.metrics.api.Clock;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.api.Timer;
import io.helidon.service.registry.ServiceRegistry;

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

    static MessagingMetrics create(MessagingConfig config, ServiceRegistry registry) {
        if (!config.metrics().enabled()) {
            return NOOP;
        }
        FailureReporter failures = new FailureReporter();
        try {
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
            for (Recorder recorder : retiring) {
                recorder.close();
            }
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
            } finally {
                lock.unlock();
            }
            try {
                return new Sample(current.clock, current.clock.monotonicTime(), true);
            } catch (RuntimeException e) {
                disable(e);
                return UNTIMED;
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
            List<Observation> replay = List.of();
            lock.lock();
            try {
                initialized = true;
                if (disabled || acquired == null) {
                    pending.clear();
                } else {
                    if (!retired) {
                        registration = acquired;
                    }
                    replay = List.copyOf(pending);
                    pending.clear();
                }
            } finally {
                lock.unlock();
            }
            if (!replay.isEmpty()) {
                try {
                    for (Observation observation : replay) {
                        acquired.record(observation.nanos(), observation.failed());
                    }
                } catch (RuntimeException e) {
                    disable(e);
                }
            }
        }

        private void cancel() {
            lock.lock();
            try {
                initialized = true;
                disabled = true;
                registration = null;
                pending.clear();
            } finally {
                lock.unlock();
            }
        }

        private void disable(RuntimeException failure) {
            lock.lock();
            try {
                disabled = true;
                registration = null;
                pending.clear();
            } finally {
                lock.unlock();
            }
            failures.report(failure);
        }

        private void close() {
            lock.lock();
            try {
                if (retired) {
                    return;
                }
                retired = true;
                registration = null;
                // Preserve completed pending observations until initialization can publish them to shared meters.
            } finally {
                lock.unlock();
            }
        }
    }

    private record Observation(long nanos, boolean failed) {
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
        private final Clock clock;
        private final Timer timer;
        private final Counter counter;

        private Registration(Clock clock, Timer timer, Counter counter) {
            this.clock = clock;
            this.timer = timer;
            this.counter = counter;
        }

        private void record(long nanos, boolean failed) {
            if (timer != null) {
                timer.record(nanos, TimeUnit.NANOSECONDS);
            }
            if (failed && counter != null) {
                counter.increment();
            }
        }
    }

    private record Acquisition(String channel, Recorder recorder, FailureReporter failures) {
    }

    /**
     * Coordinates registration callbacks only. The registry owns all resulting meter series.
     */
    private static final class RegistryState {
        private final MeterRegistry registry;
        private final ReentrantLock lock = new ReentrantLock();
        private final ArrayDeque<Acquisition> acquisitions = new ArrayDeque<>();
        // Graphs and pending acquisitions, guarded by REGISTRIES_LOCK.
        private int clients;
        private volatile boolean idle = true;
        private boolean registering;

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
                if (!registering) {
                    registering = true;
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
                Timer timer = timed
                        ? registry.getOrCreate(factory.timerBuilder(DURATION)
                                .tags(meterTags)
                                .description("Duration of completed channel batch processing attempts")
                                .origin(ORIGIN))
                        : null;
                Counter counter = counted
                        ? registry.getOrCreate(factory.counterBuilder(FAILURES)
                                .tags(meterTags)
                                .description("Failed channel batch processing attempts")
                                .origin(ORIGIN))
                        : null;
                return new Registration(clock, timer, counter);
            } catch (RuntimeException e) {
                failures.report(e);
                return null;
            }
        }

        private void runOperation(Acquisition first) {
            Boolean previous = PROVIDER_OPERATION.get();
            PROVIDER_OPERATION.set(true);
            try {
                completeAcquisition(first);
                finishOperation(false);
            } catch (RuntimeException | Error e) {
                finishOperation(true);
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

        private void finishOperation(boolean cancel) {
            while (true) {
                Acquisition acquisition;
                lock.lock();
                try {
                    acquisition = acquisitions.pollFirst();
                    if (acquisition == null) {
                        registering = false;
                        idle = true;
                        return;
                    }
                } finally {
                    lock.unlock();
                }
                if (cancel) {
                    acquisition.recorder().cancel();
                    releaseRegistry(this);
                } else {
                    completeAcquisition(acquisition);
                }
            }
        }
    }
}
