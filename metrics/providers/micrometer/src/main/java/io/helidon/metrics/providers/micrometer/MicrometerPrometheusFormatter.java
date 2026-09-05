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

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MeterRegistryFormatter;
import io.helidon.service.registry.Services;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.prometheus.client.Collector;
import io.prometheus.client.exporter.common.TextFormat;

/**
 * Retrieves and prepares meter output from the specified meter registry according to the formats supported by the Prometheus
 * meter registry.
 * <p>
 * Because the Prometheus exposition format is flat, and because some meter types have multiple values, the meter names
 * in the output repeat the actual meter name with suffixes to indicate the specific quantities (e.g.,
 * count, total, max) each reported value conveys. Further, meter names in the output might need the prefix
 * "m_" if the actual meter name starts with a digit or underscore and underscores replace special characters.
 * </p>
 */
public class MicrometerPrometheusFormatter implements MeterRegistryFormatter {
    /**
     * Mapping from supported media types to the corresponding Prometheus registry content types.
     */
    public static final Map<MediaType, String> MEDIA_TYPE_TO_FORMAT = Map.of(
            MediaTypes.TEXT_PLAIN, TextFormat.CONTENT_TYPE_004,
            MediaTypes.APPLICATION_OPENMETRICS_TEXT, TextFormat.CONTENT_TYPE_OPENMETRICS_100);

    private static final Pattern SPECIAL_CHARACTERS_MAPPED_TO_UNDERSCORE_PATTERN = Pattern.compile("[-+.!?@#$%^&*`'\\s]+");
    private static final Pattern NON_DIGIT_OR_UNDERSCORE_PREFIX_PATTERN = Pattern.compile("^[0-9_]+.*");
    private static final Pattern NON_IDENTIFIER_PATTERN = Pattern.compile("[^A-Za-z0-9_:]");

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

    /**
     * Returns the Prometheus-format meter name suffixes for the given meter type.
     *
     * @param meterType {@link io.micrometer.core.instrument.Meter.Type} of interest
     * @return suffixes used in reporting the corresponding meter's value(s)
     */
    static Set<String> meterNameSuffixes(Meter.Type meterType) {
        return switch (meterType) {
            case COUNTER -> Set.of("_total");
            case DISTRIBUTION_SUMMARY, LONG_TASK_TIMER, TIMER -> Set.of("_count", "_sum", "_max", "_bucket");
            case GAUGE, OTHER -> Set.of();
        };
    }

    static Optional<PrometheusMeterRegistry> prometheusMeterRegistry(MeterRegistry meterRegistry) {
        io.micrometer.core.instrument.MeterRegistry mMeterRegistry;
        try {
            mMeterRegistry = meterRegistry.unwrap(io.micrometer.core.instrument.MeterRegistry.class);
        } catch (ClassCastException ignored) {
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
     * Prepares a set containing the names of meters from the specified Prometheus meter registry which match
     * the specified meter name selections.
     * <p>
     * For meters with multiple values, the Prometheus registry essentially creates and actually displays in its output
     * additional or "child" meters. A child meter's name is the parent's name plus a suffix consisting
     * of the child meter's units (if any) plus the child name. For example, the timer {@code myDelay}  has child meters
     * {@code myDelay_seconds_count}, {@code myDelay_seconds_sum}, and {@code myDelay_seconds_max}. (The output contains
     * repetitions of the parent meter's name for each quantile, but that does not affect the meter names we need to ask
     * the Prometheus meter registry to retrieve for us when we scrape.)
     * </p>
     * <p>
     * We interpret any name selection passed to this method as specifying a parent name. We can ask the Prometheus meter
     * registry to select specific meters by meter name when we scrape, but we need to pass it an expanded name selection that
     * includes the relevant child meter names as well as the parent name. One way to choose those is first to collect the
     * names from the Prometheus meter registry itself and derive the names to have the meter registry select by from those
     * matching meters, their units, etc.
     * </p>
     *
     * @param prometheusMeterRegistry Prometheus meter registry to query
     * @param names           meter names to select
     * @return set of matching meter names (with units and suffixes as needed) to match the names as stored in the meter registry
     */
    Set<String> meterNamesOfInterest(PrometheusMeterRegistry prometheusMeterRegistry,
                                     Set<String> names) {

        Set<String> result = new HashSet<>();

        for (Meter meter : prometheusMeterRegistry.getMeters()) {
            String meterName = meter.getId().getName();
            if (!names.isEmpty() && !names.contains(meterName)) {
                continue;
            }
            Set<String> allUnitsForMeterName = new HashSet<>();
            allUnitsForMeterName.add("");
            Set<String> allSuffixesForMeterName = new HashSet<>();
            allSuffixesForMeterName.add("");

            prometheusMeterRegistry.find(meterName)
                    .meters()
                    .forEach(m -> {
                        Meter.Id meterId = m.getId();
                        String normalizedUnit = normalizeUnit(meterId.getBaseUnit());
                        if (!normalizedUnit.isBlank()) {
                            allUnitsForMeterName.add("_" + normalizedUnit);
                        }
                        allSuffixesForMeterName.addAll(meterNameSuffixes(meterId.getType()));
                    });

            String normalizedMeterName = normalizeNameToPrometheus(meterName);

            allUnitsForMeterName
                    .forEach(units -> allSuffixesForMeterName
                            .forEach(suffix -> result.add(normalizedMeterName + units + suffix)));
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

    private static String normalizeUnit(String unit) {
        return unit == null ? "" : unit;
    }

    private String scrapeSelected(PrometheusMeterRegistry prometheusMeterRegistry, Set<String> meterNamesOfInterest) {
        Enumeration<Collector.MetricFamilySamples> metricFamilySamples = meterNamesOfInterest == null
                ? prometheusMeterRegistry.getPrometheusRegistry().metricFamilySamples()
                : prometheusMeterRegistry.getPrometheusRegistry().filteredMetricFamilySamples(meterNamesOfInterest);
        List<Collector.MetricFamilySamples> matchingFamilies = new ArrayList<>();

        while (metricFamilySamples.hasMoreElements()) {
            Collector.MetricFamilySamples family = metricFamilySamples.nextElement();
            List<Collector.MetricFamilySamples.Sample> matchingSamples = family.samples.stream()
                    .filter(sample -> matchesTagSelection(prometheusMeterRegistry, sample))
                    .toList();
            if (!matchingSamples.isEmpty()) {
                matchingFamilies.add(new Collector.MetricFamilySamples(family.name,
                                                                        family.unit,
                                                                        family.type,
                                                                        family.help,
                                                                        matchingSamples));
            }
        }
        if (matchingFamilies.isEmpty()) {
            return "";
        }

        StringWriter result = new StringWriter();
        try {
            TextFormat.writeFormat(MEDIA_TYPE_TO_FORMAT.get(resultMediaType),
                                   result,
                                   Collections.enumeration(matchingFamilies));
        } catch (IOException e) {
            throw new UncheckedIOException("Error preparing Prometheus metrics output", e);
        }
        return result.toString();
    }

    private boolean matchesTagSelection(PrometheusMeterRegistry prometheusMeterRegistry,
                                        Collector.MetricFamilySamples.Sample sample) {
        for (Map.Entry<String, Set<String>> selection : tagSelection.entrySet()) {
            String tagName = prometheusMeterRegistry.config().namingConvention().tagKey(selection.getKey());
            int labelIndex = sample.labelNames.indexOf(tagName);
            if (labelIndex < 0 || !selection.getValue().contains(sample.labelValues.get(labelIndex))) {
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
