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

package io.helidon.integrations.oci.metrics;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.helidon.Main;
import io.helidon.builder.api.RuntimeType;
import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.common.resumable.Resumable;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MetricsPublisher;
import io.helidon.service.registry.Services;
import io.helidon.spi.HelidonShutdownHandler;

import com.oracle.bmc.monitoring.Monitoring;
import com.oracle.bmc.monitoring.model.MetricDataDetails;
import com.oracle.bmc.monitoring.model.PostMetricDataDetails;
import com.oracle.bmc.monitoring.requests.PostMetricDataRequest;

/**
 * When configured as a metrics publisher of type {@value TYPE}, periodically transmits Helidon metrics to the configured OCI
 * metrics backend. The class relies on the service registry to provide a correctly-configured instance
 * of {@link com.oracle.bmc.monitoring.Monitoring}.
 */
class OciMetricsService implements MetricsPublisher, Resumable, HelidonShutdownHandler, AutoCloseable,
                                   RuntimeType.Api<OciMetricsConfig> {

    static final String TYPE = "oci";
    private static final System.Logger LOGGER = System.getLogger(OciMetricsService.class.getName());
    private static final UnitConverter STORAGE_UNIT_CONVERTER = UnitConverter.storageUnitConverter();
    private static final UnitConverter TIME_UNIT_CONVERTER = UnitConverter.timeUnitConverter();
    private static final List<UnitConverter> UNIT_CONVERTERS = List.of(STORAGE_UNIT_CONVERTER, TIME_UNIT_CONVERTER);
    private final OciMetricsConfig prototype;
    private ScheduledExecutorService scheduledExecutorService;
    private Monitoring monitoringClient;
    private OciMetricsData ociMetricsData;

    private OciMetricsService(OciMetricsConfig prototype) {
        this.prototype = prototype;
        if (isEligibleForTransmission()) {
            monitoringClient = prototype.monitoringClient().orElseGet(() -> Services.get(Monitoring.class));
            prepareTransmission();
        }
    }

    static OciMetricsConfig.Builder builder() {
        return OciMetricsConfig.builder();
    }

    static OciMetricsService create(java.util.function.Consumer<OciMetricsConfig.Builder> consumer) {
        return builder().update(consumer).build();
    }

    static OciMetricsService create(OciMetricsConfig ociMetricsConfig) {
        return new OciMetricsService(ociMetricsConfig);
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

    @Override
    public OciMetricsConfig prototype() {
        return this.prototype;
    }

    @Override
    public void close() {
        shutdown();
    }

    @Override
    public void suspend() {
        shutdown();
    }

    @Override
    public void resume() {
        startup();
    }

    @Override
    public boolean enabled() {
        return prototype().enabled();
    }

    @Override
    public String name() {
        return prototype.name().orElse(TYPE);
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public void shutdown() {
        // Shutdown executor if created
        if (scheduledExecutorService != null) {
            LOGGER.log(System.Logger.Level.TRACE, "Shutting down OCI Metrics service {0}", name());
            scheduledExecutorService.shutdownNow();
        }
    }

    void startup() {
        if (isEligibleForTransmission()) {
            prepareTransmission();
        }
    }

    private void prepareTransmission() {
        LOGGER.log(System.Logger.Level.TRACE, "Starting OCI Metrics publisher {0}", name());
        Main.addShutdownHandler(this);

        ociMetricsData = new OciMetricsData(
                prototype.scopes(),
                prototype.nameFormatter(),
                prototype.compartmentId().orElse(null),
                prototype.namespace().orElse(null),
                prototype.resourceGroup().orElse(null),
                prototype.descriptionEnabled());
        startExecutor();
    }

    private boolean isEligibleForTransmission() {
        if (!prototype.enabled()) {
            LOGGER.log(System.Logger.Level.INFO, "OCI metrics publisher {0} is disabled", name());
            return false;
        }

        if (prototype.scopes().isEmpty()) {
            LOGGER.log(System.Logger.Level.INFO,
                       "OCI metrics publisher {0} is configured with no scopes so will not transmit any metrics",
                       name());
            return false;
        }

        return true;

    }

    private void startExecutor() {
        Context ctx = Contexts.context().orElseGet(Contexts::globalContext);
        scheduledExecutorService = Executors.newScheduledThreadPool(1, Thread.ofVirtual().factory());
        scheduledExecutorService.scheduleAtFixedRate(() -> Contexts.runInContext(ctx, this::pushMetrics),
                                                     prototype.initialDelay().toNanos(),
                                                     prototype.delay().toNanos(),
                                                     TimeUnit.NANOSECONDS);
    }

    private void pushMetrics() {
        List<MetricDataDetails> allMetricDataDetails = ociMetricsData.getMetricDataDetails();
        LOGGER.log(System.Logger.Level.TRACE, String.format("Processing %d metrics", allMetricDataDetails.size()));
        if (!allMetricDataDetails.isEmpty()) {
            while (true) {
                if (allMetricDataDetails.size() > prototype.batchSize()) {
                    postBatch(allMetricDataDetails.subList(0, prototype.batchSize()));
                    // discard metrics that had been posted
                    allMetricDataDetails.subList(0, prototype.batchSize()).clear();
                    if (prototype.batchDelay().isPositive()) {
                        try {
                            TimeUnit.NANOSECONDS.sleep(prototype.batchDelay().toNanos());
                        } catch (InterruptedException ignore) {
                            Thread.currentThread().interrupt();
                        }
                    }
                } else {
                    postBatch(allMetricDataDetails);
                    break;
                }
            }
        }
    }

    // Here, "post" means "send" from OCI Monitoring terminology as opposed to "after."
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
                                m.getDatapoints().getFirst().getTimestamp(),
                                m.getDatapoints().getFirst().getValue(),
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
}
