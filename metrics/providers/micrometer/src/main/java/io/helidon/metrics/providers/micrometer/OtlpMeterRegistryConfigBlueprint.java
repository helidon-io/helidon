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

package io.helidon.metrics.providers.micrometer;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.config.Config;

import io.micrometer.registry.otlp.AggregationTemporality;
import io.micrometer.registry.otlp.HistogramFlavor;

/**
 * Helidon-specific implementation of the Micrometer {@link io.micrometer.registry.otlp.OtlpConfig} interface.
 */
@Prototype.Configured
@Prototype.Blueprint
@Prototype.Implement({"io.micrometer.registry.otlp.OtlpConfig"})
@Prototype.CustomMethods(ConfigSupport.OtlpMeterRegistrySupport.CustomMethods.class)
interface OtlpMeterRegistryConfigBlueprint {

    /**
     * The prefix for settings.
     *
     * @return prefix
     */
    @Option.Default("otlp")
    @Option.Configured
    String prefix();

    /**
     * URL to which to send metrics telemetry.
     *
     * @return backend URL
     */
    @Option.Default("http://localhost:4318/v1/metrics")
    @Option.Configured
    String url();

    /**
     * Interval between successive transmissions of metrics data.
     *
     * @return step interval
     */
    @Option.Default("PT60s")
    @Option.Configured
    Duration step();

    /**
     * Attribute name/value pairs to be associated with all metrics transmissions.
     *
     * @return name/value pairs of attribute settings
     */
    @Option.Configured
    Map<String, String> resourceAttributes();

    /**
     * Algorithm to use for adjusting values before transmission.
     *
     * @return aggregation strategy
     */
    @Option.Default("CUMULATIVE")
    @Option.Configured
    AggregationTemporality aggregationTemporality();

    /**
     * Headers to add to each transmission message.
     *
     * @return headers
     */
    @Option.Configured
    Map<String, String> headers();

    /**
     * Type of histogram to use for summarizing values.
     *
     * @return histogram "flavor"
     */
    @Option.Default("EXPLICIT_BUCKET_HISTOGRAM")
    @Option.Configured
    HistogramFlavor histogramFlavor();

    /**
     * Meter-specific (by name) histogram flavors.
     *
     * @return meter-specific histogram flavors
     */
    @Option.Configured
    Map<String, HistogramFlavor> histogramFlavorPerMeter();

    /**
     * Maximum scale value to apply to statistical histogram.
     *
     * @return maximum scale
     */
    @Option.DefaultInt(20)
    @Option.Configured
    int maxScale();

    /**
     * Maximum bucket count to apply to statistical histogram.
     *
     * @return maximum bucket count
     */
    @Option.DefaultInt(160)
    @Option.Configured
    int maxBucketCount();

    /**
     * Maximum number of buckets to use for specific meters.
     *
     * @return meter-specific maxBucket values
     */
    @Option.Configured
    Map<String, Integer> maxBucketsPerMeter();

    /**
     * Base time unit for timers.
     *
     * @return base time unit
     */
    @Option.Default("java.util.concurrent.TimeUnit.MILLISECONDS")
    @Option.Configured
    TimeUnit baseTimeUnit();

    /**
     * Metrics configuration node.
     *
     * @return metrics configuration
     */
    @Option.Redundant
    Config config();

    /**
     * Property values to be returned by the OTLP meter registry configuration.
     *
     * @return properties
     */
    @Option.Configured
    Map<String, String> properties();

}
