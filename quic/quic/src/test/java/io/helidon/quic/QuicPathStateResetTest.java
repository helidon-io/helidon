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

package io.helidon.quic;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class QuicPathStateResetTest {
    @Test
    void resetsRttToConfiguredInitialState() {
        QuicRuntimeConfig.Recovery recoveryConfig = new QuicRuntimeConfig.Recovery(
                QuicRuntimeConfig.DEFAULT_MAX_PTO_BACKOFF_EXPONENT,
                QuicRuntimeConfig.DEFAULT_MAX_PTO_BACKOFF_TIMEOUT,
                QuicRuntimeConfig.DEFAULT_MIN_PTO_BACKOFF_TIMEOUT,
                Duration.ofMillis(100),
                1000);
        QuicRttEstimator estimator = QuicRttEstimator.create(recoveryConfig);
        estimator.consumeRttSample(10_000, 0, TimeSource.now());

        estimator.resetForPath();

        QuicRttEstimator.QuicRttEstimatorState state = estimator.state();
        assertThat(state.rttSampleCount(), is(0L));
        assertThat(state.latestRttMicros(), is(0L));
        assertThat(state.minRttMicros(), is(0L));
        assertThat(state.smoothedRttMicros(), is(100_000L));
        assertThat(state.rttVarMicros(), is(50_000L));
    }

    @Test
    void resetsCongestionWindowForNewPathMtu() {
        QuicRuntimeConfig runtimeConfig = QuicRuntimeConfig.create(QuicConfig.create());
        QuicRttEstimator estimator = QuicRttEstimator.create(runtimeConfig.recovery());
        QuicCongestionController controller = QuicRenoCongestionController.create(runtimeConfig,
                                                                                   "test",
                                                                                   estimator,
                                                                                   1200);
        controller.packetSent(1200);

        controller.resetForPath(1300);

        assertThat(controller.maxDatagramSize(), is(1300L));
        assertThat(controller.congestionWindow(), is(13000L));
    }
}
