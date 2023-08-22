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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.DistributionSummary;
import io.helidon.metrics.api.Gauge;
import io.helidon.metrics.api.HistogramSnapshot;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.Metrics;
import io.helidon.metrics.api.Timer;

import com.oracle.bmc.monitoring.model.Datapoint;
import com.oracle.bmc.monitoring.model.MetricDataDetails;

class OciMetricsData {
    private static final UnitConverter STORAGE_UNIT_CONVERTER = UnitConverter.storageUnitConverter();
    private static final UnitConverter TIME_UNIT_CONVERTER = UnitConverter.timeUnitConverter();
    private static final List<UnitConverter> UNIT_CONVERTERS = List.of(STORAGE_UNIT_CONVERTER, TIME_UNIT_CONVERTER);

    private final Set<String> scopes;
    private final OciMetricsSupport.NameFormatter nameFormatter;
    private final String compartmentId;
    private final String namespace;
    private final String resourceGroup;
    private final boolean descriptionEnabled;

    OciMetricsData(
            Set<String> scopes,
            OciMetricsSupport.NameFormatter nameFormatter,
            String compartmentId,
            String namespace,
            String resourceGroup,
            boolean descriptionEnabled) {
        this.compartmentId = compartmentId;
        this.nameFormatter = nameFormatter;
        this.namespace = namespace;
        this.resourceGroup = resourceGroup;
        this.descriptionEnabled = descriptionEnabled;
        this.scopes = scopes;
    }

    List<MetricDataDetails> getMetricDataDetails() {
        boolean hasWildcardScope = scopes.contains("*");
        List<MetricDataDetails> allMetricDataDetails = new ArrayList<>();
        Metrics.globalRegistry().meters().stream()
                .filter(meter -> hasWildcardScope || (meter.scope().isPresent() && scopes.contains(meter.scope().get())))
                    .flatMap(this::metricDataDetails)
                    .forEach(allMetricDataDetails::add);
        return allMetricDataDetails;
    }

    Stream<MetricDataDetails> metricDataDetails(Meter metric) {
        if (metric instanceof Counter) {
            return forCounter(metric.id(), ((Counter) metric));
        } else if (metric instanceof Gauge) {
            return forGauge(metric.id(), ((Gauge) metric));
        } else if (metric instanceof Timer) {
            return forTimer(metric.id(), ((Timer) metric));
        } else if (metric instanceof DistributionSummary) {
            return forHistogram(metric.id(), ((DistributionSummary) metric));
        } else {
            return Stream.empty();
        }
    }

    private Stream<MetricDataDetails> forCounter(Meter.Id metricId, Counter counter) {
        return Stream.of(metricDataDetails(counter, metricId, null, counter.count()));
    }

    private Stream<MetricDataDetails> forGauge(Meter.Id metricId, Gauge gauge) {
        return Stream.of(metricDataDetails(gauge, metricId, null, gauge.value()));
    }

    private Stream<MetricDataDetails> forTimer(Meter.Id metricId, Timer timer) {
        Stream.Builder<MetricDataDetails> result = Stream.builder();
        long count = timer.count();
        result.add(metricDataDetails(timer, metricId, "seconds_count", count));
        if (count > 0) {
            HistogramSnapshot snapshot = timer.snapshot();
            result.add(metricDataDetails(timer,
                                         metricId,
                                         "mean_seconds",
                                         snapshot.mean()));
            result.add(metricDataDetails(timer,
                                         metricId,
                                        "max_seconds",
                                         snapshot.max()));
        }
        return result.build();
    }

    private Stream<MetricDataDetails> forHistogram(Meter.Id metricId, DistributionSummary histogram) {
        Stream.Builder<MetricDataDetails> result = Stream.builder();
        long count = histogram.count();
        String units = histogram.baseUnit();
        String unitsPrefix = units != null && !Objects.equals(units, Meter.BaseUnits.NONE) ? units + "_" : "";
        String unitsSuffix = units != null && !Objects.equals(units, Meter.BaseUnits.NONE) ? "_" + units : "";
        result.add(metricDataDetails(histogram, metricId, unitsPrefix + "count", count));
        if (count > 0) {
            HistogramSnapshot snapshot = histogram.snapshot();
            result.add(metricDataDetails(histogram,
                                         metricId,
                                         "mean" + unitsSuffix,
                                         snapshot.mean()));
            result.add(metricDataDetails(histogram,
                                         metricId,
                                         "max" + unitsSuffix,
                                         snapshot.max()));
        }
        return result.build();
    }

    private MetricDataDetails metricDataDetails(Meter metric,
            Meter.Id metricId, String suffix, double value) {
        if (Double.isNaN(value)) {
            return null;
        }

        Map<String, String> dimensions = dimensions(metric);
        List<Datapoint> datapoints = datapoints(metric.description(), value);
        String metricName = nameFormatter.format(metric, metricId, suffix, metric.baseUnit());
        return MetricDataDetails.builder()
                .compartmentId(compartmentId)
                .name(metricName)
                .namespace(namespace)
                .resourceGroup(resourceGroup)
                .metadata(ociMetadata(metric.baseUnit()))
                .datapoints(datapoints)
                .dimensions(dimensions)
                .build();
    }

    private Map<String, String> dimensions(Meter metric) {
        Map<String, String> result = metric.id().tagsMap();
        result.put("scope", metric.scope().orElse(Meter.Scope.VENDOR));
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

    private List<Datapoint> datapoints(String unit, double value) {
        return Collections.singletonList(Datapoint.builder()
                                                 .value(convertUnits(unit, value))
                                                 .timestamp(new Date())
                                                 .build());
    }

    private Map<String, String> ociMetadata(String description) {
        return (descriptionEnabled && description != null && !description.isEmpty())
                ? Collections.singletonMap("description",
                                           description.length() <= 256
                                                   ? description
                                                   // trim metadata value as oci metadata.value has a maximum of 256 characters
                                                   : description.substring(0, 256))
                : null;
    }
}
