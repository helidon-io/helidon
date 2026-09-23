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

package io.helidon.metrics.api;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Registry settings for a meter identified by its exact name, regardless of its tags.
 *
 * @since 28.0.0
 */
@Prototype.Configured
@Prototype.Blueprint(decorator = MeterConfigSupport.BuilderDecorator.class)
interface MeterConfigBlueprint {

    /**
     * Meter name to match exactly, before any exporter-specific naming conversion.
     * Wildcards and regular expressions are not interpreted.
     *
     * @return non-blank meter name
     */
    @Option.Configured
    String name();

    /**
     * Whether the meter is enabled. This setting cannot override globally disabled metrics.
     *
     * @return whether the meter is enabled
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean enabled();

    /**
     * Local timer percentiles, preserving builder settings when absent and disabling percentiles when explicitly empty.
     * Values must be finite and between {@code 0.0} and {@code 1.0}, inclusive. Disabling percentiles preserves the timer's
     * count, total time, and maximum. This setting does not disable histogram buckets.
     * <p>
     * These settings override timer builder customizations at registration. Registering an enabled meter other than a
     * {@link io.helidon.metrics.api.Timer} with this setting fails with {@link java.lang.IllegalArgumentException}.
     *
     * @return optional timer percentiles
     */
    @Option.Configured
    Optional<List<Double>> percentiles();

    /**
     * Explicit timer histogram bucket boundaries, preserving builder settings when absent and clearing explicit boundaries
     * when empty. Each duration must be positive and representable in nanoseconds. This setting is independent of local
     * percentiles and automatically generated percentile histogram buckets.
     * <p>
     * These settings override timer builder customizations at registration. Registering an enabled meter other than a
     * {@link io.helidon.metrics.api.Timer} with this setting fails with {@link java.lang.IllegalArgumentException}.
     *
     * @return optional timer bucket boundaries
     */
    @Option.Configured
    Optional<List<Duration>> buckets();

    /**
     * Minimum expected timer duration used to size the histogram, preserving builder settings when absent.
     * The duration must be positive, representable in nanoseconds, and no greater than the maximum expected duration when
     * both are set. Recorded durations below this value are not discarded.
     * <p>
     * This setting overrides timer builder customizations at registration. Registering an enabled meter other than a
     * {@link io.helidon.metrics.api.Timer} with this setting fails with {@link java.lang.IllegalArgumentException}.
     *
     * @return optional minimum expected timer duration
     */
    @Option.Configured
    Optional<Duration> minimumExpectedValue();

    /**
     * Maximum expected timer duration used to size the histogram, preserving builder settings when absent.
     * The duration must be positive, representable in nanoseconds, and no less than the minimum expected duration when
     * both are set. Recorded durations above this value are not discarded.
     * <p>
     * This setting overrides timer builder customizations at registration. Registering an enabled meter other than a
     * {@link io.helidon.metrics.api.Timer} with this setting fails with {@link java.lang.IllegalArgumentException}.
     *
     * @return optional maximum expected timer duration
     */
    @Option.Configured
    Optional<Duration> maximumExpectedValue();
}
