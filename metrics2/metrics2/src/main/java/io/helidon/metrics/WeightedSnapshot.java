/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.metrics;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;

import org.eclipse.microprofile.metrics.Snapshot;

/*
 * This class is heavily inspired by:
 * WeightedSnapshot
 *
 * From Dropwizard Metrics v 3.2.3.
 * Distributed under Apache License, Version 2.0
 *
 */

/**
 * A statistical snapshot of a {@link WeightedSnapshot}.
 */
class WeightedSnapshot extends Snapshot {

    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private final long[] values;
    private final double[] normWeights;
    private final double[] quantiles;
    /**
     * Create a new {@link Snapshot} with the given values.
     *
     * @param values an unordered set of values in the reservoir
     */
    WeightedSnapshot(Collection<WeightedSample> values) {
        final WeightedSample[] copy = values.toArray(new WeightedSample[] {});

        Arrays.sort(copy, Comparator.comparingLong(o -> o.value));

        this.values = new long[copy.length];
        this.normWeights = new double[copy.length];
        this.quantiles = new double[copy.length];

        double sumWeight = 0;
        for (WeightedSample sample : copy) {
            sumWeight += sample.weight;
        }

        for (int i = 0; i < copy.length; i++) {
            this.values[i] = copy[i].value;
            /*
             * A zero denominator could cause the resulting double to be infinity or, if the numerator is also 0,
             * NaN. Either causes problems when formatting for JSON output. Just use 0 instead.
             */
            this.normWeights[i] = sumWeight != 0 ? copy[i].weight / sumWeight : 0;
        }

        for (int i = 1; i < copy.length; i++) {
            this.quantiles[i] = this.quantiles[i - 1] + this.normWeights[i - 1];
        }
    }

    /**
     * Returns the value at the given quantile.
     *
     * @param quantile a given quantile, in {@code [0..1]}
     * @return the value in the distribution at {@code quantile}
     */
    @Override
    public double getValue(double quantile) {
        if ((quantile < 0.0) || (quantile > 1.0) || Double.isNaN(quantile)) {
            throw new IllegalArgumentException(quantile + " is not in [0..1]");
        }

        if (values.length == 0) {
            return 0.0;
        }

        int posx = Arrays.binarySearch(quantiles, quantile);
        if (posx < 0) {
            posx = ((-posx) - 1) - 1;
        }

        if (posx < 1) {
            return values[0];
        }

        if (posx >= values.length) {
            return values[values.length - 1];
        }

        return values[(int) posx];
    }

    /**
     * Returns the number of values in the snapshot.
     *
     * @return the number of values
     */
    @Override
    public int size() {
        return values.length;
    }

    /**
     * Returns the entire set of values in the snapshot.
     *
     * @return the entire set of values
     */
    @Override
    public long[] getValues() {
        return Arrays.copyOf(values, values.length);
    }

    /**
     * Returns the highest value in the snapshot.
     *
     * @return the highest value
     */
    @Override
    public long getMax() {
        if (values.length == 0) {
            return 0;
        }
        return values[values.length - 1];
    }

    /**
     * Returns the lowest value in the snapshot.
     *
     * @return the lowest value
     */
    @Override
    public long getMin() {
        if (values.length == 0) {
            return 0;
        }
        return values[0];
    }

    /**
     * Returns the weighted arithmetic mean of the values in the snapshot.
     *
     * @return the weighted arithmetic mean
     */
    @Override
    public double getMean() {
        if (values.length == 0) {
            return 0;
        }

        double sum = 0;
        for (int i = 0; i < values.length; i++) {
            sum += values[i] * normWeights[i];
        }
        return sum;
    }

    /**
     * Returns the weighted standard deviation of the values in the snapshot.
     *
     * @return the weighted standard deviation value
     */
    @Override
    public double getStdDev() {
        // two-pass algorithm for variance, avoids numeric overflow

        if (values.length <= 1) {
            return 0;
        }

        final double mean = getMean();
        double variance = 0;

        for (int i = 0; i < values.length; i++) {
            final double diff = values[i] - mean;
            variance += normWeights[i] * diff * diff;
        }

        return Math.sqrt(variance);
    }

    /**
     * Writes the values of the snapshot to the given stream.
     *
     * @param output an output stream
     */
    @Override
    public void dump(OutputStream output) {
        final PrintWriter out = new PrintWriter(new OutputStreamWriter(output, UTF_8));
        try {
            for (long value : values) {
                out.printf("%d%n", value);
            }
        } finally {
            out.close();
        }
    }

    /**
     * A single sample item with value and its weights for {@link WeightedSnapshot}.
     */
    static class WeightedSample {
        private final long value;
        private final double weight;

        WeightedSample(long value, double weight) {
            this.value = value;
            this.weight = weight;
        }

        long getValue() {
            return value;
        }

        double getWeight() {
            return weight;
        }
    }
}
