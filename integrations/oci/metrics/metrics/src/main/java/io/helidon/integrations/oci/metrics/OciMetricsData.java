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
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import com.oracle.bmc.monitoring.model.Datapoint;
import com.oracle.bmc.monitoring.model.MetricDataDetails;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.Snapshot;
import org.eclipse.microprofile.metrics.Timer;

class OciMetricsData {
    private static final UnitConverter STORAGE_UNIT_CONVERTER = UnitConverter.storageUnitConverter();
    private static final UnitConverter TIME_UNIT_CONVERTER = UnitConverter.timeUnitConverter();
    private static final List<UnitConverter> UNIT_CONVERTERS = List.of(STORAGE_UNIT_CONVERTER, TIME_UNIT_CONVERTER);

    private final Map<MetricRegistry, String> metricRegistries;
    private final OciMetricsSupport.NameFormatter nameFormatter;
    private final String compartmentId;
    private final String namespace;
    private final String resourceGroup;
    private final boolean descriptionEnabled;

    OciMetricsData(
            Map<MetricRegistry, String> metricRegistries,
            OciMetricsSupport.NameFormatter nameFormatter,
            String compartmentId,
            String namespace,
            String resourceGroup,
            boolean descriptionEnabled) {
        this.metricRegistries = metricRegistries;
        this.compartmentId = compartmentId;
        this.nameFormatter = nameFormatter;
        this.namespace = namespace;
        this.resourceGroup = resourceGroup;
        this.descriptionEnabled = descriptionEnabled;
    }

    List<MetricDataDetails> getMetricDataDetails() {
        List<MetricDataDetails> allMetricDataDetails = new ArrayList<>();
        for (MetricRegistry metricRegistry : metricRegistries.keySet()) {
            metricRegistry.getMetrics().entrySet().stream()
                    .flatMap(entry -> metricDataDetails(metricRegistry, entry.getKey(), entry.getValue()))
                    .forEach(allMetricDataDetails::add);
        }
        return allMetricDataDetails;
    }

    Stream<MetricDataDetails> metricDataDetails(MetricRegistry metricRegistry, MetricID metricId, Metric metric) {
        if (metric instanceof Counter) {
            return forCounter(metricRegistry, metricId, ((Counter) metric));
        } else if (metric instanceof Gauge<?>) {
            return forGauge(metricRegistry, metricId, ((Gauge<? extends Number>) metric));
        } else if (metric instanceof Timer) {
            return forTimer(metricRegistry, metricId, ((Timer) metric));
        } else if (metric instanceof Histogram) {
            return forHistogram(metricRegistry, metricId, ((Histogram) metric));
        } else {
            return Stream.empty();
        }
    }

    private Stream<MetricDataDetails> forCounter(MetricRegistry metricRegistry, MetricID metricId, Counter counter) {
        return Stream.of(metricDataDetails(counter, metricRegistry, metricId, null, counter.getCount()));
    }

    private Stream<MetricDataDetails> forGauge(MetricRegistry metricRegistry, MetricID metricId, Gauge<? extends Number> gauge) {
        return Stream.of(metricDataDetails(gauge, metricRegistry, metricId, null, gauge.getValue().doubleValue()));
    }

    private Stream<MetricDataDetails> forTimer(MetricRegistry metricRegistry, MetricID metricId, Timer timer) {
        Stream.Builder<MetricDataDetails> result = Stream.builder();
        long count = timer.getCount();
        result.add(metricDataDetails(timer, metricRegistry, metricId, "seconds_count", count));
        if (count > 0) {
            Snapshot snapshot = timer.getSnapshot();
            result.add(metricDataDetails(timer,
                                         metricRegistry,
                                         metricId,
                                         "mean_seconds",
                                         snapshot.getMean()));
            result.add(metricDataDetails(timer,
                                         metricRegistry,
                                         metricId,
                                        "max_seconds",
                                         snapshot.getMax()));
        }
        return result.build();
    }

    private Stream<MetricDataDetails> forHistogram(MetricRegistry metricRegistry, MetricID metricId, Histogram histogram) {
        Stream.Builder<MetricDataDetails> result = Stream.builder();
        long count = histogram.getCount();
        Metadata metadata = metricRegistry.getMetadata().get(metricId.getName());
        String units = metadata.getUnit();
        String unitsPrefix = units != null && !Objects.equals(units, MetricUnits.NONE) ? units + "_" : "";
        String unitsSuffix = units != null && !Objects.equals(units, MetricUnits.NONE) ? "_" + units : "";
        result.add(metricDataDetails(histogram, metricRegistry, metricId, unitsPrefix + "count", count));
        if (count > 0) {
            Snapshot snapshot = histogram.getSnapshot();
            result.add(metricDataDetails(histogram,
                                         metricRegistry,
                                         metricId,
                                         "mean" + unitsSuffix,
                                         snapshot.getMean()));
            result.add(metricDataDetails(histogram,
                                         metricRegistry,
                                         metricId,
                                         "max" + unitsSuffix,
                                         snapshot.getMax()));
        }
        return result.build();
    }

    private MetricDataDetails metricDataDetails(Metric metric,
            MetricRegistry metricRegistry, MetricID metricId, String suffix, double value) {
        if (Double.isNaN(value)) {
            return null;
        }

        Metadata metadata = metricRegistry.getMetadata().get(metricId.getName());
        Map<String, String> dimensions = dimensions(metricId, metricRegistry);
        List<Datapoint> datapoints = datapoints(metadata, value);
        String metricName = nameFormatter.format(metric, metricId, suffix, metadata);
        return MetricDataDetails.builder()
                .compartmentId(compartmentId)
                .name(metricName)
                .namespace(namespace)
                .resourceGroup(resourceGroup)
                .metadata(ociMetadata(metadata))
                .datapoints(datapoints)
                .dimensions(dimensions)
                .build();
    }

    private Map<String, String> dimensions(MetricID metricId, MetricRegistry metricRegistry) {
        String registryType = metricRegistries.get(metricRegistry);
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
                                                 .value(convertUnits(metadata.getUnit(), value))
                                                 .timestamp(new Date())
                                                 .build());
    }

    private Map<String, String> ociMetadata(Metadata metadata) {
        return (descriptionEnabled && metadata.getDescription() != null && !metadata.getDescription().isEmpty())
                ? Collections.singletonMap("description",
                                           metadata.getDescription().length() <= 256
                                                   ? metadata.getDescription()
                                                   // trim metadata value as oci metadata.value has a maximum of 256 characters
                                                   : metadata.getDescription().substring(0, 256))
                : null;
    }
}
