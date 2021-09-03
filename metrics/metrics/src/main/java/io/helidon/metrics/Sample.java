/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.metrics;

/**
 * Common behavior to all types of samples.
 */
interface Sample {

    boolean IS_EXEMPLAR_HANDLING_ACTIVE = ExemplarServiceManager.isActive();

    static Derived derived(double value, Sample.Labeled reference) {
        return new Derived.Impl(value, reference);
    }

    static Derived derived(double value) {
        return new Derived.Impl(value, null);
    }

    static Labeled labeled(long value) {
        return IS_EXEMPLAR_HANDLING_ACTIVE
                ? new Labeled.Impl(value, ExemplarServiceManager.exemplarLabel(), System.currentTimeMillis())
                : new Labeled.Impl(value, ExemplarServiceManager.INACTIVE_LABEL, 0);
    }

    /**
     * Returns the value as a double.
     * <p>
     *     For actual samples this serves as a conversion from long to double so all sample types can be treated somewhat
     *     uniformly.
     * </p>
     * @return value of the sample as a double
     */
    double doubleValue();

    /**
     * Sample that does not exist as an actual observation but is derived from actual observations. E.g., mean.
     * Most derived sample instances have a reference to an actual sample that is an exemplar for the derived sample. Because
     * derived samples are typically computed from actual samples, the value is a double (rather than a long as with the actual
     * samples).
     */
    interface Derived extends Sample {

        Derived ZERO = new Derived.Impl(0.0, null);

        double value();
        Labeled sample();

        class Impl implements Derived {

            private final double value;
            private final Labeled sample;

            Impl(double value, Labeled reference) {
                this.value = value;
                this.sample = reference;
            }

            @Override
            public double value() {
                return value;
            }

            @Override
            public Labeled sample() {
                return sample;
            }

            @Override
            public double doubleValue() {
                return value;
            }
        }
    }

    /**
     * A sample with a label and a timestamp, typically representing actual observations (rather than derived values).
     */
    interface Labeled extends Sample {

        long value();
        String label();
        long timestamp();

        class Impl implements Labeled {
            private final long value;
            private final String label;
            private final long timestamp;

            Impl(long value, String label, long timestamp) {
                this.value = value;
                this.label = label;
                this.timestamp = timestamp;
            }

            @Override
            public long value() {
                return value;
            }

            @Override
            public String label() {
                return label;
            }

            @Override
            public long timestamp() {
                return timestamp;
            }

            @Override
            public double doubleValue() {
                return value;
            }
        }
    }
}
