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
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import io.helidon.Main;
import io.helidon.builder.api.RuntimeType;
import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MetricsPublisher;
import io.helidon.service.registry.Services;
import io.helidon.spi.HelidonShutdownHandler;

import com.oracle.bmc.monitoring.Monitoring;
import com.oracle.bmc.monitoring.model.MetricDataDetails;
import com.oracle.bmc.monitoring.model.PostMetricDataDetails;
import com.oracle.bmc.monitoring.requests.PostMetricDataRequest;

/**
 * Periodically transmits Helidon metrics to an OCI Monitoring backend.
 * <p>
 * This service is configured as a metrics publisher of type
 * {@value TYPE} and uses either the instance of {@link com.oracle.bmc.monitoring.Monitoring} that was explicitly configured on
 * {@link io.helidon.integrations.oci.metrics.OciMetricsConfig} or, if not set in the config, obtained from the service registry.
 * The {@code Monitoring} instance determines how to connect to the backend; this service manages the periodic use of that
 * instance to send the metrics data to the backend.
 */
class OciMetricsService implements MetricsPublisher,
                                   HelidonShutdownHandler,
                                   AutoCloseable,
                                   RuntimeType.Api<OciMetricsConfig> {

    static final String TYPE = "oci";

    private static final System.Logger LOGGER = System.getLogger(OciMetricsService.class.getName());
    private static final UnitConverter STORAGE_UNIT_CONVERTER = UnitConverter.storageUnitConverter();
    private static final UnitConverter TIME_UNIT_CONVERTER = UnitConverter.timeUnitConverter();
    private static final List<UnitConverter> UNIT_CONVERTERS = List.of(STORAGE_UNIT_CONVERTER, TIME_UNIT_CONVERTER);

    private final OciMetricsConfig prototype;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition idle = lock.newCondition();

    // NEW can start once; STOPPING admits no work and permits close retries; CLOSED owns no executor or hook.
    private volatile State state = State.NEW;
    private ScheduledExecutorService scheduledExecutorService;
    private ScheduledFuture<?> scheduledTask;
    private OciMetricsData ociMetricsData;
    private Monitoring monitoringClient;
    private Thread activeThread;
    private boolean shutdownHandlerRequested;

    private OciMetricsService(OciMetricsConfig prototype) {
        this.prototype = prototype;
    }

    static OciMetricsConfig.Builder builder() {
        return OciMetricsConfig.builder();
    }

    static OciMetricsService create(Consumer<OciMetricsConfig.Builder> consumer) {
        return builder().update(consumer).build();
    }

    static OciMetricsService create(OciMetricsConfig ociMetricsConfig) {
        return createStopped(ociMetricsConfig);
    }

    static OciMetricsService createStopped(OciMetricsConfig ociMetricsConfig) {
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
    public void startPublishing() {
        startup();
    }

    @Override
    public void close() {
        shutdown();
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
        ScheduledExecutorService executor;
        lock.lock();
        try {
            if (state == State.CLOSED) {
                return;
            }
            state = State.STOPPING;
            if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                LOGGER.log(System.Logger.Level.TRACE, "Shutting down OCI Metrics service {0}", name());
            }
            if (scheduledTask != null) {
                scheduledTask.cancel(false);
            }
            executor = scheduledExecutorService;
            if (executor != null) {
                executor.shutdownNow();
            }
            if (activeThread == Thread.currentThread()) {
                throw new IllegalStateException("OCI metrics publisher cannot wait for its own transmission to finish");
            }
            long remaining = TimeUnit.SECONDS.toNanos(10);
            while (activeThread != null) {
                if (remaining <= 0) {
                    throw new IllegalStateException("OCI metrics publisher shutdown timed out waiting for a transmission");
                }
                try {
                    remaining = idle.awaitNanos(remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("OCI metrics publisher shutdown was interrupted", e);
                }
            }
        } finally {
            lock.unlock();
        }

        // A cancelled future is not proof that the task has exited. Do not mark CLOSED until the executor terminates.
        if (executor != null) {
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("OCI metrics publisher shutdown timed out waiting for the executor");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("OCI metrics publisher shutdown was interrupted", e);
            }
        }
        // Main can call shutdown while holding its own handler lock. Never call Main with our lock held.
        Main.removeShutdownHandler(this);
        lock.lock();
        try {
            state = State.CLOSED;
        } finally {
            lock.unlock();
        }
    }

    void startup() {
        lock.lock();
        try {
            if (state == State.RUNNING) {
                return;
            }
            if (state != State.NEW) {
                throw new IllegalStateException("A stopping or closed OCI metrics publisher cannot be started");
            }
        } finally {
            lock.unlock();
        }
        try {
            registerShutdownHandler();
            lock.lock();
            try {
                if (state == State.RUNNING) {
                    return;
                }
                if (state != State.NEW) {
                    throw new IllegalStateException("A stopping or closed OCI metrics publisher cannot be started");
                }
                if (!isEligibleForTransmission()) {
                    state = State.RUNNING;
                    return;
                }
                monitoringClient = prototype.monitoringClient().orElseGet(() -> Services.get(Monitoring.class));
                ociMetricsData = new OciMetricsData(prototype.scopes(),
                                                   prototype.nameFormatter(),
                                                   prototype.compartmentId().orElse(null),
                                                   prototype.namespace().orElse(null),
                                                   prototype.resourceGroup().orElse(null),
                                                   prototype.descriptionEnabled());
                scheduledExecutorService = Executors.newScheduledThreadPool(1, Thread.ofVirtual().factory());
                Context ctx = Contexts.context().orElseGet(Contexts::globalContext);
                state = State.RUNNING;
                scheduledTask = scheduledExecutorService.scheduleAtFixedRate(
                        () -> runTransmission(ctx),
                        prototype.initialDelay().toNanos(),
                        prototype.delay().toNanos(),
                        TimeUnit.NANOSECONDS);
                if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                    LOGGER.log(System.Logger.Level.TRACE, "Started OCI Metrics publisher {0}", name());
                }
            } finally {
                lock.unlock();
            }
        } catch (RuntimeException | Error failure) {
            try {
                shutdown();
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    private void registerShutdownHandler() {
        lock.lock();
        try {
            if (shutdownHandlerRequested) {
                return;
            }
            shutdownHandlerRequested = true;
        } finally {
            lock.unlock();
        }
        Main.addShutdownHandler(this);
        boolean remove;
        lock.lock();
        try {
            remove = state == State.STOPPING || state == State.CLOSED;
        } finally {
            lock.unlock();
        }
        if (remove) {
            Main.removeShutdownHandler(this);
        }
    }

    private void runTransmission(Context context) {
        lock.lock();
        try {
            if (state != State.RUNNING) {
                return;
            }
            activeThread = Thread.currentThread();
        } finally {
            lock.unlock();
        }
        try {
            Contexts.runInContext(context, this::pushMetrics);
        } catch (RuntimeException | Error failure) {
            lock.lock();
            try {
                state = State.STOPPING;
                scheduledExecutorService.shutdown();
            } finally {
                lock.unlock();
            }
            LOGGER.log(System.Logger.Level.ERROR, "OCI metrics publisher stopped after a transmission processing failure");
            if (failure instanceof Error error) {
                throw error;
            }
        } finally {
            lock.lock();
            try {
                activeThread = null;
                idle.signalAll();
            } finally {
                lock.unlock();
            }
        }
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

    private void pushMetrics() {
        List<MetricDataDetails> allMetricDataDetails = ociMetricsData.getMetricDataDetails();
        if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
            LOGGER.log(System.Logger.Level.TRACE, "Processing {0} metrics", allMetricDataDetails.size());
        }
        if (!allMetricDataDetails.isEmpty()) {
            while (state == State.RUNNING) {
                if (allMetricDataDetails.size() > prototype.batchSize()) {
                    postBatch(allMetricDataDetails.subList(0, prototype.batchSize()));
                    // discard metrics that had been posted
                    allMetricDataDetails.subList(0, prototype.batchSize()).clear();
                    if (prototype.batchDelay().isPositive()) {
                        try {
                            TimeUnit.NANOSECONDS.sleep(prototype.batchDelay().toNanos());
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
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

        if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
            LOGGER.log(System.Logger.Level.TRACE, "Pushing {0} metrics to OCI", metricDataDetailsList.size());
            metricDataDetailsList
                    .forEach(m -> {
                        LOGGER.log(System.Logger.Level.TRACE,
                                "Metric details: name={0}, namespace={1}, dimensions={2}, "
                                        + "datapoints.timestamp={3}, datapoints.value={4}, metadata={5}",
                                m.getName(),
                                m.getNamespace(),
                                m.getDimensions(),
                                m.getDatapoints().getFirst().getTimestamp(),
                                m.getDatapoints().getFirst().getValue(),
                                m.getMetadata());
                    });
        }
        String originalMonitoringEndpoint = monitoringClient.getEndpoint();
        try {
            // Use the ingestion endpoint for posting
            monitoringClient.setEndpoint(
                    monitoringClient.getEndpoint().replaceFirst("telemetry\\.", "telemetry-ingestion."));
            monitoringClient.postMetricData(postMetricDataRequest);
            if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                LOGGER.log(System.Logger.Level.TRACE, "Successfully posted {0} metrics to OCI", metricDataDetailsList.size());
            }
        } catch (RuntimeException e) {
            LOGGER.log(System.Logger.Level.WARNING, "Unable to send metrics to OCI");
        } finally {
            // restore original endpoint
            monitoringClient.setEndpoint(originalMonitoringEndpoint);
        }
    }

    private enum State {
        NEW, RUNNING, STOPPING, CLOSED
    }
}
