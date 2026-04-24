/*
 * Copyright (c) 2021, 2026 Oracle and/or its affiliates.
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
 * Snapshot view of a metric's derived and labeled sample values.
 * <p>
 * Implementations expose summary values computed for the metric at a point in time, such as quantiles, max, mean,
 * and observation count.
 * </p>
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
     * Maximal value.
     *
     * @return max
     */
    Labeled max();

    /**
     * Mean value.
     *
     * @return mean
     */
    Derived mean();

    /**
     * Number of values represented by the snapshot.
     *
     * @return number of values
     */
    long size();
}
