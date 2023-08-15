/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.metrics.api;

import java.io.PrintStream;
import java.util.concurrent.TimeUnit;

/**
 * Snapshot in time of a histogram.
 */
public interface HistogramSnapshot extends Wrapper {

    /**
     * Returns an "empty" snapshot which has summary values but no data points.
     *
     * @param count count of observations the snapshot should report
     * @param total total value of observations the snapshot should report
     * @param max maximum value the snapshot should report
     * @return empty snapshot reporting the values as specified
     */
    static HistogramSnapshot empty(long count, double total, double max) {
        return MetricsFactory.getInstance().histogramSnapshotEmpty(count, total, max);
    }

    /**
     * Returns the count of observations in the snapshot.
     *
     * @return count of observations
     */
    long count();

    /**
     * Returns the total value over all observations in the snapshot.
     *
     * @return total value over all observations
     */
    double total();

    /**
     * Returns the total value over all observations, interpreting the values as times in nanoseconds and expressing the time
     * in the specified {@link java.util.concurrent.TimeUnit}.
     *
     * @param timeUnit time unit in which to express the total value
     * @return total value expressed in the selected time unit
     */
    double total(TimeUnit timeUnit);

    /**
     * Returns the maximum value over all observations.
     *
     * @return maximum value
     */
    double max();

    /**
     * Returns the average value overall observations.
     *
     * @return average value
     */
    double mean();

    /**
     * Returns the average value over all observations, interpreting the values as times in nanoseconds and expressing the
     * average in the specified {@link java.util.concurrent.TimeUnit}.
     *
     * @param timeUnit time unitin which to express the average
     * @return average value expressed in the selected time unit
     */
    double mean(TimeUnit timeUnit);

    /**
     * Returns the values at the configured percentiles for the histogram.
     *
     * @return pairs of percentile and the histogram value at that percentile
     */
    Iterable<ValueAtPercentile> percentileValues();

    /**
     * Returns information about each of the configured buckets for the histogram.
     *
     * @return pairs of boundary value and count of observations in that boundary
     */
    Iterable<Bucket> histogramCounts();

    /**
     * Dumps a summary of the snapshot to the specified {@link java.io.PrintStream} using the indicated scaling factor for
     * observations.
     *
     * @param out {@code PrintStream} to which to dump the snapshot summary
     * @param scale scale factor to apply to observations for output
     */
    void outputSummary(PrintStream out, double scale);
}
