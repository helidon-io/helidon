/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import io.helidon.config.Config;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.metrics.api.RegistryFactory;
import io.helidon.webserver.Routing;
import io.helidon.webserver.Service;
import io.helidon.webserver.WebServer;

import com.oracle.bmc.monitoring.Monitoring;
import com.oracle.bmc.monitoring.model.MetricDataDetails;
import com.oracle.bmc.monitoring.model.PostMetricDataDetails;
import com.oracle.bmc.monitoring.requests.PostMetricDataRequest;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricRegistry.Type;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;

/**
 * OCI Metrics Support
 * <p>
 * Even though this service does not create an endpoint, a calling SE app should still register it by invoking
 * {@link Routing.Rules#register(io.helidon.webserver.Service...)}, as it should with any service it uses. That allows
 * this service to detect when the webserver shuts down so it can shut down as well.
 * </p>
 */
public class OciMetricsSupport implements Service {
    private static final Logger LOGGER = Logger.getLogger(OciMetricsSupport.class.getName());

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
    private final Type[] scopes;
    private final int batchSize;
    private final boolean enabled;
    private final AtomicInteger webServerCounter = new AtomicInteger(0);

    private final Monitoring monitoringClient;

    private final Map<MetricRegistry, Type> metricRegistries = new HashMap<>();
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
         * @param metricId {@code MetricID} of the metric being formatted
         * @param suffix name suffix to append to the recorded metric name (e.g, "total"); can be null
         * @param metadata metric metadata describing the metric
         * @return the formatted metric name
         */
        default String format(MetricID metricId, String suffix, Metadata metadata) {

            MetricType metricType = metadata.getTypeRaw();

            StringBuilder result = new StringBuilder(metricId.getName());
            if (suffix != null) {
                result.append("_").append(suffix);
            }
            result.append("_").append(metricType.toString().replace(" ", "_"));

            String units = formattedBaseUnits(metadata.getUnit());
            if (units != null && !units.isBlank()) {
                result.append("_").append(units);
            }
            return result.toString();
        }
    }

    static String formattedBaseUnits(String metricUnits) {
        String baseUnits = baseMetricUnits(metricUnits);
        return baseUnits == null ? "" : baseUnits;
    }

    static String baseMetricUnits(String metricUnits) {
        if (!MetricUnits.NONE.equals(metricUnits) && !metricUnits.isEmpty()) {
            for (UnitConverter converter : UNIT_CONVERTERS) {
                if (converter.handles(metricUnits)) {
                    return converter.baseUnits();
                }
            }
        }
        return null;
    }

    private void shutdown(WebServer webServer) {
        if (webServerCounter.decrementAndGet() < 1) {
            LOGGER.fine("Shutting down OCI Metrics agent");
            scheduledExecutorService.shutdownNow();
        }
    }

    private void startExecutor() {
        scheduledExecutorService = Executors.newScheduledThreadPool(1);
        scheduledExecutorService.scheduleAtFixedRate(this::pushMetrics, initialDelay, delay, schedulingTimeUnit);
    }

    private void pushMetrics() {
        List<MetricDataDetails> allMetricDataDetails = ociMetricsData.getMetricDataDetails();
        LOGGER.finest(String.format("Processing %d metrics", allMetricDataDetails.size()));

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

        LOGGER.finest(String.format("Pushing %d metrics to OCI", metricDataDetailsList.size()));
        if (LOGGER.isLoggable(Level.FINEST)) {
            metricDataDetailsList
                    .forEach(m -> {
                        LOGGER.finest(String.format(
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
            LOGGER.finest(String.format("Successfully posted %d metrics to OCI", metricDataDetailsList.size()));
        } catch (Throwable e) {
            LOGGER.warning(String.format("Unable to send metrics to OCI: %s", e.getMessage()));
        } finally {
            // restore original endpoint
            this.monitoringClient.setEndpoint(originalMonitoringEndpoint);
        }
    }

    @Override
    public void update(Routing.Rules rules) {
        if (!enabled) {
            LOGGER.info("Metric push to OCI is disabled!");
            return;
        }

        if (scopes.length == 0) {
            LOGGER.info("No selected metric scopes to push to OCI");
            return;
        }

        rules.onNewWebServer(this::prepareShutdown);

        LOGGER.fine("Starting OCI Metrics agent");

        RegistryFactory rf = RegistryFactory.getInstance();
        Stream.of(scopes)
                .forEach(type -> metricRegistries.put(rf.getRegistry(type), type));

        ociMetricsData = new OciMetricsData(
                metricRegistries, nameFormatter, compartmentId, namespace, resourceGroup, descriptionEnabled);
        startExecutor();
    }

    private void prepareShutdown(WebServer webServer) {
        webServerCounter.incrementAndGet();
        webServer.whenShutdown().thenAccept(this::shutdown);
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

        private static final Map<String, Type> SCOPE_TYPES = Map.of(
                Type.BASE.getName(), Type.BASE,
                Type.VENDOR.getName(), Type.VENDOR,
                Type.APPLICATION.getName(), Type.APPLICATION
        );

        private long initialDelay = DEFAULT_SCHEDULER_INITIAL_DELAY;
        private long delay = DEFAULT_SCHEDULER_DELAY;
        private long batchDelay = DEFAULT_BATCH_DELAY;
        private TimeUnit schedulingTimeUnit = DEFAULT_SCHEDULER_TIME_UNIT;
        private String compartmentId;
        private String namespace;
        private NameFormatter nameFormatter = DEFAULT_NAME_FORMATTER;
        private String resourceGroup;
        private Type[] scopes = getAllMetricScopes();
        private boolean descriptionEnabled = true;
        private int batchSize = DEFAULT_BATCH_SIZE;
        private boolean enabled = true;
        private Monitoring monitoringClient;

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
         * MetricID, String, Metadata)} method for details
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
                this.scopes = getAllMetricScopes();
            } else {
                List<Type> convertedScope = new ArrayList<>();
                for (String element: value) {
                    Type scopeItem = SCOPE_TYPES.get(element.toLowerCase(Locale.ROOT).trim());
                    if (scopeItem != null) {
                        convertedScope.add(scopeItem);
                    }
                }
                this.scopes = convertedScope.toArray(new Type[convertedScope.size()]);
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

        private static Type[] getAllMetricScopes() {
            return new ArrayList<>(SCOPE_TYPES.values()).toArray(new Type[SCOPE_TYPES.size()]);
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
