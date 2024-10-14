/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.config.Config;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.DistributionSummary;
import io.helidon.metrics.api.FunctionalCounter;
import io.helidon.metrics.api.Gauge;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.Timer;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;

import com.oracle.bmc.monitoring.Monitoring;
import com.oracle.bmc.monitoring.model.MetricDataDetails;
import com.oracle.bmc.monitoring.model.PostMetricDataDetails;
import com.oracle.bmc.monitoring.requests.PostMetricDataRequest;

/**
 * OCI Metrics Support.
 */
public class OciMetricsSupport implements HttpService {
    private static final System.Logger LOGGER = System.getLogger(OciMetricsSupport.class.getName());

    private static final UnitConverter STORAGE_UNIT_CONVERTER = UnitConverter.storageUnitConverter();
    private static final UnitConverter TIME_UNIT_CONVERTER = UnitConverter.timeUnitConverter();
    private static final List<UnitConverter> UNIT_CONVERTERS = List.of(STORAGE_UNIT_CONVERTER, TIME_UNIT_CONVERTER);
    private static final NameFormatter DEFAULT_NAME_FORMATTER = new NameFormatter() { };

    private ScheduledExecutorService scheduledExecutorService;

    private final String compartmentId;
    private final String namespace;
    private final NameFormatter nameFormatter;
    private final long initialDelay;
    private final long delay;
    private final long batchDelay;
    private final TimeUnit schedulingTimeUnit;
    private final String resourceGroup;
    private final boolean descriptionEnabled;
    private final Set<String> scopes;
    private final int batchSize;
    private final boolean enabled;

    private final Monitoring monitoringClient;
    private OciMetricsData ociMetricsData;

    private OciMetricsSupport(Builder builder) {
        initialDelay = builder.initialDelay;
        delay = builder.delay;
        batchDelay = builder.batchDelay;
        schedulingTimeUnit = builder.schedulingTimeUnit;
        compartmentId = builder.compartmentId;
        namespace = builder.namespace;
        nameFormatter = builder.nameFormatter;
        resourceGroup = builder.resourceGroup;
        descriptionEnabled = builder.descriptionEnabled;
        scopes = builder.scopes;
        batchSize = builder.batchSize;
        enabled = builder.enabled;
        this.monitoringClient = builder.monitoringClient;
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
     * Prescribes behavior for formatting metric names for use by OCI.
     */
    public interface NameFormatter {

        /**
         * Formats a metric name for OCI.
         * <p>
         *     The default implementation creates an OCI metric name with this format:
         *     {@code metric-name[_suffix][_units]}
         *     where {@code _suffix} is omitted if the caller passes a null suffix, and {@code _units} is omitted if the metrics
         *     metadata does not have units set or, in translating the units for OCI, the result is blank.
         * </p>
         *
         * @param metric the metric to be formatted
         * @param metricId {@code MetricID} of the metric being formatted
         * @param suffix name suffix to append to the recorded metric name (e.g, "total"); can be null
         * @param unit metric unit
         * @return the formatted metric name
         */
        default String format(Meter metric, Meter.Id metricId, String suffix, String unit) {

            StringBuilder result = new StringBuilder(metricId.name());
            if (suffix != null) {
                result.append("_").append(suffix);
            }
            result.append("_").append(textType(metric).replace(" ", "_"));

            String units = formattedBaseUnits(unit);
            if (units != null && !units.isBlank()) {
                result.append("_").append(units);
            }
            return result.toString();
        }

        /**
         * Converts a metric instance into the corresponding text representation of its metric type.
         *
         * @param metric {@link io.helidon.metrics.api.Meter} to be converted
         * @return text type of the metric
         */
        static String textType(Meter metric) {
            if (metric instanceof Counter) {
                return "counter";
            }
            if (metric instanceof FunctionalCounter) {
                return "counter";
            }
            if (metric instanceof Gauge) {
                return "gauge";
            }
            if (metric instanceof DistributionSummary) {
                return "histogram";
            }
            if (metric instanceof Timer) {
                return "timer";
            }
            throw new IllegalArgumentException("Cannot map metric of type " + metric.getClass().getName());
        }
    }

    static String formattedBaseUnits(String metricUnits) {
        String baseUnits = baseMetricUnits(metricUnits);
        return baseUnits == null ? "" : baseUnits;
    }

    static String baseMetricUnits(String metricUnits) {
        if (metricUnits != null && !Meter.BaseUnits.NONE.equals(metricUnits) && !metricUnits.isEmpty()) {
            for (UnitConverter converter : UNIT_CONVERTERS) {
                if (converter.handles(metricUnits)) {
                    return converter.baseUnits();
                }
            }
        }
        return null;
    }

    private void startExecutor() {
        Context ctx = Contexts.context().orElseGet(Contexts::globalContext);
        scheduledExecutorService = Executors.newScheduledThreadPool(1);
        scheduledExecutorService.scheduleAtFixedRate(() -> {
            Contexts.runInContext(ctx, this::pushMetrics);
        }, initialDelay, delay, schedulingTimeUnit);
    }

    private void pushMetrics() {
        List<MetricDataDetails> allMetricDataDetails = ociMetricsData.getMetricDataDetails();
        LOGGER.log(System.Logger.Level.TRACE, String.format("Processing %d metrics", allMetricDataDetails.size()));

        if (allMetricDataDetails.size() > 0) {
            while (true) {
                if (allMetricDataDetails.size() > batchSize) {
                    postBatch(allMetricDataDetails.subList(0, batchSize));
                    // discard metrics that had been posted
                    allMetricDataDetails.subList(0, batchSize).clear();
                    if (batchDelay > 0L) {
                        try {
                            schedulingTimeUnit.sleep(batchDelay);
                        } catch (InterruptedException ignore) {
                        }
                    }
                } else {
                    postBatch(allMetricDataDetails);
                    break;
                }
            }
        }
    }

    private void postBatch(List<MetricDataDetails> metricDataDetailsList) {
        PostMetricDataDetails postMetricDataDetails = PostMetricDataDetails.builder()
                .metricData(metricDataDetailsList)
                .build();

        PostMetricDataRequest postMetricDataRequest = PostMetricDataRequest.builder()
                .postMetricDataDetails(postMetricDataDetails)
                .build();

        LOGGER.log(System.Logger.Level.TRACE, String.format("Pushing %d metrics to OCI", metricDataDetailsList.size()));
        if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
            metricDataDetailsList
                    .forEach(m -> {
                        LOGGER.log(System.Logger.Level.TRACE, String.format(
                                "Metric details: name=%s, namespace=%s, dimensions=%s, "
                                        + "datapoints.timestamp=%s, datapoints.value=%f, metadata=%s",
                                m.getName(),
                                m.getNamespace(),
                                m.getDimensions(),
                                m.getDatapoints().get(0).getTimestamp(),
                                m.getDatapoints().get(0).getValue(),
                                m.getMetadata()));
                    });
        }
        String originalMonitoringEndpoint = this.monitoringClient.getEndpoint();
        try {
            // Use the ingestion endpoint for posting
            this.monitoringClient.setEndpoint(
                    monitoringClient.getEndpoint().replaceFirst("telemetry\\.", "telemetry-ingestion."));
            this.monitoringClient.postMetricData(postMetricDataRequest);
            LOGGER.log(System.Logger.Level.TRACE,
                    String.format("Successfully posted %d metrics to OCI", metricDataDetailsList.size()));
        } catch (Throwable e) {
            LOGGER.log(System.Logger.Level.WARNING, String.format("Unable to send metrics to OCI: %s", e.getMessage()));
        } finally {
            // restore original endpoint
            this.monitoringClient.setEndpoint(originalMonitoringEndpoint);
        }
    }

    @Override
    public void routing(HttpRules rules) {
        // noop
    }

    @Override
    public void beforeStart() {
        if (!enabled) {
            LOGGER.log(System.Logger.Level.INFO, "Metric push to OCI is disabled!");
            return;
        }

        if (scopes.isEmpty()) {
            LOGGER.log(System.Logger.Level.INFO, "No selected metric scopes to push to OCI");
            return;
        }

        LOGGER.log(System.Logger.Level.TRACE, "Starting OCI Metrics agent");

        ociMetricsData = new OciMetricsData(
                scopes, nameFormatter, compartmentId, namespace, resourceGroup, descriptionEnabled);
        startExecutor();
    }

    @Override
    public void afterStop() {
        // Shutdown executor if created
        if (scheduledExecutorService != null) {
            LOGGER.log(System.Logger.Level.TRACE, "Shutting down OCI Metrics agent");
            scheduledExecutorService.shutdownNow();
        }
    }

    /**
     * Fluent API builder to create {@link OciMetricsSupport}.
     */
    @Configured
    public static class Builder implements io.helidon.common.Builder<Builder, OciMetricsSupport> {

        private static final long DEFAULT_SCHEDULER_INITIAL_DELAY = 1L;
        private static final long DEFAULT_SCHEDULER_DELAY = 60L;
        private static final long DEFAULT_BATCH_DELAY = 1L;
        private static final TimeUnit DEFAULT_SCHEDULER_TIME_UNIT = TimeUnit.SECONDS;
        private static final int DEFAULT_BATCH_SIZE = 50;

        private long initialDelay = DEFAULT_SCHEDULER_INITIAL_DELAY;
        private long delay = DEFAULT_SCHEDULER_DELAY;
        private long batchDelay = DEFAULT_BATCH_DELAY;
        private TimeUnit schedulingTimeUnit = DEFAULT_SCHEDULER_TIME_UNIT;
        private String compartmentId;
        private String namespace;
        private NameFormatter nameFormatter = DEFAULT_NAME_FORMATTER;
        private String resourceGroup;
        private Set<String> scopes = Meter.Scope.BUILT_IN_SCOPES;
        private boolean descriptionEnabled = true;
        private int batchSize = DEFAULT_BATCH_SIZE;
        private boolean enabled = true;
        private Monitoring monitoringClient;

        private Builder() {
        }

        @Override
        public OciMetricsSupport build() {
            if (monitoringClient == null) {
                throw new IllegalArgumentException("Monitoring client must be set in builder before building it");
            }
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

            compartmentId = value;
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

            namespace = value;
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

            this.nameFormatter = nameFormatter;
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

            resourceGroup = value;
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
            descriptionEnabled = value;
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
                this.scopes = Meter.Scope.BUILT_IN_SCOPES;
            } else {
                Set<String> convertedScope = new HashSet<>();
                for (String element: value) {
                    String scopeItem = element.toLowerCase(Locale.ROOT).trim();
                    convertedScope.add(scopeItem);
                }
                this.scopes = convertedScope;
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
            enabled = value;
            return this;
        }

        /**
         * Sets the maximum no. of metrics to send in a batch
         * (defaults to {@value #DEFAULT_BATCH_SIZE}).
         *
         * @param value maximum no. of metrics to send in a batch
         * @return updated builder
         */
        @ConfiguredOption(value = "50")
        public Builder batchSize(int value) {
            batchSize = value;
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
            this.monitoringClient = monitoringClient;
            return this;
        }

        /**
         * Returns boolean value to indicate whether OciMetricsSupport service will be activated or not.
         *
         * @return {@code true} if OciMetricsSupport service will be activated or
         *         {@code false} if it not
         */
        public boolean enabled() {
            return this.enabled;
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
