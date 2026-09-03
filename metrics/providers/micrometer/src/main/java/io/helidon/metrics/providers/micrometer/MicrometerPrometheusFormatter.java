/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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
package io.helidon.metrics.providers.micrometer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MeterRegistryFormatter;
import io.helidon.service.registry.Services;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.prometheus.metrics.expositionformats.ExpositionFormatWriter;
import io.prometheus.metrics.expositionformats.OpenMetricsTextFormatWriter;
import io.prometheus.metrics.expositionformats.PrometheusTextFormatWriter;
import io.prometheus.metrics.model.snapshots.CounterSnapshot;
import io.prometheus.metrics.model.snapshots.DataPointSnapshot;
import io.prometheus.metrics.model.snapshots.GaugeSnapshot;
import io.prometheus.metrics.model.snapshots.HistogramSnapshot;
import io.prometheus.metrics.model.snapshots.InfoSnapshot;
import io.prometheus.metrics.model.snapshots.MetricSnapshot;
import io.prometheus.metrics.model.snapshots.MetricSnapshots;
import io.prometheus.metrics.model.snapshots.StateSetSnapshot;
import io.prometheus.metrics.model.snapshots.SummarySnapshot;
import io.prometheus.metrics.model.snapshots.UnknownSnapshot;

/**
 * Retrieves and prepares meter output from the specified meter registry according to the formats supported by the Prometheus
 * meter registry.
 * <p>
 * Because the Prometheus exposition format is flat, and because some meter types have multiple values, the meter names
 * in the output repeat the actual meter name with suffixes to indicate the specific quantities (e.g.,
 * count, total, max) each reported value conveys. The active Prometheus naming convention controls how meter and tag names
 * are normalized.
 * </p>
 */
public class MicrometerPrometheusFormatter implements MeterRegistryFormatter {
    /**
     * Mapping from supported media types to the corresponding Prometheus registry content types.
     */
    public static final Map<MediaType, String> MEDIA_TYPE_TO_FORMAT = Map.of(
            MediaTypes.TEXT_PLAIN, PrometheusTextFormatWriter.CONTENT_TYPE,
            MediaTypes.APPLICATION_OPENMETRICS_TEXT, OpenMetricsTextFormatWriter.CONTENT_TYPE);

    private static final Map<MediaType, ExpositionFormatWriter> MEDIA_TYPE_TO_WRITER = Map.of(
            MediaTypes.TEXT_PLAIN, PrometheusTextFormatWriter.create(),
            MediaTypes.APPLICATION_OPENMETRICS_TEXT, OpenMetricsTextFormatWriter.builder()
                    .setCreatedTimestampsEnabled(false)
                    .setExemplarsOnAllMetricTypesEnabled(true)
                    .build());
    private static final Pattern SPECIAL_CHARACTERS_MAPPED_TO_UNDERSCORE_PATTERN = Pattern.compile("[-+.!?@#$%^&*`'\\s]+");
    private static final Pattern NON_DIGIT_OR_UNDERSCORE_PREFIX_PATTERN = Pattern.compile("^[0-9_]+.*");
    private static final Pattern NON_IDENTIFIER_PATTERN = Pattern.compile("[^A-Za-z0-9_:]");
    private static final Set<String> MICROMETER_GENERATED_LABEL_NAMES = Set.of("le", "quantile", "statistic", "vmrange");

    private final Set<String> meterNames;
    private final Map<String, Set<String>> tagSelection;
    private final MediaType resultMediaType;
    private final MeterRegistry meterRegistry;

    private MicrometerPrometheusFormatter(Builder builder) {
        meterNames = copyToSet(builder.meterNameSelection);
        tagSelection = builder.tagSelection;
        resultMediaType = builder.resultMediaType;
        meterRegistry = Objects.requireNonNullElseGet(builder.meterRegistry,
                                                      () -> Services.get(MeterRegistry.class));
    }

    /**
     * Returns a new builder for constructing a formatter.
     *
     * @param meterRegistry the {@link io.helidon.metrics.api.MeterRegistry} from which to build the Prometheus output
     * @return new builder
     */
    public static Builder builder(MeterRegistry meterRegistry) {
        return new Builder(meterRegistry);
    }

    /**
     * Convert the meter or tag name to the format used by the Prometheus simple client.
     *
     * @param name original name
     * @return normalized name
     */
    public static String normalizeNameToPrometheus(String name) {
        String result = name;

        // Convert special characters to underscores.
        result = SPECIAL_CHARACTERS_MAPPED_TO_UNDERSCORE_PATTERN.matcher(result).replaceAll("_");

        // Prometheus simple client adds the prefix "m_" if a meter name starts with a digit or an underscore.
        if (NON_DIGIT_OR_UNDERSCORE_PREFIX_PATTERN.matcher(result).matches()) {
            result = "m_" + result;
        }

        // Replace non-identifier characters.
        result = NON_IDENTIFIER_PATTERN.matcher(result).replaceAll("_");

        return result;
    }

    static Optional<PrometheusMeterRegistry> prometheusMeterRegistry(MeterRegistry meterRegistry) {
        io.micrometer.core.instrument.MeterRegistry mMeterRegistry;
        try {
            mMeterRegistry = meterRegistry.unwrap(io.micrometer.core.instrument.MeterRegistry.class);
        } catch (ClassCastException _) {
            return Optional.empty();
        }
        if (mMeterRegistry instanceof CompositeMeterRegistry compositeMeterRegistry) {
            return compositeMeterRegistry.getRegistries().stream()
                    .filter(PrometheusMeterRegistry.class::isInstance)
                    .findFirst()
                    .map(PrometheusMeterRegistry.class::cast);
        }
        return Optional.empty();
    }

    /**
     * Returns the Prometheus output governed by the previously-specified media type, optionally filtered
     * by the previously-specified tag and meter name selections.
     *
     * @return filtered Prometheus output
     */
    @Override
    public Optional<Object> format() {

        Optional<PrometheusMeterRegistry> prometheusMeterRegistry = prometheusMeterRegistry(meterRegistry);
        if (prometheusMeterRegistry.isPresent()) {

            Set<String> meterNamesOfInterest = meterNames.isEmpty()
                    ? null // The Prometheus registry's scrape method treats null as "match all names."
                    : meterNamesOfInterest(prometheusMeterRegistry.get(), meterNames);
            if (meterNamesOfInterest != null && meterNamesOfInterest.isEmpty()) {
                return Optional.empty();
            }

            String prometheusOutput = tagSelection.isEmpty()
                    ? prometheusMeterRegistry.get()
                            .scrape(MEDIA_TYPE_TO_FORMAT.get(resultMediaType), meterNamesOfInterest)
                    : scrapeSelected(prometheusMeterRegistry.get(), meterNamesOfInterest);

            return prometheusOutput.isBlank() ? Optional.empty() : Optional.of(prometheusOutput);
        }
        return Optional.empty();
    }

    @Override
    public Optional<Object> formatMetadata() {
        return Optional.empty();
    }

    /**
     * Prepares a set containing the names of metric families from the specified Prometheus meter registry which match
     * the specified meter name selections.
     * <p>
     * The new Prometheus registry selects metric families, not individual emitted samples. Timers and distribution summaries
     * use a base family for count, sum, buckets, and quantiles and a separate family for the maximum value.
     * </p>
     *
     * @param prometheusMeterRegistry Prometheus meter registry to query
     * @param names           meter names to select
     * @return names of matching metric families as stored in the Prometheus registry
     */
    Set<String> meterNamesOfInterest(PrometheusMeterRegistry prometheusMeterRegistry,
                                     Set<String> names) {

        Set<String> result = new HashSet<>();

        for (Meter meter : prometheusMeterRegistry.getMeters()) {
            Meter.Id meterId = meter.getId();
            String meterName = LegacyPrometheusMeterFilter.originalGaugeName(meterId.getName());
            if (!names.isEmpty() && !names.contains(meterName)) {
                continue;
            }

            String conventionName = prometheusMeterRegistry.config()
                    .namingConvention()
                    .name(meterId.getName(), meterId.getType(), meterId.getBaseUnit());
            result.add(conventionName);
            if (meterId.getType() == Meter.Type.TIMER || meterId.getType() == Meter.Type.DISTRIBUTION_SUMMARY) {
                result.add(conventionName + "_max");
            }
        }
        return result;
    }

    private static Set<String> copyToSet(Iterable<String> values) {
        Set<String> result = new HashSet<>();
        values.forEach(value -> result.add(Objects.requireNonNull(value)));
        return Set.copyOf(result);
    }

    private static String flushForMeterAndClear(StringBuilder helpAndType, StringBuilder metricData) {
        StringBuilder result = new StringBuilder();
        if (!metricData.isEmpty()) {
            result.append(helpAndType.toString())
                    .append(metricData);
        }
        helpAndType.setLength(0);
        metricData.setLength(0);
        return result.toString();
    }

    private static Map<String, Set<String>> meterTagNamesByFamily(PrometheusMeterRegistry prometheusMeterRegistry) {
        Map<String, Set<String>> result = new HashMap<>();
        var namingConvention = prometheusMeterRegistry.config().namingConvention();
        prometheusMeterRegistry.forEachMeter(meter -> {
            Meter.Id meterId = meter.getId();
            String conventionName = meterId.getConventionName(namingConvention);
            String familyName = meterId.getType() == Meter.Type.COUNTER && conventionName.endsWith("_total")
                    ? conventionName.substring(0, conventionName.length() - "_total".length())
                    : conventionName;
            result.computeIfAbsent(familyName,
                                   _ -> meterId.getConventionTags(namingConvention).stream()
                                           .map(Tag::getKey)
                                           .collect(Collectors.toUnmodifiableSet()));
            if (meter instanceof Timer || meter instanceof DistributionSummary || meter instanceof LongTaskTimer) {
                result.putIfAbsent(conventionName + "_max", result.get(familyName));
            }
        });
        return result;
    }

    private static MetricSnapshot withDataPoints(MetricSnapshot snapshot,
                                                 List<? extends DataPointSnapshot> dataPoints) {
        if (snapshot instanceof CounterSnapshot counterSnapshot) {
            return new CounterSnapshot(counterSnapshot.getMetadata(),
                                       dataPoints.stream()
                                               .map(CounterSnapshot.CounterDataPointSnapshot.class::cast)
                                               .toList());
        }
        if (snapshot instanceof GaugeSnapshot gaugeSnapshot) {
            return new GaugeSnapshot(gaugeSnapshot.getMetadata(),
                                     dataPoints.stream()
                                             .map(GaugeSnapshot.GaugeDataPointSnapshot.class::cast)
                                             .toList());
        }
        if (snapshot instanceof HistogramSnapshot histogramSnapshot) {
            return new HistogramSnapshot(histogramSnapshot.isGaugeHistogram(),
                                         histogramSnapshot.getMetadata(),
                                         dataPoints.stream()
                                                 .map(HistogramSnapshot.HistogramDataPointSnapshot.class::cast)
                                                 .toList());
        }
        if (snapshot instanceof SummarySnapshot summarySnapshot) {
            return new SummarySnapshot(summarySnapshot.getMetadata(),
                                       dataPoints.stream()
                                               .map(SummarySnapshot.SummaryDataPointSnapshot.class::cast)
                                               .toList());
        }
        if (snapshot instanceof InfoSnapshot infoSnapshot) {
            return new InfoSnapshot(infoSnapshot.getMetadata(),
                                    dataPoints.stream()
                                            .map(InfoSnapshot.InfoDataPointSnapshot.class::cast)
                                            .toList());
        }
        if (snapshot instanceof StateSetSnapshot stateSetSnapshot) {
            return new StateSetSnapshot(stateSetSnapshot.getMetadata(),
                                        dataPoints.stream()
                                                .map(StateSetSnapshot.StateSetDataPointSnapshot.class::cast)
                                                .toList());
        }
        if (snapshot instanceof UnknownSnapshot unknownSnapshot) {
            return new UnknownSnapshot(unknownSnapshot.getMetadata(),
                                       dataPoints.stream()
                                               .map(UnknownSnapshot.UnknownDataPointSnapshot.class::cast)
                                               .toList());
        }
        throw new IllegalStateException("Unsupported Prometheus metric snapshot type: " + snapshot.getClass().getName());
    }

    private String scrapeSelected(PrometheusMeterRegistry prometheusMeterRegistry, Set<String> meterNamesOfInterest) {
        MetricSnapshots snapshots = meterNamesOfInterest == null
                ? prometheusMeterRegistry.getPrometheusRegistry().scrape()
                : prometheusMeterRegistry.getPrometheusRegistry().scrape(meterNamesOfInterest::contains);
        var namingConvention = prometheusMeterRegistry.config().namingConvention();
        boolean selectsGeneratedLabel = tagSelection.keySet().stream()
                .map(namingConvention::tagKey)
                .anyMatch(MICROMETER_GENERATED_LABEL_NAMES::contains);
        Map<String, Set<String>> meterTagNamesByFamily = selectsGeneratedLabel
                ? meterTagNamesByFamily(prometheusMeterRegistry)
                : Map.of();
        MetricSnapshots.Builder matchingSnapshotsBuilder = MetricSnapshots.builder();

        for (MetricSnapshot snapshot : snapshots) {
            Set<String> meterTagNames = selectsGeneratedLabel
                    ? meterTagNamesByFamily.getOrDefault(snapshot.getMetadata().getPrometheusName(), Set.of())
                    : Set.of();
            List<? extends DataPointSnapshot> matchingDataPoints = snapshot.getDataPoints().stream()
                    .filter(dataPoint -> matchesTagSelection(prometheusMeterRegistry,
                                                             meterTagNames,
                                                             dataPoint))
                    .toList();
            if (!matchingDataPoints.isEmpty()) {
                matchingSnapshotsBuilder.metricSnapshot(withDataPoints(snapshot, matchingDataPoints));
            }
        }

        MetricSnapshots matchingSnapshots = matchingSnapshotsBuilder.build();
        if (matchingSnapshots.size() == 0) {
            return "";
        }

        ByteArrayOutputStream result = new ByteArrayOutputStream();
        try {
            MEDIA_TYPE_TO_WRITER.get(resultMediaType).write(result, matchingSnapshots);
        } catch (IOException e) {
            throw new UncheckedIOException("Error preparing Prometheus metrics output", e);
        }
        return result.toString(StandardCharsets.UTF_8);
    }

    private boolean matchesTagSelection(PrometheusMeterRegistry prometheusMeterRegistry,
                                        Set<String> meterTagNames,
                                        DataPointSnapshot dataPoint) {
        for (Map.Entry<String, Set<String>> selection : tagSelection.entrySet()) {
            String tagName = prometheusMeterRegistry.config().namingConvention().tagKey(selection.getKey());
            if (MICROMETER_GENERATED_LABEL_NAMES.contains(tagName) && !meterTagNames.contains(tagName)) {
                return false;
            }
            String tagValue = dataPoint.getLabels().get(tagName);
            if (tagValue == null || !selection.getValue().contains(tagValue)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Builder for creating a tailored Prometheus formatter.
     */
    public static class Builder implements io.helidon.common.Builder<Builder, MicrometerPrometheusFormatter> {

        private Iterable<String> meterNameSelection = Set.of();
        private Map<String, Set<String>> tagSelection = Map.of();
        private MediaType resultMediaType = MediaTypes.TEXT_PLAIN;
        private MeterRegistry meterRegistry;

        /**
         * Used only internally.
         */
        private Builder() {
        }

        private Builder(MeterRegistry meterRegistry) {
            this.meterRegistry = meterRegistry;
        }

        @Override
        public MicrometerPrometheusFormatter build() {
            return new MicrometerPrometheusFormatter(this);
        }

        /**
         * Sets the meter name with which to filter the output.
         *
         * @param meterNameSelection meter name to select
         * @return updated builder
         */
        public Builder meterNameSelection(Iterable<String> meterNameSelection) {
            this.meterNameSelection = Objects.requireNonNull(meterNameSelection);
            return identity();
        }

        /**
         * Sets the tag names and allowed values with which to filter the output.
         * A meter must match one allowed value for every configured tag name.
         *
         * @param tagSelection tag names and their allowed values
         * @return updated builder
         */
        public Builder tagSelection(Map<String, ? extends Collection<String>> tagSelection) {
            Objects.requireNonNull(tagSelection);
            Map<String, Set<String>> copy = new HashMap<>();
            tagSelection.forEach((name, values) -> copy.put(Objects.requireNonNull(name),
                                                            Set.copyOf(Objects.requireNonNull(values))));
            this.tagSelection = Map.copyOf(copy);
            return identity();
        }

        /**
         * No-op, will be removed.
         *
         * @param ignoredScopeSelection ignored; must not be {@code null}
         * @return updated builder
         * @deprecated No-op, will be removed.
         */
        @Deprecated(since = "27.0.0", forRemoval = true)
        public Builder scopeSelection(Iterable<String> ignoredScopeSelection) {
            Objects.requireNonNull(ignoredScopeSelection);
            return identity();
        }

        /**
         * No-op, will be removed.
         *
         * @param ignoredScopeTagName ignored; must not be {@code null}
         * @return updated builder
         * @deprecated No-op, will be removed.
         */
        @Deprecated(since = "27.0.0", forRemoval = true)
        public Builder scopeTagName(String ignoredScopeTagName) {
            Objects.requireNonNull(ignoredScopeTagName);
            return identity();
        }

        /**
         * Sets the {@link io.helidon.common.media.type.MediaType} which controls the formatting of the resulting output.
         *
         * @param resultMediaType media type
         * @return updated builder
         */
        public Builder resultMediaType(MediaType resultMediaType) {
            this.resultMediaType = resultMediaType;
            return identity();
        }
    }
}
