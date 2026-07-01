/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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
package io.helidon.metrics.systemmeters;

import java.lang.ref.WeakReference;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import io.helidon.Main;
import io.helidon.common.Api;
import io.helidon.common.resumable.Resumable;
import io.helidon.common.resumable.ResumableSupport;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.SystemTagsManager;
import io.helidon.metrics.api.Timer;
import io.helidon.metrics.spi.MetersProvider;
import io.helidon.spi.HelidonShutdownHandler;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingStream;

/**
 * Provides system meters for virtual threads using Java Flight Recorder events.
 * <p>
 * The virtual thread meters are all net changes since this object was initialized during metrics start-up. This
 * currently happens before the server listeners start so the virtual thread data should be fairly accurate.
 * <p>
 * Further, we track both the number of pinned virtual threads and a distribution summary of their length. That's because
 * distribution summaries reset after a while to manage storage and reduce the contribution of stale samples.
 * Keeping a separate count gives some indication of the level of thread pinning over the entire lifetime of the server.
 * <p>
 * JFR delivers events in batches. For performance the values we track are stored as longs without
 * concern for concurrent updates which should not happen anyway.
 */
public class VThreadSystemMetersProvider implements MetersProvider, HelidonShutdownHandler, Resumable, AutoCloseable {

    // Parts of the meter names.
    static final String METER_NAME_PREFIX = "vthreads.";
    static final String COUNT = "count";
    static final String SUBMIT_FAILURES = "submitFailures";
    static final String PINNED = "pinned";
    static final String RECENT_PINNED = "recentPinned";
    static final String STARTS = "starts";

    private static final String METER_SCOPE = Meter.Scope.BASE;

    private static final System.Logger LOGGER = System.getLogger(VThreadSystemMetersProvider.class.getName());
    private final ReentrantLock lifecycleLock = new ReentrantLock();
    private final Condition shutdownHandlerUpdated = lifecycleLock.newCondition();
    private Timer recentPinnedVirtualThreads;
    private long virtualThreadSubmitFails;
    private long pinnedVirtualThreads;
    private long virtualThreads;
    private long virtualThreadStarts;
    private long pinnedVirtualThreadsThresholdMillis;
    private RecordingStream recordingStream;
    private MetricsFactory metricsFactory;
    private MetricsConfig metricsConfig;
    private SystemTagsManager systemTagsManager;
    private boolean shutdownHandlerRegistrationRequired;
    private boolean shutdownHandlerRegistrationApplied;
    private boolean shutdownHandlerUpdateInProgress;
    private boolean resumableRegistered;
    private boolean closed;
    private long lifecycleGeneration;
    private long resumableGeneration;

    /**
     * Required public constructor for {@link java.util.ServiceLoader}.
     */
    @Api.Internal
    public VThreadSystemMetersProvider() {
    }

    @Override
    public Collection<Meter.Builder<?, ?>> meterBuilders(MetricsFactory metricsFactory) {
        Collection<Meter.Builder<?, ?>> meterBuilders;
        long setupGeneration;
        lifecycleLock.lock();
        try {
            setupGeneration = ++lifecycleGeneration;
            closed = false;
            this.metricsFactory = metricsFactory;
            metricsConfig = metricsFactory.metricsConfig();
            systemTagsManager = SystemTagsManager.create(metricsConfig);
            if (!metricsConfig.virtualThreadsEnabled()) {
                return List.of();
            }

            if (!shutdownHandlerRegistrationRequired) {
                shutdownHandlerRegistrationRequired = true;
            }
            if (!resumableRegistered) {
                ResumableSupport.get().register(resumable());
                resumableRegistered = true;
            }
            pinnedVirtualThreadsThresholdMillis = metricsConfig.virtualThreadsPinnedThreshold().toMillis();

            meterBuilders = new ArrayList<>(List.of(
                    metricsFactory.gaugeBuilder(METER_NAME_PREFIX + SUBMIT_FAILURES, () -> virtualThreadSubmitFails)
                            .description("Virtual thread submit failures")
                            .scope(METER_SCOPE),
                    metricsFactory.gaugeBuilder(METER_NAME_PREFIX + PINNED, () -> pinnedVirtualThreads)
                            .description("Number of pinned virtual threads")
                            .scope(METER_SCOPE),
                    metricsFactory.timerBuilder(METER_NAME_PREFIX + RECENT_PINNED)
                            .description("Pinned virtual thread durations")
                            .scope(METER_SCOPE),
                    metricsFactory.gaugeBuilder(METER_NAME_PREFIX + COUNT, () -> virtualThreads)
                            .description("Active virtual threads")
                            .scope(METER_SCOPE),
                    metricsFactory.gaugeBuilder(METER_NAME_PREFIX + STARTS, () -> virtualThreadStarts)
                            .description("Number of virtual thread starts")
                            .scope(METER_SCOPE)
                    ));

        } finally {
            lifecycleLock.unlock();
        }

        try {
            updateShutdownHandlerRegistration(true);
            lifecycleLock.lock();
            try {
                if (!closed && lifecycleGeneration == setupGeneration) {
                    startRecordingStream();
                }
            } finally {
                lifecycleLock.unlock();
            }
            return meterBuilders;
        } catch (RuntimeException | Error e) {
            boolean updateShutdownHandler;
            lifecycleLock.lock();
            try {
                if (lifecycleGeneration != setupGeneration) {
                    throw e;
                }
                closed = true;
                lifecycleGeneration++;
                resumableGeneration++;
                if (recordingStream != null) {
                    try {
                        stopRecordingStream();
                    } catch (RuntimeException | Error cleanupError) {
                        e.addSuppressed(cleanupError);
                    }
                }
                shutdownHandlerRegistrationRequired = false;
                recentPinnedVirtualThreads = null;
                this.metricsFactory = null;
                metricsConfig = null;
                systemTagsManager = null;
                resumableRegistered = false;
                updateShutdownHandler = shutdownHandlerRegistrationRequired
                        != shutdownHandlerRegistrationApplied;
            } finally {
                lifecycleLock.unlock();
            }
            if (updateShutdownHandler) {
                try {
                    updateShutdownHandlerRegistration(false);
                } catch (RuntimeException | Error cleanupError) {
                    e.addSuppressed(cleanupError);
                }
            }
            throw e;
        }
    }

    @Override
    public void shutdown() {
        lifecycleLock.lock();
        try {
            if (recordingStream != null) {
                stopRecordingStream();
            }
        } finally {
            lifecycleLock.unlock();
        }
    }

    @Override
    public void close() {
        boolean updateShutdownHandler;
        lifecycleLock.lock();
        try {
            closed = true;
            lifecycleGeneration++;
            resumableGeneration++;
            if (recordingStream != null) {
                stopRecordingStream();
            }
            if (shutdownHandlerRegistrationRequired) {
                shutdownHandlerRegistrationRequired = false;
            }
            recentPinnedVirtualThreads = null;
            metricsFactory = null;
            metricsConfig = null;
            systemTagsManager = null;
            resumableRegistered = false;
            updateShutdownHandler = shutdownHandlerRegistrationRequired
                    != shutdownHandlerRegistrationApplied;
        } finally {
            lifecycleLock.unlock();
        }
        if (updateShutdownHandler) {
            updateShutdownHandlerRegistration(false);
        }
    }

    @Override
    public void suspend() {
        lifecycleLock.lock();
        try {
            if (recordingStream != null) {
                stopRecordingStream();
            }
        } finally {
            lifecycleLock.unlock();
        }
    }

    @Override
    public void resume() {
        lifecycleLock.lock();
        try {
            resumeLocked();
        } finally {
            lifecycleLock.unlock();
        }
    }

    // For testing
    long pinnedVirtualThreadsThresholdMillis() {
        return pinnedVirtualThreadsThresholdMillis;
    }

    // Visible for testing.
    boolean hasQueuedLifecycleOperation() {
        return lifecycleLock.hasQueuedThreads();
    }

    // Visible for testing.
    boolean recordingStreamActive() {
        lifecycleLock.lock();
        try {
            return recordingStream != null;
        } finally {
            lifecycleLock.unlock();
        }
    }

    // Visible for testing.
    Resumable resumable() {
        return new WeakResumable(this, resumableGeneration);
    }

    // Visible for testing.
    void registerShutdownHandler() {
        Main.addShutdownHandler(this);
    }

    // Visible for testing.
    void unregisterShutdownHandler() {
        Main.removeShutdownHandler(this);
    }

    // Visible for testing.
    void startRecordingStream() {

        if (recordingStream != null) {
            stopRecordingStream();
        }

        RecordingStream newRecordingStream = new RecordingStream();
        newRecordingStream.setSettings(Map.of("jdk.VirtualThreadPinned#threshold",
                                              pinnedVirtualThreadsThresholdMillis + " ms"));

        listenFor(newRecordingStream, Map.of("jdk.VirtualThreadSubmitFailed", this::recordSubmitFail,
                                             "jdk.VirtualThreadPinned", this::recordThreadPin,
                                             "jdk.VirtualThreadStart", this::recordThreadStart,
                                             "jdk.VirtualThreadEnd", this::recordThreadEnd));

        recordingStream = newRecordingStream;
        newRecordingStream.startAsync();
    }

    private void stopRecordingStream() {
        RecordingStream streamToStop = recordingStream;
        recordingStream = null;
        try {
            LOGGER.log(System.Logger.Level.INFO, "Stopping recording stream");
            streamToStop.close();
            streamToStop.awaitTermination(Duration.ofSeconds(10));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while stopping virtual thread metrics recording", e);
        }
    }

    private void suspend(long generation) {
        lifecycleLock.lock();
        try {
            if (resumableGeneration == generation && recordingStream != null) {
                stopRecordingStream();
            }
        } finally {
            lifecycleLock.unlock();
        }
    }

    private void resume(long generation) {
        lifecycleLock.lock();
        try {
            if (resumableGeneration == generation) {
                resumeLocked();
            }
        } finally {
            lifecycleLock.unlock();
        }
    }

    private void resumeLocked() {
        if (!closed && metricsConfig != null && metricsConfig.virtualThreadsEnabled()) {
            startRecordingStream();
        }
    }

    private void updateShutdownHandlerRegistration(boolean awaitUpdate) {
        while (true) {
            lifecycleLock.lock();
            try {
                if (shutdownHandlerUpdateInProgress) {
                    if (!awaitUpdate) {
                        return;
                    }
                    try {
                        shutdownHandlerUpdated.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Interrupted while updating shutdown handler registration", e);
                    }
                    continue;
                }
                if (shutdownHandlerRegistrationRequired == shutdownHandlerRegistrationApplied) {
                    return;
                }
                shutdownHandlerUpdateInProgress = true;
            } finally {
                lifecycleLock.unlock();
            }

            try {
                while (true) {
                    boolean registrationRequired;
                    lifecycleLock.lock();
                    try {
                        registrationRequired = shutdownHandlerRegistrationRequired;
                        if (registrationRequired == shutdownHandlerRegistrationApplied) {
                            shutdownHandlerUpdateInProgress = false;
                            shutdownHandlerUpdated.signalAll();
                            return;
                        }
                    } finally {
                        lifecycleLock.unlock();
                    }

                    if (registrationRequired) {
                        registerShutdownHandler();
                    } else {
                        unregisterShutdownHandler();
                    }

                    lifecycleLock.lock();
                    try {
                        shutdownHandlerRegistrationApplied = registrationRequired;
                    } finally {
                        lifecycleLock.unlock();
                    }
                }
            } catch (RuntimeException | Error e) {
                lifecycleLock.lock();
                try {
                    shutdownHandlerUpdateInProgress = false;
                    shutdownHandlerUpdated.signalAll();
                } finally {
                    lifecycleLock.unlock();
                }
                throw e;
            }
        }
    }

    private static void listenFor(RecordingStream rs, Map<String, Consumer<RecordedEvent>> events) {
        // Enable events of interest explicitly (as well as registering the callback) to be sure we receive the events we need.

        events.forEach((eventName, callback) -> {
            rs.enable(eventName);
            rs.onEvent(eventName, callback);
        });
    }

    // visible for testing
    Timer findPinned() {
        var result = metricsFactory
                .globalRegistry()
                .timer(METER_NAME_PREFIX + RECENT_PINNED,
                       systemTagsManager.withScopeTag(Collections.emptyList(), Optional.of(METER_SCOPE)));
        if (result.isEmpty()) {
            throw new IllegalStateException(METER_NAME_PREFIX + RECENT_PINNED + " meter expected but not registered");
        }
        return result.get();
    }

    private void recordThreadStart(RecordedEvent event) {
        virtualThreads++;
        virtualThreadStarts++;
        if (virtualThreadStarts < 0) {
            LOGGER.log(System.Logger.Level.INFO,
                       "Metrics counter for virtual thread starts has overflowed; clearing and continuing");
            virtualThreadStarts = 0;
        }
    }

    private void recordThreadEnd(RecordedEvent event) {
        virtualThreads--;
    }

    private void recordSubmitFail(RecordedEvent event) {
        virtualThreadSubmitFails++;
    }

    private void recordThreadPin(RecordedEvent event) {
        pinnedVirtualThreads++;
        Timer timer = recentPinnedVirtualThreads;
        if (timer == null) {
            timer = findPinned();
            recentPinnedVirtualThreads = timer;
        }
        timer.record(event.getDuration());
    }

    private static class WeakResumable implements Resumable {
        private final WeakReference<VThreadSystemMetersProvider> providerRef;
        private final long generation;

        private WeakResumable(VThreadSystemMetersProvider provider, long generation) {
            this.providerRef = new WeakReference<>(provider);
            this.generation = generation;
        }

        @Override
        public void suspend() {
            VThreadSystemMetersProvider provider = providerRef.get();
            if (provider != null) {
                provider.suspend(generation);
            }
        }

        @Override
        public void resume() {
            VThreadSystemMetersProvider provider = providerRef.get();
            if (provider != null) {
                provider.resume(generation);
            }
        }
    }
}
