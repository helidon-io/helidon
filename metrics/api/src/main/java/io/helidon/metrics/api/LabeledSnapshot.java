/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates.
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

import io.helidon.metrics.api.Sample.Derived;
import io.helidon.metrics.api.Sample.Labeled;

/**
 * Internal interface prescribing minimum behavior of a snapshot needed to produce output.
 */
public interface LabeledSnapshot {
    /**
     * Value of a specific quantile.
     *
     * @param quantile quantile to get value for
     * @return derived value of the quantile
     */
    Derived value(double quantile);

    /**
     * Median value.
     *
     * @return median
     */
    Derived median();

    /**
     * Maximal value.
     *
     * @return max
     */
    Labeled max();

    /**
     * Minimal value.
     *
     * @return min
     */
    Labeled min();

    /**
     * Mean value.
     *
     * @return mean
     */
    Derived mean();

    /**
     * Standard deviation.
     *
     * @return standard deviation
     */
    Derived stdDev();

    /**
     * 75th percentile value.
     *
     * @return 75th percentile value
     */
    Derived sample75thPercentile();

    /**
     * 95th percentile value.
     *
     * @return 95th percentile value
     */
    Derived sample95thPercentile();

    /**
     * 98th percentile value.
     *
     * @return 98th percentile value
     */
    Derived sample98thPercentile();

    /**
     * 99th percentile value.
     *
     * @return 99th percentile value
     */
    Derived sample99thPercentile();

    /**
     * 99.9 percentile value.
     *
     * @return 99.9 percentile value
     */
    Derived sample999thPercentile();
}
