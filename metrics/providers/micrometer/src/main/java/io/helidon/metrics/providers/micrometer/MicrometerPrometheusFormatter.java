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

import java.util.HashSet;
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

import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.prometheus.metrics.expositionformats.OpenMetricsTextFormatWriter;
import io.prometheus.metrics.expositionformats.PrometheusTextFormatWriter;

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

    private static final Pattern SPECIAL_CHARACTERS_MAPPED_TO_UNDERSCORE_PATTERN = Pattern.compile("[-+.!?@#$%^&*`'\\s]+");
    private static final Pattern NON_DIGIT_OR_UNDERSCORE_PREFIX_PATTERN = Pattern.compile("^[0-9_]+.*");
    private static final Pattern NON_IDENTIFIER_PATTERN = Pattern.compile("[^A-Za-z0-9_:]");

    private final String scopeTagName;
    private final Set<String> scopes;
    private final Set<String> meterNames;
    private final MediaType resultMediaType;
    private final MeterRegistry meterRegistry;

    private MicrometerPrometheusFormatter(Builder builder) {
        scopeTagName = builder.scopeTagName;
        meterNames = (builder.meterNameSelection instanceof Set<String> namesSet)
                ? namesSet
                : new HashSet<>() {
                    {
                        builder.meterNameSelection.forEach(this::add);
                    }
                };

        scopes = (builder.scopeSelection instanceof Set<String> scopesSet)
                ? scopesSet
                : new HashSet<>() {
                    {
                        builder.scopeSelection.forEach(this::add);
                    }
                };
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
     * Returns the Prometheus output governed by the previously-specified media type, optionally filtered
     * by the previously-specified scope and meter name selections.
     *
     * @return filtered Prometheus output
     */
    @Override
    public Optional<Object> format() {

        Optional<PrometheusMeterRegistry> prometheusMeterRegistry = prometheusMeterRegistry(meterRegistry);
        if (prometheusMeterRegistry.isPresent()) {

            /*
            Optimize for the no-selection case (neither scope nor name selections were requested).
             */
            Set<String> meterNamesOfInterest;

            if (meterNames.isEmpty() && scopes.isEmpty()) {
                meterNamesOfInterest = null; // The Prometheus registry's scrape method treats null as "match all names."
            } else {
                meterNamesOfInterest = meterNamesOfInterest(prometheusMeterRegistry.get(),
                                     scopes,
                                     meterNames);
                if (meterNamesOfInterest.isEmpty()) {
                    return Optional.empty();
                }
            }

            String prometheusOutput = prometheusMeterRegistry.get()
                    .scrape(MicrometerPrometheusFormatter.MEDIA_TYPE_TO_FORMAT.get(resultMediaType),
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
     * Prepares a set containing the names of metric families from the specified Prometheus meter registry which match
     * the specified scope and meter name selections.
     * <p>
     * The new Prometheus registry selects metric families, not individual emitted samples. Timers and distribution summaries
     * use a base family for count, sum, buckets, and quantiles and a separate family for the maximum value.
     * </p>
     *
     * @param prometheusMeterRegistry Prometheus meter registry to query
     * @param scopes          scope names to select
     * @param names           meter names to select
     * @return names of matching metric families as stored in the Prometheus registry
     */
    Set<String> meterNamesOfInterest(PrometheusMeterRegistry prometheusMeterRegistry,
                                     Set<String> scopes,
                                     Set<String> names) {

        Set<String> result = new HashSet<>();

        for (io.helidon.metrics.api.Meter meter : meterRegistry.meters()) {
            String meterName = meter.id().name();
            if ((!names.isEmpty() && !names.contains(meterName))
                || (!scopes.isEmpty()
                            && scopeTagName != null
                            && !scopeTagName.isBlank()
                            && meter.scope().filter(scopes::contains).isEmpty())) {
                continue;
            }

            io.micrometer.core.instrument.Meter micrometerMeter =
                    meter.unwrap(io.micrometer.core.instrument.Meter.class);
            io.micrometer.core.instrument.Meter.Id meterId = micrometerMeter.getId();
            String conventionName = prometheusMeterRegistry.config()
                    .namingConvention()
                    .name(meterId.getName(), meterId.getType(), meterId.getBaseUnit());
            result.add(conventionName);
            if (meter.type() == io.helidon.metrics.api.Meter.Type.TIMER
                    || meter.type() == io.helidon.metrics.api.Meter.Type.DISTRIBUTION_SUMMARY) {
                result.add(conventionName + "_max");
            }
        }
        return result;
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
