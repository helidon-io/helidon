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
package io.helidon.metrics.microprofile;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;

import io.micrometer.core.instrument.Meter;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.prometheus.client.exporter.common.TextFormat;

/**
 * Retrieves and prepares meter output according to the formats supported by the Prometheus meter registry.
 * <p>
 *     Because the Prometheus exposition format is flat, and because some meter types have multiple values, the meter names
 *     in the output repeat the actual meter name with suffixes to indicate the specific quantities (e.g.,
 *     count, total, max) each reported value conveys. Further, meter names in the output might need the prefix
 *     "m_" if the actual meter name starts with a digit or underscore and underscores replace special characters.
 * </p>
 */
public class PrometheusFormatter {
    public static final Map<MediaType, String> MEDIA_TYPE_TO_FORMAT = Map.of(MediaTypes.TEXT_PLAIN,
                                                                             TextFormat.CONTENT_TYPE_004,
                                                                             MediaTypes.APPLICATION_OPENMETRICS_TEXT,
                                                                             TextFormat.CONTENT_TYPE_OPENMETRICS_100);

    public static Builder builder() {
        return new Builder();
    }

    private static final String PROMETHEUS_TYPE_PREFIX = "# TYPE";
    private static final String PROMETHEUS_HELP_PREFIX = "# HELP";

    private final String scopeSelection;
    private final String meterSelection;
    private final MediaType resultMediaType;

    private PrometheusFormatter(Builder builder) {
        scopeSelection = builder.scopeSelection;
        meterSelection = builder.meterNameSelection;
        resultMediaType = builder.resultMediaType;
    }

    public String filteredOutput() {
        return formattedOutput(MpRegistryFactory.get().prometheusMeterRegistry(),
                               resultMediaType,
                               scopeSelection,
                               meterSelection);
    }

    static String formattedOutput(PrometheusMeterRegistry prometheusMeterRegistry,
                                  MediaType resultMediaType,
                                  String scopeSelection,
                                  String meterNameSelection) {
        String rawPrometheusOutput = prometheusMeterRegistry
                .scrape(PrometheusFormatter.MEDIA_TYPE_TO_FORMAT.get(resultMediaType),
                        meterNamesOfInterest(prometheusMeterRegistry, meterNameSelection));

        return filter(rawPrometheusOutput, scopeSelection, meterNameSelection);

    }

    static String filter(String output, String scope, String meterName) {
        /*
         * Output looks like repeating sections of this:
         *
         * # HELP xxx
         * # TYPE yyy
         * meter-name{tagA=value1,tagB=value2} data
         * meter-name{tagA=value3,tagB=value4} otherData
         * ... (possibly more lines for the same meter with different tag values)
         *
         *
         * To select using scope or meter name, always accumulate the type and help information.
         * Then, once we have the line containing the actual meter ID, if that line matches the selection
         * add the previously-gathered help and type and the meter line to the output.
         */
        Pattern scopePattern = scope != null
                ? Pattern.compile(".*?\\{.*?mp_scope=\"" + scope + "\".*?}.*?")
                : null;

        StringBuilder allOutput = new StringBuilder();
        StringBuilder typeAndHelpOutputForCurrentMeter = new StringBuilder();
        StringBuilder meterOutputForCurrentMeter = new StringBuilder();

        String[] lines = output.split("\r?\n");


        for (String line : lines) {
            if (line.startsWith(PROMETHEUS_HELP_PREFIX)) {
                allOutput.append(flushForMeterAndClear(typeAndHelpOutputForCurrentMeter, meterOutputForCurrentMeter));
                typeAndHelpOutputForCurrentMeter.append(line)
                        .append(System.lineSeparator());
            } else if (line.startsWith(PROMETHEUS_TYPE_PREFIX)) {
                typeAndHelpOutputForCurrentMeter.append(line)
                        .append(System.lineSeparator());
            } else if (scopePattern == null || scopePattern.matcher(line).matches()) {
                meterOutputForCurrentMeter.append(line)
                        .append(System.lineSeparator());
            }
        }
        return allOutput.append(flushForMeterAndClear(typeAndHelpOutputForCurrentMeter, meterOutputForCurrentMeter))
                .toString()
                .replaceFirst("# EOF\r?\n?", "");

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

    static Set<String> meterNamesOfInterest(PrometheusMeterRegistry prometheusMeterRegistry,
                                            String meterNameSelection) {
        if (meterNameSelection == null || meterNameSelection.isEmpty()) {
            return null; // null passed to PrometheusMeterRegistry.scrape means "no selection based on meter name
        }

        // Meter names in the output include units (if specified) so retrieve matching meters to get the units.
        String normalizedMeterName = normalizeMeterName(meterNameSelection);
        Set<String> unitsForMeter = new HashSet<>();
        unitsForMeter.add("");

        Set<String> suffixesForMeter = new HashSet<>();
        suffixesForMeter.add("");

        prometheusMeterRegistry.find(meterNameSelection)
                .meters()
                .forEach(meter -> {
                    Meter.Id meterId = meter.getId();
                    unitsForMeter.add("_" + meterId.getBaseUnit());
                    suffixesForMeter.addAll(meterNameSuffixes(meterId.getType()));
                });

        Set<String> result = new HashSet<>();
        unitsForMeter.forEach(units -> suffixesForMeter.forEach(
                suffix -> result.add(normalizedMeterName + units + suffix)));

        return result;
    }

    static Set<String> meterNameSuffixes(Meter.Type meterType) {
        return switch (meterType) {
            case COUNTER -> Set.of("_total");
            case LONG_TASK_TIMER -> Set.of("_count", "_sum", "_max");
            case DISTRIBUTION_SUMMARY, TIMER, GAUGE, OTHER -> Set.of();
        };
    }

    /**
     * Convert the meter name to the format used by the Prometheus simple client.
     *
     * @param meterName name of the meter
     * @return normalized meter name
     */
    static String normalizeMeterName(String meterName) {
        String result = meterName;

        // Convert special characters to underscores.
        result = result.replaceAll("[-+.!?@#$%^&*`'\\s]+", "_");

        // Prometheus simple client adds the prefix "m_" if a meter name starts with a digit or an underscore.
        if (result.matches("^[0-9_]+.*")) {
            result = "m_" + result;
        }

        // Remove non-identifier characters.
        result = result.replaceAll("[^A-Za-z0-9_]", "");

        return result;
    }

    public static class Builder implements io.helidon.common.Builder<Builder, PrometheusFormatter> {

        private String meterNameSelection;
        private String scopeSelection;
        private MediaType resultMediaType = MediaTypes.TEXT_PLAIN;

        @Override
        public PrometheusFormatter build() {
            return new PrometheusFormatter(this);
        }

        public Builder meterName(String filter) {
            meterNameSelection = filter;
            return identity();
        }

        public Builder scope(String filter) {
            scopeSelection = filter;
            return identity();
        }

        public Builder resultMediaType(MediaType resultMediaType) {
            this.resultMediaType = resultMediaType;
            return identity();
        }
    }

}
