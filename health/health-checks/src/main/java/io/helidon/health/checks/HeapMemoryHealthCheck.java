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

package io.helidon.health.checks;

import java.util.Formatter;
import java.util.Locale;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import io.helidon.config.Config;
import io.helidon.health.common.BuiltInHealthCheck;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

/**
 * A health check that verifies whether the server is running out of Java heap space. If heap usage exceeds a
 * specified threshold, then the health check will fail.
 * <p>
 * By default, this health check has a threshold of {@value DEFAULT_THRESHOLD} ({@value DEFAULT_THRESHOLD}%).
 * If heap usage exceeds this level, then the server
 * is considered to be unhealthy. This default can be modified using the
 * {@value CONFIG_KEY_THRESHOLD_PERCENT} property. The threshold should be set as a percent, such as
 * 50 for 50% or 99 for 99%.
 * </p>
 * <p>
 * This health check is automatically created and registered through CDI.
 * </p>
 * <p>
 * This health check can be referred to in properties as {@code heapMemory}. So for example, to exclude this
 * health check from being exposed, use {@code helidon.health.exclude: heapMemory}.
 * </p>
 */
@Liveness
@ApplicationScoped // this will be ignored if not within CDI
@BuiltInHealthCheck
public final class HeapMemoryHealthCheck implements HealthCheck {
    /**
     * Default threshold percentage.
     */
    public static final double DEFAULT_THRESHOLD = 98;

    static final String CONFIG_KEY_HEAP_PREFIX = "heapMemory";

    static final String CONFIG_KEY_THRESHOLD_PERCENT_SUFFIX = "thresholdPercent";

    /**
     * Config property key for heap memory threshold.
     */
    public static final String CONFIG_KEY_THRESHOLD_PERCENT = HealthChecks.CONFIG_KEY_HEALTH_PREFIX
            + "." + CONFIG_KEY_HEAP_PREFIX
            + "." + CONFIG_KEY_THRESHOLD_PERCENT_SUFFIX;

    private final Runtime rt;
    private final double thresholdPercent;

    // this will be ignored if not within CDI
    @Inject
    HeapMemoryHealthCheck(
            Runtime runtime,
            @ConfigProperty(name = CONFIG_KEY_THRESHOLD_PERCENT, defaultValue = "98") double threshold) {
        this.thresholdPercent = threshold;
        this.rt = runtime;
    }

    private HeapMemoryHealthCheck(Builder builder) {
        this.thresholdPercent = builder.threshold;
        this.rt = Runtime.getRuntime();
    }

    /**
     * Create a new fluent API builder to configure a new health check.
     *
     * @return builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a new heap memory health check with default configuration.
     *
     * @return a new health check to register with
     *         {@link io.helidon.health.HealthSupport.Builder#addLiveness(org.eclipse.microprofile.health.HealthCheck...)}
     * @see #DEFAULT_THRESHOLD
     */
    public static HeapMemoryHealthCheck create() {
        return builder().build();
    }

    @Override
    public HealthCheckResponse call() {
        //Formatter ensures that returned delimiter will be always the same
        Formatter formatter = new Formatter(Locale.US);
        final long freeMemory = rt.freeMemory();
        final long totalMemory = rt.totalMemory();
        final long maxMemory = rt.maxMemory();
        final long usedMemory = totalMemory - freeMemory;
        final long threshold = (long) ((thresholdPercent / 100) * maxMemory);
        return HealthCheckResponse.named("heapMemory")
                .state(threshold >= usedMemory)
                .withData("percentFree",
                          formatter.format("%.2f%%", 100 * ((double) (maxMemory - usedMemory) / maxMemory)).toString())
                .withData("free", DiskSpaceHealthCheck.format(freeMemory))
                .withData("freeBytes", freeMemory)
                .withData("max", DiskSpaceHealthCheck.format(maxMemory))
                .withData("maxBytes", maxMemory)
                .withData("total", DiskSpaceHealthCheck.format(totalMemory))
                .withData("totalBytes", totalMemory)
                .build();
    }

    /**
     * Fluent API builder for {@link io.helidon.health.checks.HeapMemoryHealthCheck}.
     */
    public static final class Builder implements io.helidon.common.Builder<HeapMemoryHealthCheck> {
        private double threshold = DEFAULT_THRESHOLD;

        private Builder() {
        }

        @Override
        public HeapMemoryHealthCheck build() {
            return new HeapMemoryHealthCheck(this);
        }

        /**
         * Threshol percentage. If used memory is above this threshold, reports the system is down.
         *
         * @param threshold threshold percentage (e.g. 87.47)
         * @return updated builder instance
         */
        public Builder thresholdPercent(double threshold) {
            this.threshold = threshold;
            return this;
        }

        /**
         * Set up the heap space health check via config key, if present.
         *
         * Configuration options:
         * <table class="config">
         * <caption>Heap space health check configuration</caption>
         * <tr>
         *     <th>Key</th>
         *     <th>Default Value</th>
         *     <th>Description</th>
         *     <th>Builder method</th>
         * </tr>
         * <tr>
         *     <td>{@value CONFIG_KEY_THRESHOLD_PERCENT_SUFFIX}</td>
         *     <td>{@value DEFAULT_THRESHOLD}</td>
         *     <td>Minimum percent of heap memory consumed for this health check to fail</td>
         *     <td>{@link #thresholdPercent(double)}</td>
         * </tr>
         * </table>
         *
         * @param config {@code Config} node for heap memory
         * @return updated builder instance
         */
        public Builder config(Config config) {
            config.get(CONFIG_KEY_THRESHOLD_PERCENT_SUFFIX)
                    .asDouble()
                    .ifPresent(this::thresholdPercent);

            return this;
        }
    }
}
