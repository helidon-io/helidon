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

package io.helidon.metrics.providers.helidon;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;

import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.metrics.api.Bucket;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.DistributionSummary;
import io.helidon.metrics.api.FunctionalCounter;
import io.helidon.metrics.api.Gauge;
import io.helidon.metrics.api.HistogramSnapshot;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistryFormatter;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.SystemTagsManager;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.api.Timer;

final class HelidonPrometheusFormatter implements MeterRegistryFormatter {
    private static final Pattern NON_IDENTIFIER_PATTERN = Pattern.compile("[^A-Za-z0-9_:]");
    private static final Pattern NON_LABEL_IDENTIFIER_PATTERN = Pattern.compile("[^A-Za-z0-9_]");
    private static final Pattern RESERVED_SUFFIX_PATTERN = Pattern.compile("(?:_(?:total|created|bucket|info))+$");
    private static final Set<String> LE_LABEL = Set.of("le");
    private static final Set<String> QUANTILE_LABEL = Set.of("quantile");
    private static final Set<String> HISTOGRAM_QUANTILE_LABELS = Set.of("le", "quantile");
    private static final Set<String> NO_RESERVED_LABELS = Set.of();

    private final MediaType mediaType;
    private final HelidonMeterRegistry meterRegistry;
    private final SystemTagsManager systemTagsManager;
    private final Map<String, Collection<String>> tagSelections;
    private final boolean includeHistogramQuantiles;
    private final Set<String> nameSelection = new HashSet<>();

    HelidonPrometheusFormatter(MediaType mediaType,
                             MetricsConfig metricsConfig,
                             HelidonMeterRegistry meterRegistry,
                             Map<String, Collection<String>> tagSelections,
                             Iterable<String> nameSelection,
                             boolean includeHistogramQuantiles) {
        this.mediaType = Objects.requireNonNull(mediaType);
        this.includeHistogramQuantiles = includeHistogramQuantiles && matches(mediaType, MediaTypes.TEXT_PLAIN);
        Objects.requireNonNull(metricsConfig);
        this.meterRegistry = Objects.requireNonNull(meterRegistry);
        this.systemTagsManager = meterRegistry.systemTagsManager();
        Map<String, Collection<String>> selections = new LinkedHashMap<>();
        Objects.requireNonNull(tagSelections).forEach((name, values) -> selections.put(Objects.requireNonNull(name),
                                                                                   Set.copyOf(values)));
        this.tagSelections = Map.copyOf(selections);
        Objects.requireNonNull(nameSelection).forEach(name -> this.nameSelection.add(Objects.requireNonNull(name)));
    }

    @Override
    public Optional<Object> format() {
        StringBuilder output = new StringBuilder();
        Set<String> emittedMetadata = new HashSet<>();
        List<Meter> meters = selectedMeters();
        validateNames(meters);
        Map<String, String> descriptions = descriptions(meters);
        meters.stream()
                .sorted((a, b) -> primaryFamilyName(a).compareTo(primaryFamilyName(b)))
                .forEach(meter -> appendPrimary(output, emittedMetadata, descriptions, meter));
        meters.stream()
                .filter(HelidonPrometheusFormatter::hasMaxFamily)
                .sorted((a, b) -> maxFamilyName(a).compareTo(maxFamilyName(b)))
                .forEach(meter -> appendMax(output, emittedMetadata, descriptions, meter));
        if (output.isEmpty()) {
            return Optional.empty();
        }
        if (matches(mediaType, MediaTypes.APPLICATION_OPENMETRICS_TEXT)) {
            output.append("# EOF\n");
        }
        return Optional.of(output.toString());
    }

    @Override
    public Optional<Object> formatMetadata() {
        return Optional.empty();
    }

    static String normalizeNameToPrometheus(String name) {
        String result = NON_IDENTIFIER_PATTERN.matcher(name).replaceAll("_");
        if (!result.isEmpty() && Character.isDigit(result.charAt(0))) {
            result = "_" + result.substring(1);
        }
        return result;
    }

    static String normalizeLabelName(String name) {
        String result = NON_LABEL_IDENTIFIER_PATTERN.matcher(normalizeNameToPrometheus(name)).replaceAll("_");
        while (result.startsWith("__")) {
            result = result.substring(1);
        }
        return result;
    }

    private static double gaugeValue(Gauge<?> gauge) {
        try {
            return gauge.value().doubleValue();
        } catch (Throwable _) {
            // An unavailable user gauge must not suppress the remaining meters in the scrape.
            return Double.NaN;
        }
    }

    private boolean enabled(Meter meter) {
        return meterRegistry.isMeterEnabled(meter.id().name(), meter.id().tagsMap());
    }

    private boolean matchesName(String name) {
        return nameSelection.isEmpty() || nameSelection.contains(name);
    }

    private List<Meter> selectedMeters() {
        return meterRegistry.meters().stream()
                .filter(meter -> enabled(meter) && matchesName(meter.id().name()) && matchesTags(meter))
                .toList();
    }

    private boolean matchesTags(Meter meter) {
        if (tagSelections.isEmpty()) {
            return true;
        }
        Map<String, String> labels = labels(meter, NO_RESERVED_LABELS);
        return tagSelections.entrySet().stream()
                .allMatch(selection -> {
                    String value = labels.get(normalizeLabelName(selection.getKey()));
                    return value != null && selection.getValue().contains(value);
                });
    }

    private void validateNames(List<Meter> meters) {
        Map<String, Meter> owners = new HashMap<>();
        for (Meter meter : meters) {
            String name = promName(meter);
            List<String> names = switch (meter.type()) {
                case COUNTER -> List.of(name, name + "_total");
                case TIMER, DISTRIBUTION_SUMMARY -> List.of(name,
                                                          name + "_count",
                                                          name + "_sum",
                                                          name + "_bucket",
                                                          name + "_max");
                default -> List.of(name);
            };
            for (String exportedName : names) {
                Meter owner = owners.putIfAbsent(exportedName, meter);
                if (owner != null
                        && (!owner.id().name().equals(meter.id().name())
                        || owner.type() != meter.type()
                        // Timer text output always uses seconds, regardless of the preferred API unit.
                        || (!(meter instanceof Timer) && !owner.baseUnit().equals(meter.baseUnit())))) {
                    throw new IllegalArgumentException("Prometheus metric name collision: '" + exportedName
                                                               + "' is produced by both " + describeMeter(owner)
                                                               + " and " + describeMeter(meter));
                }
            }
        }
    }

    private static String describeMeter(Meter meter) {
        return "'" + meter.id().name() + "' (type=" + meter.type()
                + ", baseUnit=" + meter.baseUnit().orElse("<none>") + ")";
    }

    private Map<String, String> descriptions(List<Meter> meters) {
        Map<String, String> result = new LinkedHashMap<>();
        meters.forEach(meter -> meter.description()
                .ifPresent(description -> metadataNames(meter)
                        .forEach(name -> result.putIfAbsent(name, description))));
        return result;
    }

    private List<String> metadataNames(Meter meter) {
        if (meter instanceof Counter || meter instanceof FunctionalCounter) {
            String familyName = promName(meter);
            String sampleName = familyName + "_total";
            return matches(mediaType, MediaTypes.APPLICATION_OPENMETRICS_TEXT)
                    ? List.of(familyName, sampleName)
                    : List.of(sampleName, familyName);
        } else if (meter instanceof Timer) {
            String name = promName(meter);
            return List.of(name, name + "_max");
        } else if (meter instanceof DistributionSummary) {
            String name = promName(meter);
            return List.of(name, name + "_max");
        } else {
            return List.of(promName(meter));
        }
    }

    private String primaryFamilyName(Meter meter) {
        return metadataNames(meter).get(0);
    }

    private static boolean hasMaxFamily(Meter meter) {
        return meter instanceof Timer || meter instanceof DistributionSummary;
    }

    private String maxFamilyName(Meter meter) {
        return promName(meter) + "_max";
    }

    private void appendPrimary(StringBuilder output,
                               Set<String> emittedMetadata,
                               Map<String, String> descriptions,
                               Meter meter) {
        if (meter instanceof Counter counter) {
            appendCounter(output, emittedMetadata, descriptions, meter, counter.count());
        } else if (meter instanceof FunctionalCounter counter) {
            appendCounter(output, emittedMetadata, descriptions, meter, counter.count());
        } else if (meter instanceof Gauge<?> gauge) {
            appendSimple(output,
                         emittedMetadata,
                         descriptions,
                         meter,
                         promName(meter),
                         "gauge",
                         gaugeValue(gauge));
        } else if (meter instanceof Timer timer) {
            appendTimer(output, emittedMetadata, descriptions, meter, timer);
        } else if (meter instanceof DistributionSummary summary) {
            appendSummary(output, emittedMetadata, descriptions, meter, summary);
        }
    }

    private void appendMax(StringBuilder output,
                           Set<String> emittedMetadata,
                           Map<String, String> descriptions,
                           Meter meter) {
        if (meter instanceof Timer timer) {
            appendSimple(output,
                         emittedMetadata,
                         descriptions,
                         meter,
                         maxFamilyName(meter),
                         "gauge",
                         timer.max(TimeUnit.SECONDS));
        } else if (meter instanceof DistributionSummary summary) {
            appendSimple(output,
                         emittedMetadata,
                         descriptions,
                         meter,
                         maxFamilyName(meter),
                         "gauge",
                         summary.max());
        }
    }

    private void appendCounter(StringBuilder output,
                               Set<String> emittedMetadata,
                               Map<String, String> descriptions,
                               Meter meter,
                               long value) {
        String familyName = promName(meter);
        String sampleName = familyName + "_total";
        String metadataName = matches(mediaType, MediaTypes.APPLICATION_OPENMETRICS_TEXT) ? familyName : sampleName;
        appendHelpAndType(output, emittedMetadata, descriptions, metadataName, "counter", meter);
        appendSample(output, sampleName, meter, Map.of(), value);
    }

    private void appendSimple(StringBuilder output,
                              Set<String> emittedMetadata,
                              Map<String, String> descriptions,
                              Meter meter,
                              String name,
                              String type,
                              double value) {
        appendHelpAndType(output, emittedMetadata, descriptions, name, type, meter);
        appendSample(output, name, meter, Map.of(), value);
    }

    private void appendTimer(StringBuilder output,
                             Set<String> emittedMetadata,
                             Map<String, String> descriptions,
                             Meter meter,
                             Timer timer) {
        String name = promName(meter);
        HistogramSnapshot snapshot = timer.snapshot();
        if (hasBuckets(snapshot)) {
            appendHistogram(output,
                            emittedMetadata,
                            descriptions,
                            name,
                            meter,
                            snapshot,
                            true);
        } else {
            appendSummary(output,
                          emittedMetadata,
                          descriptions,
                          name,
                          meter,
                          snapshot,
                          true);
        }
    }

    private void appendSummary(StringBuilder output,
                               Set<String> emittedMetadata,
                               Map<String, String> descriptions,
                               Meter meter,
                               DistributionSummary summary) {
        String name = promName(meter);
        HistogramSnapshot snapshot = summary.snapshot();
        if (hasBuckets(snapshot)) {
            appendHistogram(output,
                            emittedMetadata,
                            descriptions,
                            name,
                            meter,
                            snapshot,
                            false);
        } else {
            appendSummary(output,
                          emittedMetadata,
                          descriptions,
                          name,
                          meter,
                          snapshot,
                          false);
        }
    }

    private void appendHistogram(StringBuilder output,
                                 Set<String> emittedMetadata,
                                 Map<String, String> descriptions,
                                 String name,
                                 Meter meter,
                                 HistogramSnapshot snapshot,
                                 boolean timer) {
        appendHelpAndType(output, emittedMetadata, descriptions, name, "histogram", meter);
        Set<String> reservedLabels = includeHistogramQuantiles ? HISTOGRAM_QUANTILE_LABELS : LE_LABEL;
        if (includeHistogramQuantiles) {
            appendQuantiles(output, name, meter, snapshot, timer, reservedLabels);
        }
        for (Bucket bucket : snapshot.histogramCounts()) {
            if (!Double.isFinite(bucket.boundary())) {
                continue;
            }
            String boundary = timer
                    ? Double.toString(bucket.boundary(TimeUnit.SECONDS))
                    : Double.toString(bucket.boundary());
            appendSample(output, name + "_bucket", meter, Map.of("le", boundary), bucket.count(), reservedLabels);
        }
        double sum = timer ? snapshot.total(TimeUnit.SECONDS) : snapshot.total();
        appendSample(output, name + "_bucket", meter, Map.of("le", "+Inf"), snapshot.count(), reservedLabels);
        appendSample(output, name + "_sum", meter, Map.of(), sum, reservedLabels);
        appendSample(output, name + "_count", meter, Map.of(), snapshot.count(), reservedLabels);
    }

    private void appendSummary(StringBuilder output,
                               Set<String> emittedMetadata,
                               Map<String, String> descriptions,
                               String name,
                               Meter meter,
                               HistogramSnapshot snapshot,
                               boolean timer) {
        appendHelpAndType(output, emittedMetadata, descriptions, name, "summary", meter);
        appendQuantiles(output, name, meter, snapshot, timer, QUANTILE_LABEL);
        double sum = timer ? snapshot.total(TimeUnit.SECONDS) : snapshot.total();
        appendSample(output, name + "_sum", meter, Map.of(), sum, QUANTILE_LABEL);
        appendSample(output, name + "_count", meter, Map.of(), snapshot.count(), QUANTILE_LABEL);
    }

    private void appendQuantiles(StringBuilder output,
                                String name,
                                Meter meter,
                                HistogramSnapshot snapshot,
                                boolean timer,
                                Set<String> reservedLabels) {
        StreamSupport.stream(snapshot.percentileValues().spliterator(), false)
                .sorted((first, second) -> Double.compare(first.percentile(), second.percentile()))
                .forEach(valueAtPercentile -> {
                    double value = timer ? valueAtPercentile.value(TimeUnit.SECONDS) : valueAtPercentile.value();
                    if (snapshot.count() == 0) {
                        // Preserve the established Micrometer exposition of empty distributions.
                        value = 0D;
                    }
                    appendSample(output,
                                 name,
                                 meter,
                                 Map.of("quantile", Double.toString(valueAtPercentile.percentile())),
                                 value,
                                 reservedLabels);
                });
    }

    private void appendHelpAndType(StringBuilder output,
                                   Set<String> emittedMetadata,
                                   Map<String, String> descriptions,
                                   String name,
                                   String type,
                                   Meter meter) {
        if (!emittedMetadata.add(name)) {
            return;
        }
        output.append("# HELP ")
                .append(name)
                .append(' ')
                .append(escapeHelp(descriptions.getOrDefault(name, meter.description().orElse(meter.id().name()))))
                .append('\n');
        output.append("# TYPE ")
                .append(name)
                .append(' ')
                .append(type)
                .append('\n');
    }

    private void appendSample(StringBuilder output,
                              String name,
                              Meter meter,
                              Map<String, String> extraTags,
                              double value) {
        appendSample(output, name, meter, extraTags, value, NO_RESERVED_LABELS);
    }

    private void appendSample(StringBuilder output,
                              String name,
                              Meter meter,
                              Map<String, String> extraTags,
                              long value,
                              Set<String> reservedLabels) {
        appendSample(output, name, meter, extraTags, Long.toString(value), reservedLabels);
    }

    private void appendSample(StringBuilder output,
                              String name,
                              Meter meter,
                              Map<String, String> extraTags,
                              double value,
                              Set<String> reservedLabels) {
        appendSample(output,
                     name,
                     meter,
                     extraTags,
                     value == Double.POSITIVE_INFINITY ? "+Inf"
                             : value == Double.NEGATIVE_INFINITY ? "-Inf" : Double.toString(value),
                     reservedLabels);
    }

    private void appendSample(StringBuilder output,
                              String name,
                              Meter meter,
                              Map<String, String> extraTags,
                              String value,
                              Set<String> reservedLabels) {
        output.append(name)
                .append(tags(meter, extraTags, reservedLabels))
                .append(' ')
                .append(value)
                .append('\n');
    }

    private static boolean hasBuckets(HistogramSnapshot snapshot) {
        return snapshot.histogramCounts().iterator().hasNext();
    }

    private String promName(Meter meter) {
        String name = normalizeNameToPrometheus(meter.id().name());
        if (meter instanceof Timer) {
            return name.endsWith("_seconds") ? name : name + "_seconds";
        }
        String withUnit = meter.baseUnit()
                .filter(unit -> !unit.isBlank())
                .map(HelidonPrometheusFormatter::normalizeNameToPrometheus)
                .map(unit -> name.endsWith("_" + unit) ? name : name + "_" + unit)
                .orElse(name);
        String withoutSuffixes = RESERVED_SUFFIX_PATTERN.matcher(withUnit).replaceAll("");
        if (!withoutSuffixes.isEmpty()) {
            return withoutSuffixes;
        }
        int nextSeparator = withUnit.indexOf('_', 1);
        return withUnit.substring(1, nextSeparator < 0 ? withUnit.length() : nextSeparator);
    }

    private String tags(Meter meter, Map<String, String> extraTags, Set<String> reservedLabels) {
        Map<String, String> labels = labels(meter, reservedLabels);
        extraTags.forEach((key, value) -> labels.put(normalizeLabelName(key), value));
        if (labels.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder("{");
        labels.forEach((key, value) -> result.append(key)
                .append("=\"")
                .append(escape(value))
                .append("\","));
        result.setCharAt(result.length() - 1, '}');
        return result.toString();
    }

    private Map<String, String> labels(Meter meter, Set<String> reservedLabels) {
        Map<String, String> labels = new LinkedHashMap<>();
        systemTagsManager
                .withoutSystemTags(meter.id().tags())
                .forEach(tag -> addTag(labels, tag, reservedLabels));
        systemTagsManager
                .displayTags()
                .forEach(tag -> addTag(labels, tag, reservedLabels));
        return labels;
    }

    private static void addTag(Map<String, String> labels, Tag tag, Set<String> reservedLabels) {
        String key = normalizeLabelName(tag.key());
        if (!reservedLabels.contains(key)) {
            labels.putIfAbsent(key, tag.value());
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\n").replace("\"", "\\\"");
    }

    private static String escapeHelp(String value) {
        return value.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\n");
    }

    private static boolean matches(MediaType a, MediaType b) {
        return a.type().equals(b.type()) && a.subtype().equals(b.subtype());
    }
}
