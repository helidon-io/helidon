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
import java.time.temporal.ChronoUnit;

import io.helidon.quic.QuicRttEstimator.QuicRttEstimatorState;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertAll;

class QuicRttEstimatorTest {
    @ParameterizedTest(name = "first sample ignores ACK delay {0} us")
    @ValueSource(longs = {0, 2_000, Long.MAX_VALUE})
    void shouldIgnoreAckDelayForFirstSample(long ackDelayMicros) {
        QuicRttEstimator estimator = QuicRttEstimator.create(QuicRuntimeConfig.create(QuicConfig.create()).recovery());

        estimator.consumeRttSample(10_000, ackDelayMicros, TimeSource.now());

        assertEstimate(estimator, QuicRttEstimatorState.create(10_000, 10_000, 10_000, 5_000, 1), 30_000);
    }

    @ParameterizedTest(name = "subsequent sample ignores excessive ACK delay {0} us")
    @ValueSource(longs = {
            Long.MAX_VALUE - 10_000,
            Long.MAX_VALUE - 9_999,
            Long.MAX_VALUE - 1,
            Long.MAX_VALUE
    })
    void shouldIgnoreExcessiveAckDelayWithoutCorruptingEstimator(long ackDelayMicros) {
        QuicRttEstimator estimator = QuicRttEstimator.create(QuicRuntimeConfig.create(QuicConfig.create()).recovery());
        estimator.consumeRttSample(10_000, 0, TimeSource.now());

        estimator.consumeRttSample(12_000, ackDelayMicros, TimeSource.now());

        // The sample exceeds the minimum by only 2 ms, so none of these delays can be subtracted.
        // An unadjusted 12 ms sample gives a 10.25 ms smoothed RTT and 4.25 ms variation.
        assertEstimate(estimator, QuicRttEstimatorState.create(12_000, 10_000, 10_250, 4_250, 2), 27_250);

        estimator.consumeRttSample(10_000, 0, TimeSource.now());

        // A following ordinary sample must retain a usable history, including integer-microsecond rounding.
        assertEstimate(estimator, QuicRttEstimatorState.create(10_000, 10_000, 10_218, 3_250, 3), 23_218);
    }

    @ParameterizedTest(name = "RTT sample {0} us with ACK delay {1} us")
    @CsvSource({
            "14000, 2000, 10000, 10250, 4250, 27250",
            "14000, 4000, 10000, 10000, 3750, 25000",
            "14000, 4001, 10000, 10500, 4750, 29500",
            "10000,    1, 10000, 10000, 3750, 25000",
            " 8000, 2000,  8000,  9750, 4250, 26750"
    })
    void shouldAdjustAckDelayOnlyWhenAdjustedSampleIsAtLeastMinimum(long latestRttMicros,
                                                                   long ackDelayMicros,
                                                                   long minRttMicros,
                                                                   long smoothedRttMicros,
                                                                   long rttVarMicros,
                                                                   long ptoMicros) {
        QuicRttEstimator estimator = QuicRttEstimator.create(QuicRuntimeConfig.create(QuicConfig.create()).recovery());
        estimator.consumeRttSample(10_000, 0, TimeSource.now());

        estimator.consumeRttSample(latestRttMicros, ackDelayMicros, TimeSource.now());

        assertEstimate(estimator,
                       QuicRttEstimatorState.create(latestRttMicros, minRttMicros, smoothedRttMicros, rttVarMicros, 2),
                       ptoMicros);
    }

    private static void assertEstimate(QuicRttEstimator estimator,
                                       QuicRttEstimatorState expectedState,
                                       long expectedPtoMicros) {
        QuicRttEstimatorState state = estimator.state();
        Duration pto = estimator.basePtoDuration();
        assertAll(() -> assertThat("RTT estimator state", state, is(expectedState)),
                  () -> assertThat("base PTO", pto, is(Duration.of(expectedPtoMicros, ChronoUnit.MICROS))),
                  () -> assertThat("base PTO remains positive", pto, greaterThan(Duration.ZERO)));
    }
}
