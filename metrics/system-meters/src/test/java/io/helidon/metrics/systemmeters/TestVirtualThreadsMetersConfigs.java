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

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import io.helidon.common.resumable.Resumable;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.metrics.api.Gauge;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Timer;
import io.helidon.service.registry.Services;
import io.helidon.testing.junit5.Testing;

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

    }

    @Test
    void checkPinnedThreadThreshold() {
        Config config = Config.just(ConfigSources.create(Map.of("virtual-threads.enabled", "true",
                                                                "virtual-threads.pinned.threshold", "PT0.040S")));
        MetricsFactory metricsFactory = Services.get(MetricsFactory.class);
        metricsFactory.globalRegistry(MetricsConfig.create(config));
        VThreadSystemMetersProvider provider = new VThreadSystemMetersProvider();
        provider.meterBuilders(metricsFactory);

        assertThat("Pinned thread threshold", provider.pinnedVirtualThreadsThresholdMillis(), equalTo(40L));

    }

    @Test
    void checkRecentPinnedTimerLookup() {
        Config config = Config.just(ConfigSources.create(Map.of("virtual-threads.enabled", "true",
                                                                "virtual-threads.pinned.threshold", "PT0.040S")));
        MetricsFactory metricsFactory = Services.get(MetricsFactory.class);
        metricsFactory.globalRegistry(MetricsConfig.create(config));
        VThreadSystemMetersProvider provider = new VThreadSystemMetersProvider();
        provider.meterBuilders(metricsFactory);

        provider.findPinned();
    }

    @Test
    void staleResumableCannotRestartRecordingAfterClose() {
        Config config = Config.just(ConfigSources.create(Map.of("virtual-threads.enabled", "true")));
        MetricsFactory metricsFactory = Services.get(MetricsFactory.class);
        metricsFactory.globalRegistry(MetricsConfig.create(config));
        VThreadSystemMetersProvider provider = new VThreadSystemMetersProvider();
        provider.meterBuilders(metricsFactory);
        Resumable resumable = provider.resumable();

        provider.close();
        resumable.resume();

        assertThat("Recording stream after stale resume", provider.recordingStreamActive(), is(false));
    }

    @Test
    @Timeout(10)
    void closeWaitsForConcurrentResume() throws Exception {
        Config config = Config.just(ConfigSources.create(Map.of("virtual-threads.enabled", "true")));
        MetricsFactory metricsFactory = Services.get(MetricsFactory.class);
        metricsFactory.globalRegistry(MetricsConfig.create(config));
        BlockingStartProvider provider = new BlockingStartProvider();
        provider.meterBuilders(metricsFactory);
        provider.blockNextStart = true;

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> resume = executor.submit(provider::resume);
            try {
                assertThat("Resume reached recording stream start",
                           provider.startEntered.await(5, TimeUnit.SECONDS),
                           is(true));

                Future<?> close = executor.submit(provider::close);
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (!provider.hasQueuedLifecycleOperation() && System.nanoTime() < deadline) {
                    Thread.onSpinWait();
                }
                assertThat("Close queued behind resume", provider.hasQueuedLifecycleOperation(), is(true));

                provider.continueStart.countDown();
                resume.get(5, TimeUnit.SECONDS);
                close.get(5, TimeUnit.SECONDS);
                assertThat("Recording stream after close", provider.recordingStreamActive(), is(false));
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
                assertThat("Recording stream after concurrent close", provider.recordingStreamActive(), is(false));
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
        FailingShutdownHandlerProvider provider = new FailingShutdownHandlerProvider();
        provider.failNextRegistration = true;

        try {
            assertThrows(IllegalStateException.class, () -> provider.meterBuilders(metricsFactory));
            assertThat("Recording stream after registration failure", provider.recordingStreamActive(), is(false));
            assertThat("Registration attempts after failure", provider.registrationAttempts, is(1));

            provider.meterBuilders(metricsFactory);
            assertThat("Recording stream after registration retry", provider.recordingStreamActive(), is(true));
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
        FailingShutdownHandlerProvider provider = new FailingShutdownHandlerProvider();

        try {
            provider.meterBuilders(metricsFactory);
            provider.failNextRemoval = true;

            assertThrows(IllegalStateException.class, provider::close);
            assertThat("Recording stream after removal failure", provider.recordingStreamActive(), is(false));
            assertThat("Removal attempts after failure", provider.removalAttempts, is(1));

            provider.close();
            assertThat("Removal attempts after retry", provider.removalAttempts, is(2));
        } finally {
            provider.failNextRegistration = false;
            provider.failNextRemoval = false;
            provider.close();
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
