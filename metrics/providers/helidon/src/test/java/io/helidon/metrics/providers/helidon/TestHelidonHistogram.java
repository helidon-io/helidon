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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import io.helidon.metrics.api.HistogramSnapshot;
import io.helidon.metrics.api.ValueAtPercentile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertAll;

class TestHelidonHistogram {
    static Stream<Arguments> defensiveCopyCases() {
        List<Double> large = largePercentileOrder();
        return Stream.of(Arguments.of("empty", new double[0], List.of()),
                         Arguments.of("singleton", new double[] {0.25}, List.of(0.25)),
                         Arguments.of("ordered unique", new double[] {0, 0.5, 1}, List.of(0D, 0.5, 1D)),
                         Arguments.of("small unique", new double[] {1, 0.5, 0}, List.of(1D, 0.5, 0D)),
                         Arguments.of("small repeated", new double[] {1, 0.5, 1, 0.5, 0}, List.of(1D, 0.5, 0D)),
                         Arguments.of("large unique", large.stream().mapToDouble(Double::doubleValue).toArray(), large));
    }

    @Test
    void largeUnsortedDuplicateRequestsKeepFirstOccurrenceOrder() {
        List<Double> expected = largePercentileOrder();
        double[] requested = new double[16 * expected.size()];
        for (int repetition = 0; repetition < 16; repetition++) {
            for (int index = 0; index < expected.size(); index++) {
                requested[repetition * expected.size() + index] = expected.get((index + repetition * 31) % expected.size());
            }
        }
        double[] original = requested.clone();
        var histogram = HelidonHistogram.create(requested, new double[0]);
        HistogramSnapshot empty = histogram.snapshot();
        histogram.record(1.25);

        assertAll(() -> verifyPercentiles(empty, expected, Double.NaN, 0),
                  () -> verifyPercentiles(histogram.snapshot(), expected, 1.25, 1),
                  () -> assertThat("Normalization does not sort or compact the caller's array", requested, is(original)));
    }

    @ParameterizedTest(name = "{0} repetitions")
    @ValueSource(ints = {1, 128})
    void differentNaNsDeduplicateWhileSignedZeroPercentilesRemainDistinct(int repetitions) {
        double firstNaN = Double.longBitsToDouble(0x7ff8000000000001L);
        double otherNaN = Double.longBitsToDouble(0xfff8000000000042L);
        double[] sequence = {firstNaN, -0D, 0.5, otherNaN, 0D, firstNaN, -0D, 1, 0D};
        double[] requested = new double[sequence.length * repetitions];
        for (int repetition = 0; repetition < repetitions; repetition++) {
            System.arraycopy(sequence, 0, requested, repetition * sequence.length, sequence.length);
        }
        var histogram = HelidonHistogram.create(requested, new double[0]);
        histogram.record(2);
        HistogramSnapshot snapshot = histogram.snapshot();

        assertAll(() -> verifyPercentiles(snapshot, List.of(Double.NaN, -0D, 0.5, 0D, 1D), 2, 1),
                  () -> assertThat("The first NaN representation survives equality-based deduplication",
                                   Double.doubleToRawLongBits(snapshot.percentileValues().iterator().next().percentile()),
                                   is(Double.doubleToRawLongBits(firstNaN))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("defensiveCopyCases")
    void laterCallerMutationCannotChangeConfiguredPercentiles(String description, double[] requested, List<Double> expected) {
        double[] original = requested.clone();
        var histogram = HelidonHistogram.create(requested, new double[0]);
        assertThat(description + " input remains unchanged during construction", requested, is(original));
        Arrays.fill(requested, -1);
        HistogramSnapshot empty = histogram.snapshot();
        histogram.record(1.25);

        assertAll(() -> verifyPercentiles(empty, expected, Double.NaN, 0),
                  () -> verifyPercentiles(histogram.snapshot(), expected, 1.25, 1));
    }

    private static List<Double> largePercentileOrder() {
        List<Double> percentiles = new ArrayList<>(257);
        // A permutation of 257 distinct, exactly representable values across the inclusive range.
        for (int index = 0; index < 257; index++) {
            percentiles.add(((index * 73) % 257) / 256D);
        }
        return List.copyOf(percentiles);
    }

    private static void verifyPercentiles(HistogramSnapshot snapshot, List<Double> expected, double value, long count) {
        List<? extends ValueAtPercentile> percentiles = StreamSupport
                .stream(snapshot.percentileValues().spliterator(), false)
                .toList();
        assertAll(() -> assertThat("Distinct percentiles retain their first-occurrence order",
                                  percentiles.stream().map(ValueAtPercentile::percentile).toList(), is(expected)),
                  () -> assertThat("Empty or single-valued observations give deterministic percentile values",
                                   percentiles.stream().map(ValueAtPercentile::value).toList(), everyItem(is(value))),
                  () -> assertThat("Percentile normalization does not affect observation count", snapshot.count(), is(count)));
    }
}
