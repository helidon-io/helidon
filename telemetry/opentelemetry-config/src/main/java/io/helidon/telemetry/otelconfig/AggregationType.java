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

/**
 * Type of OpenTelemetry metric aggregations.
 */
public enum AggregationType {

    /**
     * Drops all metrics; exports no metrics.
     */
    DROP,

    /**
     * Default aggregation for a given instrument type.
     */
    DEFAULT,

    /**
     * Aggregates measurements into a double sum or long sum.
     */
    SUM,

    /**
     * Records the last seen measurement as a double aauge or long gauge.
     */
    LAST_VALUE,

    /**
     * Aggregates measurements into a histogram using default or explicit bucket boundaries.
     */
    EXPLICIT_BUCKET_HISTOGRAM,

    /**
     * Aggregates measurements into a base-2 exponential histogram using default or explicit maximum number
     * of buckets and maximum scale.
     */
    BASE2_EXPONENTIAL_BUCKET_HISTOGRAM;
}
