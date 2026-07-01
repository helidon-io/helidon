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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.helidon.common.resumable.Resumable;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.metrics.api.Gauge;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Timer;
import io.helidon.service.registry.Services;
import io.helidon.testing.junit5.Testing;

import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import jdk.jfr.RecordingState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static io.helidon.metrics.systemmeters.MeterBuilderMatcher.withName;
import static io.helidon.metrics.systemmeters.VThreadSystemMetersProvider.COUNT;
import static io.helidon.metrics.systemmeters.VThreadSystemMetersProvider.METER_NAME_PREFIX;
import static io.helidon.metrics.systemmeters.VThreadSystemMetersProvider.PINNED;
import static io.helidon.metrics.systemmeters.VThreadSystemMetersProvider.RECENT_PINNED;
import static io.helidon.metrics.systemmeters.VThreadSystemMetersProvider.STARTS;
import static io.helidon.metrics.systemmeters.VThreadSystemMetersProvider.SUBMIT_FAILURES;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testing.Test(perMethod = true)
class TestVirtualThreadsMetersConfigs {
    @Test
    void checkDefault() {
        Config config = Config.just(ConfigSources.create(Map.of()));
        MetricsFactory metricsFactory = Services.get(MetricsFactory.class);
        metricsFactory.globalRegistry(MetricsConfig.create(config));
        VThreadSystemMetersProvider provider = new VThreadSystemMetersProvider();
        var meterBuilders = provider.meterBuilders(metricsFactory);
        assertThat("Meter builders with default config", meterBuilders, empty());
    }

    @Test
    void checkVirtualThreadCountMetersEnabled() {
        Config config = Config.just(ConfigSources.create(Map.of("virtual-threads.enabled", "true")));
        MetricsFactory metricsFactory = Services.get(MetricsFactory.class);
        metricsFactory.globalRegistry(MetricsConfig.create(config));
        VThreadSystemMetersProvider provider = new VThreadSystemMetersProvider();
        try {
            var meterBuilders = provider.meterBuilders(metricsFactory);

            assertThat("Default meter builders",
                       meterBuilders,
                       containsInAnyOrder(allOf(withName(equalTo(METER_NAME_PREFIX + PINNED)),
                                                instanceOf(Gauge.Builder.class)),
                                          allOf(withName(equalTo(METER_NAME_PREFIX + SUBMIT_FAILURES)),
                                                instanceOf(Gauge.Builder.class)),
                                          allOf(withName(equalTo(METER_NAME_PREFIX + RECENT_PINNED)),
                                                instanceOf(Timer.Builder.class)),
                                          allOf(withName(equalTo(METER_NAME_PREFIX + COUNT)),
                                                instanceOf(Gauge.Builder.class)),
                                          allOf(withName(equalTo(METER_NAME_PREFIX + STARTS)),
                                                instanceOf(Gauge.Builder.class))));
        } finally {
            provider.close();
        }
    }

    @Test
    void checkPinnedThreadThreshold() {
        Config config = Config.just(ConfigSources.create(Map.of("virtual-threads.enabled", "true",
                                                                "virtual-threads.pinned.threshold", "PT0.040S")));
        MetricsFactory metricsFactory = Services.get(MetricsFactory.class);
        metricsFactory.globalRegistry(MetricsConfig.create(config));
        RecordingTracker recordingTracker = new RecordingTracker();
        VThreadSystemMetersProvider provider = new VThreadSystemMetersProvider();
        try {
            provider.meterBuilders(metricsFactory);

            Recording recording = recordingTracker.awaitNewRecording();
            assertThat("Pinned thread threshold",
                       recording.getSettings(),
                       hasEntry("jdk.VirtualThreadPinned#threshold", "40 ms"));
        } finally {
            provider.close();
        }
    }

    @Test
    void checkRecentPinnedTimerLookup() {
        Config config = Config.just(ConfigSources.create(Map.of("virtual-threads.enabled", "true",
                                                                "virtual-threads.pinned.threshold", "PT0.040S")));
        MetricsFactory metricsFactory = Services.get(MetricsFactory.class);
        metricsFactory.globalRegistry(MetricsConfig.create(config));
        VThreadSystemMetersProvider provider = new VThreadSystemMetersProvider();
        try {
            provider.meterBuilders(metricsFactory);

            provider.findPinned();
        } finally {
            provider.close();
        }
    }

    @Test
    void staleResumableCannotRestartRecordingAfterClose() {
        Config config = Config.just(ConfigSources.create(Map.of("virtual-threads.enabled", "true")));
        MetricsFactory metricsFactory = Services.get(MetricsFactory.class);
        metricsFactory.globalRegistry(MetricsConfig.create(config));
        RecordingTracker recordingTracker = new RecordingTracker();
        VThreadSystemMetersProvider provider = new VThreadSystemMetersProvider();
        try {
            provider.meterBuilders(metricsFactory);
            Recording recording = recordingTracker.awaitNewRecording();
            Resumable resumable = provider.resumable();

            provider.close();
            assertThat("Recording after close", recording.getState(), is(RecordingState.CLOSED));

            resumable.resume();
            recordingTracker.assertNoNewRecording("Recording after stale resume");
        } finally {
            provider.close();
        }
    }

    @Test
    @Timeout(10)
    void closeWaitsForConcurrentResume() throws Exception {
        Config config = Config.just(ConfigSources.create(Map.of("virtual-threads.enabled", "true")));
        MetricsFactory metricsFactory = Services.get(MetricsFactory.class);
        metricsFactory.globalRegistry(MetricsConfig.create(config));
        RecordingTracker recordingTracker = new RecordingTracker();
        BlockingStartProvider provider = new BlockingStartProvider();
        provider.meterBuilders(metricsFactory);
        recordingTracker.awaitNewRecording();
        provider.blockNextStart = true;

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> resume = executor.submit(provider::resume);
            try {
                assertThat("Resume reached recording stream start",
                           provider.startEntered.await(5, TimeUnit.SECONDS),
                           is(true));

                CountDownLatch closeStarted = new CountDownLatch(1);
                Future<?> close = executor.submit(() -> {
                    closeStarted.countDown();
                    provider.close();
                });
                assertThat("Close invocation started", closeStarted.await(5, TimeUnit.SECONDS), is(true));
                assertThrows(TimeoutException.class, () -> close.get(100, TimeUnit.MILLISECONDS));

                provider.continueStart.countDown();
                resume.get(5, TimeUnit.SECONDS);
                close.get(5, TimeUnit.SECONDS);
                recordingTracker.assertNoNewRecording("Recording after concurrent close");
            } finally {
                provider.continueStart.countDown();
            }
        } finally {
            provider.close();
        }
    }

    @Test
    @Timeout(10)
    void closeProceedsWhileHandlerRegistrationIsBlocked() throws Exception {
        Config config = Config.just(ConfigSources.create(Map.of("virtual-threads.enabled", "true")));
        MetricsFactory metricsFactory = Services.get(MetricsFactory.class);
        metricsFactory.globalRegistry(MetricsConfig.create(config));
        RecordingTracker recordingTracker = new RecordingTracker();
        BlockingShutdownHandlerProvider provider = new BlockingShutdownHandlerProvider();
        provider.failNextRemoval = true;

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> meterBuilders = executor.submit(() -> provider.meterBuilders(metricsFactory));
            try {
                assertThat("Shutdown handler registration started",
                           provider.registrationStarted.await(5, TimeUnit.SECONDS),
                           is(true));

                Future<?> close = executor.submit(provider::close);
                close.get(5, TimeUnit.SECONDS);

                provider.continueRegistration.countDown();
                meterBuilders.get(5, TimeUnit.SECONDS);
                assertThat("Compensating handler removal attempts", provider.removalAttempts, is(2));
                recordingTracker.assertNoNewRecording("Recording after concurrent close");
            } finally {
                provider.continueRegistration.countDown();
            }
        } finally {
            provider.close();
        }
    }

    @Test
    void failedHandlerRegistrationCleansUpAndRetries() {
        Config config = Config.just(ConfigSources.create(Map.of("virtual-threads.enabled", "true")));
        MetricsFactory metricsFactory = Services.get(MetricsFactory.class);
        metricsFactory.globalRegistry(MetricsConfig.create(config));
        RecordingTracker recordingTracker = new RecordingTracker();
        FailingShutdownHandlerProvider provider = new FailingShutdownHandlerProvider();
        provider.failNextRegistration = true;

        try {
            assertThrows(IllegalStateException.class, () -> provider.meterBuilders(metricsFactory));
            recordingTracker.assertNoNewRecording("Recording after registration failure");
            assertThat("Registration attempts after failure", provider.registrationAttempts, is(1));

            provider.meterBuilders(metricsFactory);
            Recording recording = recordingTracker.awaitNewRecording();
            assertThat("Recording after registration retry", recording.getState(), is(RecordingState.RUNNING));
            assertThat("Registration attempts after retry", provider.registrationAttempts, is(2));
        } finally {
            provider.failNextRegistration = false;
            provider.failNextRemoval = false;
            provider.close();
        }
    }

    @Test
    void failedHandlerRemovalIsRetried() {
        Config config = Config.just(ConfigSources.create(Map.of("virtual-threads.enabled", "true")));
        MetricsFactory metricsFactory = Services.get(MetricsFactory.class);
        metricsFactory.globalRegistry(MetricsConfig.create(config));
        RecordingTracker recordingTracker = new RecordingTracker();
        FailingShutdownHandlerProvider provider = new FailingShutdownHandlerProvider();

        try {
            provider.meterBuilders(metricsFactory);
            Recording recording = recordingTracker.awaitNewRecording();
            provider.failNextRemoval = true;

            assertThrows(IllegalStateException.class, provider::close);
            assertThat("Recording after removal failure", recording.getState(), is(RecordingState.CLOSED));
            assertThat("Removal attempts after failure", provider.removalAttempts, is(1));

            provider.close();
            assertThat("Removal attempts after retry", provider.removalAttempts, is(2));
        } finally {
            provider.failNextRegistration = false;
            provider.failNextRemoval = false;
            provider.close();
        }
    }

    private static final class RecordingTracker {
        private final Set<Long> initialRecordingIds = FlightRecorder.getFlightRecorder()
                .getRecordings()
                .stream()
                .map(Recording::getId)
                .collect(java.util.stream.Collectors.toSet());

        private Recording awaitNewRecording() {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            List<Recording> recordings = newRecordings();
            while (recordings.isEmpty() && System.nanoTime() < deadline) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while awaiting JFR recording", e);
                }
                recordings = newRecordings();
            }
            assertThat("Number of new JFR recordings", recordings.size(), is(1));
            return recordings.getFirst();
        }

        private void assertNoNewRecording(String reason) {
            assertThat(reason, newRecordings(), empty());
        }

        private List<Recording> newRecordings() {
            return FlightRecorder.getFlightRecorder()
                    .getRecordings()
                    .stream()
                    .filter(recording -> !initialRecordingIds.contains(recording.getId()))
                    .filter(recording -> recording.getSettings().containsKey("jdk.VirtualThreadPinned#enabled"))
                    .toList();
        }
    }

    private static final class BlockingStartProvider extends VThreadSystemMetersProvider {
        private final CountDownLatch startEntered = new CountDownLatch(1);
        private final CountDownLatch continueStart = new CountDownLatch(1);
        private volatile boolean blockNextStart;

        @Override
        void startRecordingStream() {
            if (blockNextStart) {
                startEntered.countDown();
                try {
                    continueStart.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while blocking recording stream start", e);
                }
            }
            super.startRecordingStream();
        }
    }

    private static final class BlockingShutdownHandlerProvider extends VThreadSystemMetersProvider {
        private final CountDownLatch registrationStarted = new CountDownLatch(1);
        private final CountDownLatch continueRegistration = new CountDownLatch(1);
        private boolean failNextRemoval;
        private int removalAttempts;

        @Override
        void registerShutdownHandler() {
            registrationStarted.countDown();
            try {
                continueRegistration.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while blocking shutdown handler registration", e);
            }
        }

        @Override
        void unregisterShutdownHandler() {
            removalAttempts++;
            if (failNextRemoval) {
                failNextRemoval = false;
                throw new IllegalStateException("Simulated compensating handler removal failure");
            }
        }
    }

    private static final class FailingShutdownHandlerProvider extends VThreadSystemMetersProvider {
        private boolean failNextRegistration;
        private boolean failNextRemoval;
        private int registrationAttempts;
        private int removalAttempts;

        @Override
        void registerShutdownHandler() {
            registrationAttempts++;
            if (failNextRegistration) {
                failNextRegistration = false;
                throw new IllegalStateException("Simulated shutdown handler registration failure");
            }
        }

        @Override
        void unregisterShutdownHandler() {
            removalAttempts++;
            if (failNextRemoval) {
                failNextRemoval = false;
                throw new IllegalStateException("Simulated shutdown handler removal failure");
            }
        }
    }
}
