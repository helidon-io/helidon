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
class WeightedSnapshot extends Snapshot implements DisplayableLabeledSnapshot {

    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private final WeightedSample[] copy;
    private long[] values = null;
    private final double[] normWeights;
    private final double[] quantiles;
    /**
     * Create a new {@link Snapshot} with the given values.
     *
     * @param values an unordered set of values in the reservoir
     */
    WeightedSnapshot(Collection<WeightedSample> values) {
        copy = values.toArray(new WeightedSample[] {});

        Arrays.sort(copy, Comparator.comparing(WeightedSample::getValue));

        this.normWeights = new double[copy.length];
        this.quantiles = new double[copy.length];

        double sumWeight = 0;
        for (WeightedSample sample : copy) {
            sumWeight += sample.weight;
        }

        for (int i = 0; i < copy.length; i++) {
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
        return value(quantile).value;
    }

    @Override
    public DerivedSample value(double quantile) {
        int posx = slot(quantile);
        return posx == -1 ? DerivedSample.ZERO : new DerivedSample(copy[posx].value, copy[posx].label());
    }

    int slot(double quantile) {
        if ((quantile < 0.0) || (quantile > 1.0) || Double.isNaN(quantile)) {
            throw new IllegalArgumentException(quantile + " is not in [0..1]");
        }

        if (copy.length == 0) {
            return -1;
        }

        int posx = Arrays.binarySearch(quantiles, quantile);
        if (posx < 0) {
            posx = ((-posx) - 1) - 1;
        }

        if (posx < 1) {
            return 0;
        }

        if (posx >= copy.length) {
            return copy.length - 1;
        }

        return posx;
    }

    /**
     * Returns the number of values in the snapshot.
     *
     * @return the number of values
     */
    @Override
    public int size() {
        return copy.length;
    }

    /**
     * Returns the entire set of values in the snapshot.
     *
     * @return the entire set of values
     */
    @Override
    public long[] getValues() {

        if (values == null) {
            long[] result = new long[copy.length];

            int i = 0;
            for (WeightedSample sample : copy) {
                result[i++] = sample.value;
            }
            values = result;
        }
        return values;
    }

    @Override
    public DerivedSample median() {
        return value(0.5);
    }

    @Override
    public DerivedSample sample75thPercentile() {
        return value(0.75);
    }

    @Override
    public DerivedSample sample95thPercentile() {
        return value(0.95);
    }

    @Override
    public DerivedSample sample98thPercentile() {
        return value(0.98);
    }

    @Override
    public DerivedSample sample99thPercentile() {
        return value(0.99);
    }

    @Override
    public DerivedSample sample999thPercentile() {
        return value(0.999);
    }


    /**
     * Returns the highest value in the snapshot.
     *
     * @return the highest value
     */
    @Override
    public long getMax() {
        return max().value;
    }

    @Override
    public WeightedSample max() {
        return copy.length == 0 ? WeightedSample.ZERO : copy[copy.length - 1];
    }

    /**
     * Returns the lowest value in the snapshot.
     *
     * @return the lowest value
     */
    @Override
    public long getMin() {
        return min().value;
    }

    @Override
    public WeightedSample min() {
        return copy.length == 0 ? WeightedSample.ZERO : copy[0];
    }

    /**
     * Returns the weighted arithmetic mean of the values in the snapshot.
     *
     * @return the weighted arithmetic mean
     */
    @Override
    public double getMean() {
        return mean().value;
    }

    @Override
    public DerivedSample mean() {
        if (copy.length == 0) {
            return DerivedSample.ZERO;
        }

        double sum = 0;
        for (int i = 0; i < copy.length; i++) {
            sum += copy[i].value * normWeights[i];
        }

        return new DerivedSample(sum, copy[copy.length / 2].label());
    }

    /**
     * Returns the weighted standard deviation of the values in the snapshot.
     *
     * @return the weighted standard deviation value
     */
    @Override
    public double getStdDev() {
        return stdDev().value;
    }

    @Override
    public DerivedSample stdDev() {
        // two-pass algorithm for variance, avoids numeric overflow

        if (copy.length <= 1) {
            return DerivedSample.ZERO;
        }


        final double mean = mean().value;
        double variance = 0;

        for (int i = 0; i < copy.length; i++) {
            final double diff = copy[i].value - mean;
            variance += normWeights[i] * diff * diff;
        }

        return new DerivedSample(Math.sqrt(variance));
    }

    /**
     * Writes the values of the snapshot to the given stream.
     *
     * @param output an output stream
     */
    public void dump(OutputStream output) {
        final PrintWriter out = new PrintWriter(new OutputStreamWriter(output, UTF_8));
        try {
            for (WeightedSample sample : copy) {
                out.printf("%d,%l,%s%n", sample.value, sample.weight, sample.label());
            }
        } finally {
            out.close();
        }
    }

    /**
     * A sample with a label.
     */
    static class LabeledSample {
        private final String label;

        LabeledSample(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    /**
     * Labeled sample with a recorded value.
     */
    static class WeightedSample extends LabeledSample {

        static final WeightedSample ZERO = new WeightedSample(0, 1.0, "");

        private final long value;
        private final double weight;


        WeightedSample(long value, double weight, String label) {
            super(label);
            this.value = value;
            this.weight = weight;
        }

        WeightedSample(long value) {
            this(value, 1.0, "");
        }

        long getValue() {
            return value;
        }

        double getWeight() {
            return weight;
        }
    }

    /**
     * A labeled sample with a derived value.
     */
    static class DerivedSample extends LabeledSample {

        static final DerivedSample ZERO = new DerivedSample(0.0, "");

        private final double value;

        DerivedSample(double value, String label) {
            super(label);
            this.value = value;
        }

        DerivedSample(double value) {
            this(value, "");
        }

        double value() {
            return value;
        }
    }
}
