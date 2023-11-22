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
package io.helidon.webserver.observe.metrics;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAccumulator;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.concurrent.atomic.LongAdder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.DistributionSummary;
import io.helidon.metrics.api.FunctionalCounter;
import io.helidon.metrics.api.Gauge;
import io.helidon.metrics.api.HistogramSnapshot;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MeterRegistryFormatter;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.SystemTagsManager;
import io.helidon.metrics.api.Timer;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObjectBuilder;

/**
 * JSON formatter for a meter registry (independent of the underlying registry implementation).
 */
class JsonFormatter implements MeterRegistryFormatter {

    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Map.of());
    private static final Map<String, String> JSON_ESCAPED_CHARS_MAP = initEscapedCharsMap();
    private static final Pattern JSON_ESCAPED_CHARS_REGEX = Pattern
            .compile(JSON_ESCAPED_CHARS_MAP
                             .keySet()
                             .stream()
                             .map(Pattern::quote)
                             .collect(Collectors.joining("", "[", "]")));
    private final Iterable<String> meterNameSelection;
    private final Iterable<String> scopeSelection;
    private final String scopeTagName;
    private final MetricsConfig metricsConfig;
    private final MeterRegistry meterRegistry;

    private JsonFormatter(Builder builder) {
        meterNameSelection = builder.meterNameSelection;
        scopeSelection = builder.scopeSelection;
        scopeTagName = builder.scopeTagName;
        metricsConfig = builder.metricsConfig;
        meterRegistry = builder.meterRegistry;
    }

    /**
     * Returns a new builder for a formatter.
     *
     * @return new builder
     */
    static JsonFormatter.Builder builder(MetricsConfig metricsConfig, MeterRegistry meterRegistry) {
        return new Builder(metricsConfig, meterRegistry);
    }

    static String jsonEscape(String s) {
        final Matcher m = JSON_ESCAPED_CHARS_REGEX.matcher(s);
        final StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, JSON_ESCAPED_CHARS_MAP.get(m.group()));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Returns a JSON object conveying all the meter identification and data (but no metadata), organized by scope.
     *
     * @return meter data
     */
    @Override
    public Optional<Object> format() {

        boolean organizeByScope = shouldOrganizeByScope();

        Map<String, Map<String, MetricOutputBuilder>> meterOutputBuildersByScope = organizeByScope ? new HashMap<>() : null;
        Map<String, MetricOutputBuilder> meterOutputBuildersIgnoringScope = organizeByScope ? null : new HashMap<>();

    /*
     * If we organize by multiple scopes, then meterOutputBuildersByScope will have one top-level entry per scope we find,
     * keyed by the scope name. We will gather the output for the metrics in each scope under a JSON node for that scope.
     *
     * On the other hand, if the scope selection accepts only one scope, or if we are NOT organizing by scopes, then we don't
     *  use that map and instead use meterOutputBuildersIgnoringScope to gather all JSON for the meters under the same parent.
     *
     * The JSON output format has one "flat" entry for each single-valued meter--counter or gauge--with the JSON
     * key set to the name and tags from the meter ID and the JSON value reporting the single value.
     *
     * In contrast, the JSON output has a "structured" entry for each multi-valued meter--distribution summary or timer.
     * The JSON node key is the name only--no tags--from the meter ID, and the corresponding JSON structure has a child
     *  for each distinct value-name plus tags group.
     *
     * Here is an example:
     *
         {
         "carsCounter;car=suv;colour=red": 0,
         "carsCounter;car=sedan;colour=blue": 0,
         "carsTimer": {
            "count;colour=red": 0,
            "sum;colour=red": 0.0,
            "max;colour=red": 0.0,
            "count;colour=blue": 0,
            "sum;colour=blue": 0.0,
            "max;colour=blue": 0.0
         }
        }
     */

        AtomicBoolean isAnyOutput = new AtomicBoolean(false);

        // Process each meter which matches the scope selection, adding it to the output map entry for its scope if the meter is
        // enabled and it matches any name selection.

        meterRegistry.meters(scopeSelection).forEach(meter -> {
            String name = meter.id().name();
            if (meterRegistry.isMeterEnabled(name, meter.id().tagsMap(), meter.scope())
                    && matchesName(name)) {

                Map<String, MetricOutputBuilder> meterOutputBuilderMapToUpdate =
                        organizeByScope ? meterOutputBuildersByScope
                                .computeIfAbsent(meter.scope().orElse(""),
                                                 ms -> new HashMap<>())
                                : meterOutputBuildersIgnoringScope;

                // Find the output builder for the key relevant to this meter and then
                // add this meter's contribution to the output.
                MetricOutputBuilder metricOutputBuilder = meterOutputBuilderMapToUpdate
                        .computeIfAbsent(metricOutputKey(meter),
                                         k -> MetricOutputBuilder.create(meter));
                metricOutputBuilder.add(meter);
                isAnyOutput.set(true);
            }
        });

        JsonObjectBuilder top = JSON.createObjectBuilder();
        if (organizeByScope) {
            meterOutputBuildersByScope.forEach((scope, outputBuilders) -> {
                JsonObjectBuilder scopeBuilder = JSON.createObjectBuilder();
                outputBuilders.forEach((key, outputBuilder) -> outputBuilder.apply(scopeBuilder));
                top.add(scope, scopeBuilder);
            });
        } else {
            meterOutputBuildersIgnoringScope.forEach((key, outputBuilder) -> outputBuilder.apply(top));
        }

        return isAnyOutput.get() ? Optional.of(top.build()) : Optional.empty();
    }

    @Override
    public Optional<Object> formatMetadata() {

        boolean organizeByScope = shouldOrganizeByScope();

        Map<String, Map<String, JsonObjectBuilder>> metadataOutputBuildersByScope = organizeByScope ? new HashMap<>() : null;
        Map<String, JsonObjectBuilder> metadataOutputBuildersIgnoringScope = organizeByScope ? null : new HashMap<>();

        AtomicBoolean isAnyOutput = new AtomicBoolean(false);

        meterRegistry.meters(scopeSelection).forEach(meter -> {
            String name = meter.id().name();
            if (meterRegistry.isMeterEnabled(name, meter.id().tagsMap(), meter.scope())
                    && matchesName(name)) {

                Map<String, JsonObjectBuilder> metadataOutputBuilderWithinParent =
                        organizeByScope ? metadataOutputBuildersByScope
                                .computeIfAbsent(meter.scope().orElse(""),
                                                 ms -> new HashMap<>())
                                : metadataOutputBuildersIgnoringScope;

                JsonObjectBuilder builderForThisName = metadataOutputBuilderWithinParent
                        .computeIfAbsent(name, k -> JSON.createObjectBuilder());
                addNonEmpty(builderForThisName, "type", meter.type().typeName());
                meter.baseUnit().ifPresent(u -> addNonEmpty(builderForThisName, "unit", u));
                meter.description().ifPresent(d -> addNonEmpty(builderForThisName, "description", d));
                isAnyOutput.set(true);

                List<List<String>> tagGroups = new ArrayList<>();

                List<String> tags = StreamSupport.stream(SystemTagsManager.instance()
                                                                 .withoutSystemOrScopeTags(meter.id().tags())
                                                                 .spliterator(), false)
                        .map(tag -> jsonEscape(tag.key()) + "=" + jsonEscape(tag.value()))
                        .toList();
                if (!tags.isEmpty()) {
                    tagGroups.add(tags);
                }
                if (!tagGroups.isEmpty()) {
                    JsonArrayBuilder tagsOverAllMetricsWithSameName = JSON.createArrayBuilder();
                    for (List<String> tagGroup : tagGroups) {
                        JsonArrayBuilder tagsForMetricBuilder = JSON.createArrayBuilder();
                        tagGroup.forEach(tagsForMetricBuilder::add);
                        tagsOverAllMetricsWithSameName.add(tagsForMetricBuilder);
                    }
                    builderForThisName.add("tags", tagsOverAllMetricsWithSameName);
                    isAnyOutput.set(true);
                }
            }
        });
        JsonObjectBuilder top = JSON.createObjectBuilder();
        if (organizeByScope) {
            metadataOutputBuildersByScope.forEach((scope, builders) -> {
                JsonObjectBuilder scopeBuilder = JSON.createObjectBuilder();
                builders.forEach(scopeBuilder::add);
                top.add(scope, scopeBuilder);
            });
        } else {
            metadataOutputBuildersIgnoringScope.forEach(top::add);
        }
        return isAnyOutput.get() ? Optional.of(top.build()) : Optional.empty();
    }

    /**
     * Creates a map from escape characters to quoted escape-characters so escape characters in tag names and values can
     * be escaped before sending to JSON for processing.
     *
     * @return prepared map
     */
    private static Map<String, String> initEscapedCharsMap() {
        final Map<String, String> result = new HashMap<>();
        result.put("\b", bsls("b"));
        result.put("\f", bsls("f"));
        result.put("\n", bsls("n"));
        result.put("\r", bsls("r"));
        result.put("\t", bsls("t"));
        result.put("\"", bsls("\""));
        result.put("\\", bsls("\\\\"));
        result.put(";", "_");
        return result;
    }

    private static String bsls(String s) {
        return "\\\\" + s;
    }

    private static void addNonEmpty(JsonObjectBuilder builder, String name, String value) {
        if ((null != value) && !value.isEmpty()) {
            builder.add(name, value);
        }
    }

    private static String metricOutputKey(Meter meter) {
        return meter instanceof Counter || meter instanceof io.helidon.metrics.api.Gauge
                ? flatNameAndTags(meter.id())
                : structureName(meter.id());
    }

    private static String flatNameAndTags(Meter.Id meterId) {
        StringJoiner sj = new StringJoiner(";");
        sj.add(meterId.name());
        SystemTagsManager.instance()
                .withoutSystemOrScopeTags(meterId.tags())
                .forEach(tag -> sj.add(tag.key() + "=" + tag.value()));
        return sj.toString();
    }

    private static String structureName(Meter.Id meterId) {
        return meterId.name();
    }

    private boolean shouldOrganizeByScope() {
        var it = scopeSelection.iterator();
        if (it.hasNext()) {
            it.next();
            // return false if exactly one selection; true if at least two.
            return it.hasNext();
        }
        return true;
    }

    private Iterable<String> scopesToReport() {

        Set<String> selection = new HashSet<>();
        scopeSelection.forEach(selection::add);

        List<String> scopesToReport = new ArrayList<>();

        if (!selection.isEmpty()) {
            meterRegistry.scopes().forEach(candidateScope -> {
                if (selection.contains(candidateScope)) {
                    scopesToReport.add(candidateScope);
                }
            });
        }
        return scopesToReport;
    }

    private boolean matchesName(String metricName) {
        Iterator<String> nameIterator = meterNameSelection.iterator();
        if (!nameIterator.hasNext()) {
            return true;
        }
        while (nameIterator.hasNext()) {
            if (nameIterator.next().equals(metricName)) {
                return true;
            }
        }
        return false;
    }

    private abstract static class MetricOutputBuilder {

        private final Meter meter;

        protected MetricOutputBuilder(Meter meter) {
            this.meter = meter;
        }

        protected Meter meter() {
            return meter;
        }

        protected abstract void add(Meter meter);

        protected abstract void apply(JsonObjectBuilder builder);

        private static MetricOutputBuilder create(Meter meter) {
            return meter instanceof Counter
                    || meter instanceof io.helidon.metrics.api.Gauge
                    || meter instanceof FunctionalCounter
                    ? new Flat(meter)
                    : new Structured(meter);
        }

        private static void addNarrowed(JsonObjectBuilder builder, String nameWithTags, Number number) {
            if (number instanceof AtomicInteger v) {
                builder.add(nameWithTags, v.intValue());
            } else if (number instanceof AtomicLong v) {
                builder.add(nameWithTags, v.longValue());
            } else if (number instanceof BigDecimal v) {
                builder.add(nameWithTags, v);
            } else if (number instanceof BigInteger v) {
                builder.add(nameWithTags, v);
            } else if (number instanceof Byte v) {
                builder.add(nameWithTags, v);
            } else if (number instanceof Double v) {
                builder.add(nameWithTags, v);
            } else if (number instanceof DoubleAccumulator v) {
                builder.add(nameWithTags, v.doubleValue());
            } else if (number instanceof DoubleAdder v) {
                builder.add(nameWithTags, v.doubleValue());
            } else if (number instanceof Float v) {
                builder.add(nameWithTags, v);
            } else if (number instanceof Integer v) {
                builder.add(nameWithTags, v);
            } else if (number instanceof Long v) {
                builder.add(nameWithTags, v);
            } else if (number instanceof LongAccumulator v) {
                builder.add(nameWithTags, v.longValue());
            } else if (number instanceof LongAdder v) {
                builder.add(nameWithTags, v.longValue());
            } else if (number instanceof Short v) {
                builder.add(nameWithTags, v);
            }
        }

        private static class Flat extends MetricOutputBuilder {

            private Flat(Meter meter) {
                super(meter);
            }

            @Override
            protected void apply(JsonObjectBuilder builder) {
                if (meter() instanceof Counter counter) {
                    builder.add(flatNameAndTags(meter().id()), counter.count());
                    return;
                }
                if (meter() instanceof io.helidon.metrics.api.Gauge gauge) {

                    String nameWithTags = flatNameAndTags(meter().id());
                    addNarrowed(builder, nameWithTags, gauge.value());
                    return;
                }
                if (meter() instanceof FunctionalCounter fCounter) {
                    builder.add(flatNameAndTags(meter().id()), fCounter.count());
                    return;
                }
                throw new IllegalArgumentException("Attempt to format meter with structured data as flat JSON "
                                                           + meter().getClass().getName());
            }

            @Override
            protected void add(Meter meter) {
            }
        }

        private static class Structured extends MetricOutputBuilder {

            private final List<Meter> children = new ArrayList<>();
            private final JsonObjectBuilder sameNameBuilder = JSON.createObjectBuilder();

            Structured(Meter meter) {
                super(meter);
            }

            @Override
            protected void add(Meter meter) {
                if (!meter().getClass().isInstance(meter)) {
                    throw new IllegalArgumentException(
                            String.format("Attempt to add meter of type %s to existing output for a meter of type %s",
                                          meter.getClass().getName(),
                                          meter().getClass().getName()));
                }
                children.add(meter);
            }

            @Override
            protected void apply(JsonObjectBuilder builder) {
                Meter.Id meterId = meter().id();
                children.forEach(child -> {
                    Meter.Id childID = child.id();

                    if (child instanceof Counter typedChild) {
                        sameNameBuilder.add(valueId("count", childID), typedChild.count());
                    } else if (child instanceof DistributionSummary typedChild) {
                        sameNameBuilder.add(valueId("count", childID), typedChild.count());
                        sameNameBuilder.add(valueId("max", childID), typedChild.max());
                        sameNameBuilder.add(valueId("mean", childID), typedChild.mean());
                        sameNameBuilder.add(valueId("total", childID), typedChild.totalAmount());
                        addDetails(typedChild.snapshot(), childID, null);
                    } else if (child instanceof Timer typedChild) {
                        sameNameBuilder.add(valueId("count", childID), typedChild.count());
                        sameNameBuilder.add(valueId("max", childID), typedChild.max(TimeUnit.SECONDS));
                        sameNameBuilder.add(valueId("mean", childID), typedChild.mean(TimeUnit.SECONDS));
                        sameNameBuilder.add(valueId("elapsedTime", childID), typedChild.totalTime(TimeUnit.SECONDS));
                        addDetails(typedChild.snapshot(), childID, timeUnit(typedChild));
                    } else if (child instanceof FunctionalCounter typedChild) {
                        sameNameBuilder.add(valueId("count", childID), typedChild.count());
                    } else if (child instanceof Gauge typedChild) {
                        MetricOutputBuilder.addNarrowed(sameNameBuilder, valueId("value", childID), typedChild.value());
                    } else {
                        throw new IllegalArgumentException("Unrecognized meter type "
                                                                   + meter().getClass().getName());
                    }
                });
                builder.add(meterId.name(), sameNameBuilder);
            }

            private void addDetails(HistogramSnapshot snapshot, Meter.Id childId, TimeUnit timeUnit) {
                snapshot.percentileValues().forEach(vap ->
                                                            sameNameBuilder
                                                                    .add(valueId(percentileName(vap.percentile()),
                                                                                 childId),
                                                                         timeUnit != null
                                                                                 ? vap.value(timeUnit)
                                                                                 : vap.value()));
                snapshot.histogramCounts().forEach(bucket ->
                                                           sameNameBuilder
                                                                   .add(valueId(bucketName(timeUnit != null
                                                                                                   ? bucket.boundary(timeUnit)
                                                                                                   : bucket.boundary()),
                                                                                childId),
                                                                        bucket.count()));
            }

            private static String percentileName(double percentile) {
                return "p" + percentile;
            }

            private static String bucketName(double boundary) {
                return "b" + boundary;
            }

            private static TimeUnit timeUnit(Timer timer) {
                if (timer.baseUnit().isPresent()) {
                    try {
                        return TimeUnit.valueOf(timer.baseUnit().get().toUpperCase(Locale.getDefault()));
                    } catch (IllegalArgumentException ex) {
                        return TimeUnit.SECONDS;
                    }
                }
                return TimeUnit.SECONDS;
            }

            private static String valueId(String valueName, Meter.Id meterId) {
                return valueName + tagsPortion(meterId);
            }

            private static String tagsPortion(Meter.Id metricID) {
                StringJoiner sj = new StringJoiner(";", ";", "");
                sj.setEmptyValue("");
                SystemTagsManager.instance()
                        .withoutSystemOrScopeTags(metricID.tags())
                        .forEach(tag -> sj.add(tag.key() + "=" + tag.value()));
                return sj.toString();
            }
        }

    }

    static class Builder implements io.helidon.common.Builder<Builder, JsonFormatter> {

        private final MetricsConfig metricsConfig;
        private final MeterRegistry meterRegistry;
        private Iterable<String> meterNameSelection = Set.of();
        private String scopeTagName;
        private Iterable<String> scopeSelection = Set.of();

        /**
         * Used only internally.
         */
        private Builder(MetricsConfig metricsConfig, MeterRegistry meterRegistry) {
            this.metricsConfig = metricsConfig;
            this.meterRegistry = meterRegistry;
        }

        @Override
        public JsonFormatter build() {
            return new JsonFormatter(this);
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
    }
}
