/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.integrations.oci.metrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Stream;

import io.helidon.config.Config;
import io.helidon.metrics.api.RegistryFactory;
import io.helidon.webserver.Routing;
import io.helidon.webserver.Service;
import io.helidon.webserver.WebServer;

import com.oracle.bmc.monitoring.Monitoring;
import com.oracle.bmc.monitoring.model.Datapoint;
import com.oracle.bmc.monitoring.model.MetricDataDetails;

import com.oracle.bmc.monitoring.model.PostMetricDataDetails;
import com.oracle.bmc.monitoring.requests.PostMetricDataRequest;
import org.eclipse.microprofile.metrics.ConcurrentGauge;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Meter;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricRegistry.Type;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.SimpleTimer;
import org.eclipse.microprofile.metrics.Snapshot;
import org.eclipse.microprofile.metrics.Timer;

/**
 * POC OCI Metrics Support.
 *
 * A consuming SE app would use builder to create OciMetricsSupport. This is written to use a separate "start" to start the
 * scheduled executor (instead of starting that from the constructor) in case the caller wants that level of control. Maybe
 * that's not needed. Because this does not create an endpoint the calling SE app does not need to create routing with this
 * service. But the caller does need to invoke "update" and pass the routing rules which the service uses to shut down the
 * periodic scheduler which sends metrics to OCI at the time that the web server is shutting down.
 */
public class OciMetricsSupport implements Service {
    /** Local logger instance. */
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
    private final String resourceGroup;
    private final boolean descriptionEnabled;
    private final Type[] scopes;
    private final boolean enabled;
    private final AtomicInteger webServerCounter = new AtomicInteger(0);

    private final Monitoring monitoringClient;

    private final Map<MetricRegistry, MetricRegistry.Type> metricRegistries = new HashMap<>();

    private OciMetricsSupport(Builder builder) {
        initialDelay = builder.initialDelay;
        delay = builder.delay;
        compartmentId = builder.compartmentId;
        namespace = builder.namespace;
        nameFormatter = builder.nameFormatter;
        resourceGroup = builder.resourceGroup;
        descriptionEnabled = builder.descriptionEnabled;
        scopes = builder.scopes;
        enabled = builder.enabled;
        this.monitoringClient = builder.monitoringClient;
    }

    public static OciMetricsSupport.Builder builder() {
        return new Builder();
    }

    public interface NameFormatter {
        default String format(MetricID metricId, String suffix, Metadata metadata) {

            MetricType metricType = metadata.getTypeRaw();

            StringBuilder result = new StringBuilder(metricId.getName());
            if (suffix != null) {
                result.append("_").append(suffix);
            }
            result.append("_").append(metricType);

            String units = formattedBaseUnits(metadata.getUnit().orElse(null));
            if (units != null && !units.isBlank()) {
                result.append("_").append(units);
            }
            return result.toString();
        }
    }

    private void shutdown(WebServer webServer) {
        if (webServerCounter.decrementAndGet() < 1) {
            LOGGER.fine("Shutting down OCI Metrics agent");
            scheduledExecutorService.shutdownNow();
        }
    }

    private void startExecutor() {
        scheduledExecutorService = Executors.newScheduledThreadPool(1);
        scheduledExecutorService.scheduleAtFixedRate(this::pushMetrics, initialDelay, delay, TimeUnit.SECONDS);
    }

    @Override
    public void update(Routing.Rules rules) {
        rules.onNewWebServer(this::prepareShutdown);

        if (!enabled) {
            LOGGER.info("Metric push to OCI is disabled!");
            return;
        }

        if (scopes.length == 0) {
            LOGGER.info("No selected metric scopes to push to OCI");
            return;
        }

        LOGGER.fine("Starting OCI Metrics agent");

        RegistryFactory rf = RegistryFactory.getInstance();
        Stream.of(scopes)
                .forEach(type -> metricRegistries.put(rf.getRegistry(type), type));

        startExecutor();
    }

    private void prepareShutdown(WebServer webServer) {
        webServerCounter.incrementAndGet();
        webServer.whenShutdown().thenAccept(this::shutdown);
    }

    /**
     * Builder for OciMetricsSupport.
     */
    public static class Builder implements io.helidon.common.Builder<OciMetricsSupport> {

        private long initialDelay = 1L;
        private long delay = 60L;
        private String compartmentId;
        private String namespace;
        private NameFormatter nameFormatter = DEFAULT_NAME_FORMATTER;
        private String resourceGroup;
        private Type[] scopes;
        private boolean descriptionEnabled = true;
        private boolean enabled = true;
        private Monitoring monitoringClient;

        @Override
        public OciMetricsSupport build() {
            if (monitoringClient == null) {
                throw new IllegalArgumentException("Monitoring client must be set in builder before building it");
            }
            return new OciMetricsSupport(this);
        }

        public Builder initialDelay(long value) {
            initialDelay = value;
            return this;
        }

        public Builder delay(long value) {
            delay = value;
            return this;
        }

        public Builder compartmentId(String value) {
            compartmentId = value;
            return this;
        }

        public Builder namespace(String value) {
            namespace = value;
            return this;
        }

        public Builder nameFormatter(NameFormatter nameFormatter) {
            this.nameFormatter = nameFormatter;
            return this;
        }

        public Builder resourceGroup(String value) {
            resourceGroup = value;
            return this;
        }

        public Builder descriptionEnabled(boolean value) {
            descriptionEnabled = value;
            return this;
        }

        public Builder scopes(List<String> value) {
            Map<String, Type> scopeTypes = Map.of(
                    "base", Type.BASE,
                    "vendor", Type.VENDOR,
                    "application", Type.APPLICATION
            );
            if (value == null || value.size() == 0) {
                this.scopes =  new ArrayList<Type>(scopeTypes.values()).toArray(new Type[scopeTypes.size()]);
            } else {
                List<Type> convertedScope = new ArrayList<>();
                for(String element: value) {
                    Type scopeItem = scopeTypes.get(element);
                    if (scopeItem != null) {
                        convertedScope.add(scopeItem);
                    }
                }
                this.scopes = convertedScope.toArray(new Type[convertedScope.size()]);
            }
            return this;
        }

        public Builder enabled(boolean value) {
            enabled = value;
            return this;
        }

        public Builder config(Config config) {
            config.get("initialDelay").asLong().ifPresent(this::initialDelay);
            config.get("delay").asLong().ifPresent(this::delay);
            config.get("compartmentId").asString().ifPresent(this::compartmentId);
            config.get("namespace").asString().ifPresent(this::namespace);
            config.get("resourceGroup").asString().ifPresent(this::resourceGroup);
            config.get("scopes").asList(String.class).ifPresentOrElse(this::scopes, () -> scopes(null));
            config.get("enabled").asBoolean().ifPresent(this::enabled);
            return this;
        }

        public Builder monitoringClient(Monitoring monitoringClient) {
            this.monitoringClient = monitoringClient;
            return this;
        }
    }

    private void pushMetrics() {

        List<MetricDataDetails> allMetricDataDetails = new ArrayList<>();

        for (MetricRegistry metricRegistry : metricRegistries.keySet()) {
            metricRegistry.getMetrics().entrySet().stream()
                    .flatMap(entry -> metricDataDetails(metricRegistry, entry.getKey(), entry.getValue()))
                    .forEach(allMetricDataDetails::add);
        }

        PostMetricDataDetails postMetricDataDetails = PostMetricDataDetails.builder()
                .metricData(allMetricDataDetails)
                .build();

        PostMetricDataRequest postMetricDataRequest = PostMetricDataRequest.builder()
                .postMetricDataDetails(postMetricDataDetails)
                .build();
        if (allMetricDataDetails.size() > 0) {
            LOGGER.finest(String.format("Pushing %d metrics to OCI", allMetricDataDetails.size()));
            try {
                this.monitoringClient.postMetricData(postMetricDataRequest);
            } catch (Throwable e) {
                LOGGER.warning(String.format("Unable to send metrics to OCI: %s", e.getMessage()));
            }
        }
    }

    private Stream<MetricDataDetails> metricDataDetails(MetricRegistry metricRegistry, MetricID metricId, Metric metric) {
        if (metric instanceof Counter) {
            return forCounter(metricRegistry, metricId, ((Counter) metric));
        } else if (metric instanceof ConcurrentGauge) {
            return forConcurrentGauge(metricRegistry, metricId, ((ConcurrentGauge) metric));
        } else if (metric instanceof Meter) {
            return forMeter(metricRegistry, metricId, ((Meter) metric));
        } else if (metric instanceof Gauge<?>) {
            return forGauge(metricRegistry, metricId, ((Gauge<? extends Number>) metric));
        } else if (metric instanceof SimpleTimer) {
            return forSimpleTimer(metricRegistry, metricId, ((SimpleTimer) metric));
        } else if (metric instanceof Timer) {
            return forTimer(metricRegistry, metricId, ((Timer) metric));
        } else if (metric instanceof Histogram) {
            return forHistogram(metricRegistry, metricId, ((Histogram) metric));
        } else {
            return Stream.empty();
        }
    }

    private String toMetricDetailsName(MetricID metricID) {
        return metricID.getName()
                + (metricID.getTagsAsList().isEmpty() ? "" : "[" + metricID.getTagsAsString() + "]");
    }

    private Stream<MetricDataDetails> forCounter(MetricRegistry metricRegistry, MetricID metricId, Counter counter) {
        return Stream.of(metricDataDetails(metricRegistry, metricId, null, counter.getCount()));
    }

    private Stream<MetricDataDetails> forConcurrentGauge(MetricRegistry metricRegistry, MetricID metricId, ConcurrentGauge concurrentGauge) {
        Stream.Builder<MetricDataDetails> result = Stream.builder();
        long count = concurrentGauge.getCount();
        result.add(metricDataDetails(metricRegistry, metricId, null, count));
        if (count > 0) {
            result.add(metricDataDetails(metricRegistry, metricId, "min", concurrentGauge.getMin()));
            result.add(metricDataDetails(metricRegistry, metricId, "max", concurrentGauge.getMax()));
        }
        return result.build();
    }

    private Stream<MetricDataDetails> forMeter(MetricRegistry metricRegistry, MetricID metricId, Meter meter) {
        Stream.Builder<MetricDataDetails> result = Stream.builder();
        long count = meter.getCount();
        result.add(metricDataDetails(metricRegistry, metricId, "total", count));
        if (count > 0) {
            result.add(metricDataDetails(metricRegistry, metricId, "gauge", meter.getMeanRate()));
        }
        return result.build();
    }

    private Stream<MetricDataDetails> forGauge(MetricRegistry metricRegistry, MetricID metricId, Gauge<? extends Number> gauge) {
        return Stream.of(metricDataDetails(metricRegistry, metricId, null, gauge.getValue().doubleValue()));
    }

    private Stream<MetricDataDetails> forSimpleTimer(MetricRegistry metricRegistry, MetricID metricId, SimpleTimer simpleTimer) {
        Stream.Builder<MetricDataDetails> result = Stream.builder();
        long count = simpleTimer.getCount();
        result.add(metricDataDetails(metricRegistry, metricId, "total", count));
        if (count > 0) {
            result.add(metricDataDetails(metricRegistry,
                    metricId,
                    "elapsedTime_seconds",
                    simpleTimer.getElapsedTime().toSeconds()));
        }
        return result.build();
    }

    private Stream<MetricDataDetails> forTimer(MetricRegistry metricRegistry, MetricID metricId, Timer timer) {
        Stream.Builder<MetricDataDetails> result = Stream.builder();
        long count = timer.getCount();
        result.add(metricDataDetails(metricRegistry, metricId, "seconds_count", count));
        if (count > 0) {
            Snapshot snapshot = timer.getSnapshot();
            result.add(metricDataDetails(metricRegistry,
                    metricId,
                    "mean_seconds",
                    snapshot.getMean()));
            result.add(metricDataDetails(metricRegistry,
                    metricId,
                    "min_seconds",
                    snapshot.getMin()));
            metricDataDetails(metricRegistry,
                    metricId,
                    "max_seconds",
                    snapshot.getMax());

        }
        return result.build();
    }

    private Stream<MetricDataDetails> forHistogram(MetricRegistry metricRegistry, MetricID metricId, Histogram histogram) {
        Stream.Builder<MetricDataDetails> result = Stream.builder();
        long count = histogram.getCount();
        Metadata metadata = metricRegistry.getMetadata().get(metricId.getName());
        Optional<String> units = metadata.getUnit();
        String unitsPrefix = units.map(u -> u + "_").orElse("");
        String unitsSuffix = units.map(u -> "_" + u).orElse("");
        result.add(metricDataDetails(metricRegistry, metricId, unitsPrefix + "count", count));
        if (count > 0) {
            Snapshot snapshot = histogram.getSnapshot();
            result.add(metricDataDetails(metricRegistry,
                    metricId,
                    "mean" + unitsSuffix,
                    snapshot.getMean()));
            result.add(metricDataDetails(metricRegistry,
                    metricId,
                    "min" + unitsSuffix,
                    snapshot.getMin()));
            metricDataDetails(metricRegistry,
                    metricId,
                    "max" + unitsSuffix,
                    snapshot.getMax());

        }
        return result.build();
    }

    private MetricDataDetails metricDataDetails(MetricRegistry metricRegistry, MetricID metricId, String suffix, double value) {
        if (Double.isNaN(value)) {
            return null;
        }

        Metadata metadata = metricRegistry.getMetadata().get(metricId.getName());
        Map<String, String> dimensions = dimensions(metricId, metricRegistry);
        List<Datapoint> datapoints = datapoints(metadata, value);
        LOGGER.finest(String.format(
                "Metric data details will be sent with the following values: name=%s  namespace=%s, dimensions=%s, datapoints.timestamp=%s datapoints.value=%f",
                nameFormatter.format(metricId, suffix, metadata),
                namespace,
                dimensions,
                datapoints.get(0).getTimestamp(),
                datapoints.get(0).getValue()));
        return MetricDataDetails.builder()
                .compartmentId(compartmentId)
                .name(nameFormatter.format(metricId, suffix, metadata))
                .namespace(namespace)
                .resourceGroup(resourceGroup)
                .metadata(ociMetadata(metadata))
                .datapoints(datapoints)
                .dimensions(dimensions)
                .build();
    }

    private Map<String, String> dimensions(MetricID metricId, MetricRegistry metricRegistry) {
        String registryType = metricRegistries.get(metricRegistry).getName();
        Map<String, String> result = new HashMap<>(metricId.getTags());
        result.put("scope", registryType);
        return result;
    }

    private double convertUnits(String metricUnits, double value) {
        for (UnitConverter converter : UNIT_CONVERTERS) {
            if (converter.handles(metricUnits)) {
                return converter.convert(metricUnits, value);
            }
        }
        return value;
    }

    private List<Datapoint> datapoints(Metadata metadata, double value) {
        return Collections.singletonList(Datapoint.builder()
                .value(convertUnits(metadata.getUnit().orElse(null), value))
                .timestamp(new Date())
                .build());
    }

    private static String formattedBaseUnits(String metricUnits) {
        String baseUnits = baseMetricUnits(metricUnits);
        return baseUnits == null ? "" : baseUnits;
    }

    private static String baseMetricUnits(String metricUnits) {
        if (!MetricUnits.NONE.equals(metricUnits) && !metricUnits.isEmpty()) {
            for (UnitConverter converter : UNIT_CONVERTERS) {
                if (converter.handles(metricUnits)) {
                    return converter.baseUnits();
                }
            }
        }
        return null;
    }

    private Map<String, String> ociMetadata(Metadata metadata) {
        return (descriptionEnabled && metadata.getDescription().isPresent())
                ? Collections.singletonMap("description", metadata.getDescription().get())
                : null;
    }
}