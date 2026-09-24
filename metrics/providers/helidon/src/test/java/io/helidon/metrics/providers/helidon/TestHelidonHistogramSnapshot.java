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

package io.helidon.metrics.providers.helidon;

import java.util.List;
import java.util.stream.StreamSupport;

import io.helidon.metrics.api.HistogramSnapshot;
import io.helidon.metrics.api.ValueAtPercentile;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertAll;

class TestHelidonHistogramSnapshot {
    @Test
    void unsortedSamplesYieldExactMinimumQuartilesMedianAndMaximum() {
        HistogramSnapshot snapshot = HelidonHistogramSnapshot.create(5, 25, 9,
                                                                     new double[] {9, 1, 5, 3, 7},
                                                                     new double[] {0, 0.25, 0.5, 0.75, 1},
                                                                     new double[0], new long[0]);

        assertPercentiles(snapshot, List.of(0D, 0.25, 0.5, 0.75, 1D), List.of(1D, 3D, 5D, 7D, 9D));
    }

    @Test
    void percentilesBetweenRanksSelectObservedValuesWithoutInterpolation() {
        HistogramSnapshot snapshot = HelidonHistogramSnapshot.create(4, 100, 40,
                                                                     new double[] {40, 10, 30, 20},
                                                                     new double[] {0, 0.25, Math.nextUp(0.25), 0.5, 0.625, 0.75, 1},
                                                                     new double[0], new long[0]);

        assertPercentiles(snapshot, List.of(0D, 0.25, Math.nextUp(0.25), 0.5, 0.625, 0.75, 1D),
                          List.of(10D, 10D, 20D, 20D, 30D, 30D, 40D));
    }

    private static void assertPercentiles(HistogramSnapshot snapshot, List<Double> coordinates, List<Double> expectedValues) {
        List<? extends ValueAtPercentile> percentiles = StreamSupport
                .stream(snapshot.percentileValues().spliterator(), false)
                .toList();
        assertAll(() -> assertThat("Requested percentile coordinates",
                                  percentiles.stream().map(ValueAtPercentile::percentile).toList(), is(coordinates)),
                  () -> assertThat("Percentiles represent observed sample ranks",
                                   percentiles.stream().map(ValueAtPercentile::value).toList(), is(expectedValues)));
    }
}
