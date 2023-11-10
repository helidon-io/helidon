/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MeterRegistryFormatter;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.prometheus.PrometheusMeterRegistry;
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
    private final String scopeTagName;
    private final Iterable<String> scopeSelection;
    private final Iterable<String> meterNameSelection;
    private final MediaType resultMediaType;
    private final MeterRegistry meterRegistry;

    private MicrometerPrometheusFormatter(Builder builder) {
        scopeTagName = builder.scopeTagName;
        scopeSelection = builder.scopeSelection;
        meterNameSelection = builder.meterNameSelection;
        resultMediaType = builder.resultMediaType;
        meterRegistry = Objects.requireNonNullElseGet(builder.meterRegistry,
                                                      io.helidon.metrics.api.Metrics::globalRegistry);
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
        result = result.replaceAll("[-+.!?@#$%^&*`'\\s]+", "_");

        // Prometheus simple client adds the prefix "m_" if a meter name starts with a digit or an underscore.
        if (result.matches("^[0-9_]+.*")) {
            result = "m_" + result;
        }

        // Replace non-identifier characters.
        result = result.replaceAll("[^A-Za-z0-9_]", "_");

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
            case DISTRIBUTION_SUMMARY, LONG_TASK_TIMER, TIMER -> Set.of("_count", "_sum", "_max");
            case GAUGE, OTHER -> Set.of();
        };
    }

    /**
     * Returns the Prometheus output governed by the previously-specified media type, optionally filtered
     * by the previously-specified scope and meter name selections.
     *
     * @return filtered Prometheus output
     */
    @Override
    public Optional<Object> format() {

        Optional<PrometheusMeterRegistry> prometheusMeterRegistry = prometheusMeterRegistry(meterRegistry);
        if (prometheusMeterRegistry.isPresent()) {

            // Scraping the Prometheus registry lets us limit the output to include only specified names.
            Set<String> meterNamesOfInterest = meterNamesOfInterest(prometheusMeterRegistry.get(),
                                                                    scopeSelection,
                                                                    meterNameSelection);
            if (meterNamesOfInterest.isEmpty()) {
                return Optional.empty();
            }

            String prometheusOutput = prometheusMeterRegistry.get()
                    .scrape(MicrometerPrometheusFormatter.MEDIA_TYPE_TO_FORMAT.get(
                                    resultMediaType),
                            meterNamesOfInterest);

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
     * the specified scope and meter name selections.
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
     * @param scopeSelection          scope names to select
     * @param meterNameSelection      meter names to select
     * @return set of matching meter names (with units and suffixes as needed) to match the names as stored in the meter registry
     */
    Set<String> meterNamesOfInterest(PrometheusMeterRegistry prometheusMeterRegistry,
                                     Iterable<String> scopeSelection,
                                     Iterable<String> meterNameSelection) {

        Set<String> result = new HashSet<>();

        var scopes = new HashSet<>();
        scopeSelection.forEach(scopes::add);

        var names = new HashSet<>();
        meterNameSelection.forEach(names::add);

        Predicate<Meter> scopePredicate = scopes.isEmpty() || scopeTagName == null || scopeTagName.isBlank()
                ? m -> true
                : m -> scopes.contains(m.getId().getTag(scopeTagName));

        Predicate<String> namePredicate = names.isEmpty() ? n -> true : names::contains;

        for (Meter meter : prometheusMeterRegistry.getMeters()) {
            String meterName = meter.getId().getName();
            if (!namePredicate.test(meterName) || !scopePredicate.test(meter)) {
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

    private static Optional<PrometheusMeterRegistry> prometheusMeterRegistry(MeterRegistry meterRegistry) {
        io.micrometer.core.instrument.MeterRegistry mMeterRegistry =
                meterRegistry.unwrap(io.micrometer.core.instrument.MeterRegistry.class);
        if (mMeterRegistry instanceof CompositeMeterRegistry compositeMeterRegistry) {
            return compositeMeterRegistry.getRegistries().stream()
                    .filter(PrometheusMeterRegistry.class::isInstance)
                    .findFirst()
                    .map(PrometheusMeterRegistry.class::cast);
        }
        return Optional.empty();
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

    /**
     * Builder for creating a tailored Prometheus formatter.
     */
    public static class Builder implements io.helidon.common.Builder<Builder, MicrometerPrometheusFormatter> {

        private Iterable<String> meterNameSelection = Set.of();
        private String scopeTagName;
        private Iterable<String> scopeSelection = Set.of();
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
            this.meterNameSelection = meterNameSelection;
            return identity();
        }

        /**
         * Sets the scope value with which to filter the output.
         *
         * @param scopeSelection scope to select
         * @return updated builder
         */
        public Builder scopeSelection(Iterable<String> scopeSelection) {
            this.scopeSelection = scopeSelection;
            return identity();
        }

        /**
         * Sets the scope tag name with which to filter the output.
         *
         * @param scopeTagName scope tag name
         * @return updated builder
         */
        public Builder scopeTagName(String scopeTagName) {
            this.scopeTagName = scopeTagName;
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
