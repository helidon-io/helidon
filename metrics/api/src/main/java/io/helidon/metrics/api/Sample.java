/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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

/**
 * Common behavior to all types of samples.
 */
public interface Sample {

    /**
     * Create a new derived value with a reference.
     *
     * @param value value
     * @param reference reference
     * @return a new derived value
     */
    static Derived derived(double value, Labeled reference) {
        return new DerivedSample(value, reference);
    }

    /**
     * Create a new derived value without a reference.
     *
     * @param value value
     * @return a new derived value
     */
    static Derived derived(double value) {
        return new DerivedSample(value, null);
    }

    /**
     * Create a labeled value.
     *
     * @param value value
     * @return a new labeled value if exemplar handling is supported
     */
    static Labeled labeled(double value) {
        return ExemplarServiceManager.isActive()
                ? new LabeledSample(value, ExemplarServiceManager.exemplarLabel(), System.currentTimeMillis())
                : new LabeledSample(value, ExemplarServiceManager.INACTIVE_LABEL, 0);
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
        /**
         * A derived sample with zero value and no reference.
         */
        Derived ZERO = new DerivedSample(0.0, null);

        /**
         * Derived value (usually computed).
         *
         * @return value
         */
        double value();

        /**
         * Sample.
         *
         * @return sample
         */
        Labeled sample();

    }

    /**
     * A sample with a label and a timestamp, typically representing actual observations (rather than derived values).
     */
    interface Labeled extends Sample {
        /**
         * The value.
         * @return value
         */
        double value();

        /**
         * Value label.
         *
         * @return lavel
         */
        String label();

        /**
         * Timestamp the value was recorded.
         *
         * @return timestamp
         */
        long timestamp();

    }
}
