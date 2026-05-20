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

package io.helidon.telemetry.otelconfig;

import java.util.List;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Settings for explicit bucket histogram default aggregation.
 */
@Prototype.Blueprint
@Prototype.Configured
@Prototype.CustomMethods(ExplicitBucketHistogramAggregationSupport.CustomMethods.class)
@Prototype.IncludeDefaultMethods
interface ExplicitBucketHistogramAggregationConfigBlueprint {

    /**
     * Explicit bucket boundaries.
     *
     * @return bucket boundaries
     */
    @Option.Configured
    List<Double> bucketBoundaries();

    /**
     * Whether the min and max should be recorded.
     *
     * @return whether to record min and max
     */
    @Option.Configured
    @Option.DefaultBoolean(ExplicitBucketHistogramAggregationSupport.DEFAULT_RECORD_MIN_MAX)
    default boolean recordMinMax() {
        return ExplicitBucketHistogramAggregationSupport.DEFAULT_RECORD_MIN_MAX;
    }
}
