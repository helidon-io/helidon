/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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
package io.helidon.integrations.oci.metrics;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.helidon.config.Config;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.metrics.api.Meter;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;

import com.oracle.bmc.monitoring.Monitoring;

/**
 * OCI Metrics Support.
 *
 * @deprecated Use {@link io.helidon.integrations.oci.metrics.OciMetricsService} and its configuration
 * {@link io.helidon.integrations.oci.metrics.OciMetricsConfig}.
 */
@Deprecated(since = "4.4.1", forRemoval = true)
public class OciMetricsSupport implements HttpService {
    private static final System.Logger LOGGER = System.getLogger(OciMetricsSupport.class.getName());

    private final OciMetricsService delegate;

    private OciMetricsSupport(Builder builder) {
        delegate = builder.delegate.build();
    }

    /**
     * Returns a new {@code Builder} for creating an instance of {@code OciMetricsSupport}.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Prescribes behavior for formatting metric names for use by OCI. Here for backward compatibility.
     *
     * @deprecated Use {@link OciMetricsConfig.NameFormatter}.
     */
    @Deprecated(since = "4.4.1", forRemoval = true)
    public interface NameFormatter extends OciMetricsConfig.NameFormatter{
    }

    @Override
    public void routing(HttpRules rules) {
        // noop
    }

    @Override
    public void afterStop() {
        try {
            delegate.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Fluent API builder to create {@link OciMetricsSupport}.
     *
     * @deprecated Use {@link io.helidon.integrations.oci.metrics.OciMetricsConfig.Builder}
     */
    @Deprecated(since = "4.4.1", forRemoval = true)
    @Configured
    public static class Builder implements io.helidon.common.Builder<Builder, OciMetricsSupport> {

        private static final long DEFAULT_SCHEDULER_INITIAL_DELAY = 1L;
        private static final long DEFAULT_SCHEDULER_DELAY = 60L;
        private static final long DEFAULT_BATCH_DELAY = 1L;

        private static final TimeUnit DEFAULT_SCHEDULER_TIME_UNIT = TimeUnit.SECONDS;
        private final OciMetricsConfig.Builder delegate;

        private long initialDelay = DEFAULT_SCHEDULER_INITIAL_DELAY;
        private long delay = DEFAULT_SCHEDULER_DELAY;
        private long batchDelay = DEFAULT_BATCH_DELAY;
        private TimeUnit schedulingTimeUnit = DEFAULT_SCHEDULER_TIME_UNIT;

        private Builder() {
            delegate = OciMetricsConfig.builder();
        }

        @Override
        public OciMetricsSupport build() {
            delegate.initialDelay(Duration.of(initialDelay, schedulingTimeUnit.toChronoUnit()));
            delegate.delay(Duration.of(delay, schedulingTimeUnit.toChronoUnit()));
            delegate.batchDelay(Duration.of(batchDelay, schedulingTimeUnit.toChronoUnit()));

            return new OciMetricsSupport(this);
        }

        /**
         * Sets the initial delay before metrics are sent to OCI
         * (defaults to {@value #DEFAULT_SCHEDULER_INITIAL_DELAY}).
         *
         * @param value initial delay, expressed in time units set by {@link #schedulingTimeUnit(TimeUnit)}
         * @return updated builder
         */
        @ConfiguredOption(value = "1")
        public Builder initialDelay(long value) {
            initialDelay = value;
            return this;
        }

        /**
         * Sets the delay interval between metric posting
         * (defaults to {@value #DEFAULT_SCHEDULER_DELAY}).
         *
         * @param value delay, expressed in time units set by {@link #schedulingTimeUnit(TimeUnit)}
         * @return updated builder
         */
        @ConfiguredOption(value = "60")
        public Builder delay(long value) {
            delay = value;
            return this;
        }

        /**
         * Sets the delay interval if metrics are posted in batches
         * (defaults to {@value #DEFAULT_BATCH_DELAY}).
         *
         * @param value batch delay, expressed in time units set by {@link #schedulingTimeUnit(TimeUnit)}
         * @return updated builder
         */
        @ConfiguredOption(value = "1")
        public Builder batchDelay(long value) {
            batchDelay = value;
            return this;
        }

        /**
         * Sets the time unit applied to the initial delay and delay values (defaults to {@code TimeUnit.SECONDS}).
         *
         * @param timeUnit unit of time
         * @return updated builder
         */
        @ConfiguredOption(value = "TimeUnit.SECONDS")
        public Builder schedulingTimeUnit(TimeUnit timeUnit) {
            Objects.requireNonNull(timeUnit);

            schedulingTimeUnit = timeUnit;
            return this;
        }

        /**
         * Sets the compartment ID.
         *
         * @param value compartment ID
         * @return updated builder
         */
        @ConfiguredOption
        public Builder compartmentId(String value) {
            Objects.requireNonNull(value);
            delegate.compartmentId(value);
            return this;
        }

        /**
         * Sets the namespace.
         *
         * @param value namespace
         * @return updated builder
         */
        @ConfiguredOption
        public Builder namespace(String value) {
            Objects.requireNonNull(value);
            delegate.namespace(value);
            return this;
        }

        /**
         * Sets the {@link NameFormatter} to use in formatting metric
         * names. See the
         * {@link NameFormatter#format(
         * Meter, Meter.Id, String, String)} method for details
         * about the default formatting.
         *
         * @param nameFormatter the formatter to use
         * @return updated builder
         */
        public Builder nameFormatter(NameFormatter nameFormatter) {
            Objects.requireNonNull(nameFormatter);
            delegate.nameFormatter(nameFormatter);
            return this;
        }

        /**
         * Sets the resource group.
         *
         * @param value resource group
         * @return updated builder
         */
        @ConfiguredOption
        public Builder resourceGroup(String value) {
            Objects.requireNonNull(value);
            delegate.resourceGroup(value);
            return this;
        }

        /**
         * Sets whether the description should be enabled or not.
         * <p>
         *     Defaults to {@code true}.
         * </p>
         *
         * @param value enabled
         * @return updated builder
         */
        @ConfiguredOption(value = "true")
        public Builder descriptionEnabled(boolean value) {
            delegate.descriptionEnabled(value);
            return this;
        }

        /**
         * Sets which metrics scopes (e.g., base, vendor, application) should be sent to OCI.
         * <p>
         *     If this method is never invoked, defaults to all scopes.
         * </p>
         *
         * @param value array of metric scopes to process
         * @return updated builder
         */
        @ConfiguredOption(value = "All scopes")
        public Builder scopes(String[] value) {
            Objects.requireNonNull(value);

            return scopes(Arrays.asList(value));
        }

        private Builder scopes(List<String> value) {
            if (value == null || value.isEmpty()) {
                delegate.scopes(Meter.Scope.BUILT_IN_SCOPES);
            } else {
                Set<String> convertedScope = new HashSet<>();
                for (String element: value) {
                    String scopeItem = element.toLowerCase(Locale.ROOT).trim();
                    convertedScope.add(scopeItem);
                }
                delegate.scopes(convertedScope);
            }
            return this;
        }

        /**
         * Sets whether metrics transmission to OCI is enabled.
         * <p>
         *     Defaults to {@code true}.
         * </p>
         * @param value whether metrics transmission should be enabled
         * @return updated builder
         */
        @ConfiguredOption(value = "true")
        public Builder enabled(boolean value) {
            delegate.enabled(value);
            return this;
        }

        /**
         * Sets the maximum no. of metrics to send in a batch
         * (defaults to {@value OciMetricsConfig#DEFAULT_BATCH_SIZE}).
         *
         * @param value maximum no. of metrics to send in a batch
         * @return updated builder
         */
        @ConfiguredOption(value = "50")
        public Builder batchSize(int value) {
            delegate.batchSize(value);
            return this;
        }

        /**
         * Updates the builder using the specified OCI metrics {@link Config} node.
         * @param config {@code Config} node containing the OCI metrics settings
         * @return updated builder
         */
        public Builder config(Config config) {
            config.get("initialDelay").asLong().ifPresent(this::initialDelay);
            config.get("delay").asLong().ifPresent(this::delay);
            config.get("batchDelay").asLong().ifPresent(this::batchDelay);
            config.get("schedulingTimeUnit").as(Builder::toSchedulingTimeUnit).ifPresent(this::schedulingTimeUnit);
            config.get("compartmentId").asString().ifPresent(this::compartmentId);
            config.get("namespace").asString().ifPresent(this::namespace);
            config.get("resourceGroup").asString().ifPresent(this::resourceGroup);
            config.get("scopes").asList(String.class).ifPresent(this::scopes);
            config.get("batchSize").asInt().ifPresent(this::batchSize);
            config.get("enabled").asBoolean().ifPresent(this::enabled);
            config.get("descriptionEnabled").asBoolean().ifPresent(this::descriptionEnabled);
            return this;
        }

        /**
         * Sets the {@link Monitoring} client instance to use in sending metrics to OCI.
         *
         * @param monitoringClient the {@code MonitoringClient} instance
         * @return updated builder
         */
        public Builder monitoringClient(Monitoring monitoringClient) {
            delegate.monitoringClient(monitoringClient);
            return this;
        }

        /**
         * Returns boolean value to indicate whether OciMetricsSupport service will be activated or not.
         *
         * @return {@code true} if OciMetricsSupport service will be activated or
         *         {@code false} if it not
         */
        public boolean enabled() {
            return delegate.enabled();
        }

        private static TimeUnit toSchedulingTimeUnit(Config value) {
            Optional<String> timeUnitValue = value.asString().map(s -> s.toUpperCase(Locale.ROOT));
            if (timeUnitValue.isEmpty() || timeUnitValue.get().isBlank()) {
                throw new IllegalArgumentException("Required value for schedulingTimeUnit is missing");
            }
            return TimeUnit.valueOf(timeUnitValue.get());
        }
    }
}
