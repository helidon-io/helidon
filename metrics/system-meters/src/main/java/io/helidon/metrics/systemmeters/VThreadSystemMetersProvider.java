/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import io.helidon.common.LazyValue;
import io.helidon.metrics.api.Gauge;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.Metrics;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Timer;
import io.helidon.metrics.spi.MetersProvider;

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
public class VThreadSystemMetersProvider implements MetersProvider {

    // Parts of the meter names.
    static final String METER_NAME_PREFIX = "vthreads.";
    static final String COUNT = "count";
    static final String SUBMIT_FAILURES = "submitFailures";
    static final String PINNED = "pinned";
    static final String RECENT_PINNED = "recentPinned";
    static final String STARTS = "starts";

    private static final String METER_SCOPE = Meter.Scope.BASE;

    private static final System.Logger LOGGER = System.getLogger(VThreadSystemMetersProvider.class.getName());
    private final LazyValue<Timer> recentPinnedVirtualThreads = LazyValue.create(this::findPinned);
    private long virtualThreadSubmitFails;
    private long pinnedVirtualThreads;
    private long virtualThreads;
    private long virtualThreadStarts;
    private long pinnedVirtualThreadsThresholdMillis;

    /**
     * For service loading.
     */
    public VThreadSystemMetersProvider() {
    }

    @Override
    public Collection<Meter.Builder<?, ?>> meterBuilders(MetricsFactory metricsFactory) {

        MetricsConfig metricsConfig = metricsFactory.metricsConfig();
        if (!metricsConfig.virtualThreadsEnabled()) {
            return List.of();
        }

        var recordingStream = new RecordingStream();
        pinnedVirtualThreadsThresholdMillis = metricsConfig.virtualThreadsPinnedThreshold().toMillis();
        recordingStream.setSettings(Map.of("jdk.VirtualThreadPinned#threshold",
                                            pinnedVirtualThreadsThresholdMillis + " ms"));

        List<Meter.Builder<?, ?>> meterBuilders = new ArrayList<>(List.of(
                Gauge.builder(METER_NAME_PREFIX + SUBMIT_FAILURES, () -> virtualThreadSubmitFails)
                        .description("Virtual thread submit failures")
                        .scope(METER_SCOPE),
                Gauge.builder(METER_NAME_PREFIX + PINNED, () -> pinnedVirtualThreads)
                        .description("Number of pinned virtual threads")
                        .scope(METER_SCOPE),
                Timer.builder(METER_NAME_PREFIX + RECENT_PINNED)
                        .description("Pinned virtual thread durations")
                        .scope(METER_SCOPE)));

        listenFor(recordingStream, Map.of("jdk.VirtualThreadSubmitFailed", this::recordSubmitFail,
                             "jdk.VirtualThreadPinned", this::recordThreadPin));

        if (metricsFactory.metricsConfig().virtualThreadCountEnabled()) {
            meterBuilders.add(Gauge.builder(METER_NAME_PREFIX + COUNT, () -> virtualThreads)
                                      .description("Active virtual threads")
                                      .scope(METER_SCOPE));
            meterBuilders.add(Gauge.builder(METER_NAME_PREFIX + STARTS, () -> virtualThreadStarts)
                                      .description("Number of virtual thread starts")
                                      .scope(METER_SCOPE));

            listenFor(recordingStream, Map.of("jdk.VirtualThreadStart", this::recordThreadStart,
                                 "jdk.VirtualThreadEnd", this::recordThreadEnd));
        }

        recordingStream.startAsync();
        return meterBuilders;
    }

    // For testing
    long pinnedVirtualThreadsThresholdMillis() {
        return pinnedVirtualThreadsThresholdMillis;
    }

    private static void listenFor(RecordingStream rs, Map<String, Consumer<RecordedEvent>> events) {
        // Enable events of interest explicitly (as well as registering the callback) to be sure we receive the events we need.

        events.forEach((eventName, callback) -> {
            rs.enable(eventName);
            rs.onEvent(eventName, callback);
        });
    }

    private Timer findPinned() {
        var result = Metrics.globalRegistry().timer(METER_NAME_PREFIX + PINNED, List.of());
        if (result.isEmpty()) {
            throw new IllegalStateException(METER_NAME_PREFIX + "pinned meter expected but not registered");
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
        recentPinnedVirtualThreads.get().record(event.getDuration());
    }
}
