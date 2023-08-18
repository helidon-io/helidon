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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.DistributionSummary;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MeterRegistryFormatter;
import io.helidon.metrics.api.Registry;
import io.helidon.metrics.api.RegistryFactory;
import io.helidon.metrics.api.Timer;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

/**
 * JSON formatter for a meter registry (independent of the underlying registry implementation).
 */
class JsonFormatter implements MeterRegistryFormatter {

    /**
     * Returns a new builder for a formatter.
     *
     * @return new builder
     */
    static JsonFormatter.Builder builder(MeterRegistry meterRegistry) {
        return new Builder(meterRegistry);
    }

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
    private final MeterRegistry meterRegistry;

    private JsonFormatter(Builder builder) {
        meterNameSelection = builder.meterNameSelection;
        scopeSelection = builder.scopeSelection;
        scopeTagName = builder.scopeTagName;
        meterRegistry = builder.meterRegistry;
    }

    /**
     * Returns a JSON object conveying all the meter identification and data (but no metadata), organized by scope.
     *
     * @return meter data
     */
    @Override
    public Optional<JsonObject> format() {

        boolean organizeByScope = shouldOrganizeByScope();

        Map<String, Map<String, MetricOutputBuilder>> meterOutputBuildersByScope = organizeByScope ? new HashMap<>() : null;
        Map<String, MetricOutputBuilder> meterOutputBuildersIgnoringScope = organizeByScope ? null : new HashMap<>();

    /*
     * If we organize by multiple scopes, then meterOutputBuildersByScope will have one top-level entry per scope we find,
     * keyed by the scope name. We will gather the output for the metrics in each scope under a JSON node for that scope.
     *
     * If the scope selection accepts only one scope, or if we are NOT organizing by scopes, then we don't use that map and
     * instead use meterOutputBuildersIgnoringScope to gather all JSON for the meters under the same parent.
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


        var scopes = new HashSet<>();
        scopeSelection.forEach(scopes::add);

        var names = new HashSet<>();
        meterNameSelection.forEach(names::add);

        Predicate<String> namePredicate = names.isEmpty() ? n -> true : names::contains;

        AtomicBoolean isAnyOutput = new AtomicBoolean(false);

        meterRegistry.scopes().forEach(scope -> {
            String matchingScope = matchingScope(scope);
            if (matchingScope != null) {
                meterRegistry.meters().forEach(meter -> {
                    if (meterRegistry.isMeterEnabled(meter.id()) && namePredicate.test(meter.id().name())) {
                        if (matchesName(meter.id().name())) {

                            Map<String, MetricOutputBuilder> meterOutputBuildersWithinParent =
                                    organizeByScope ? meterOutputBuildersByScope
                                            .computeIfAbsent(matchingScope,
                                                             ms -> new HashMap<>())
                                            : meterOutputBuildersIgnoringScope;

                            // Find the output builder for the key relevant to this meter and then add this meter's contribution
                            // to the output.
                            MetricOutputBuilder metricOutputBuilder = meterOutputBuildersWithinParent
                                    .computeIfAbsent(metricOutputKey(meter),
                                                     k -> MetricOutputBuilder.create(meter));
                            metricOutputBuilder.add(meter);
                            isAnyOutput.set(true);

                        }
                    }
                });
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
    public Optional<JsonObject> formatMetadata() {

        boolean organizeByScope = shouldOrganizeByScope();

        Map<String, Map<String, JsonObjectBuilder>> metadataOutputBuildersByScope = organizeByScope ? new HashMap<>() : null;
        Map<String, JsonObjectBuilder> metadataOutputBuildersIgnoringScope = organizeByScope ? null : new HashMap<>();

        AtomicBoolean isAnyOutput = new AtomicBoolean(false);
        RegistryFactory registryFactory = RegistryFactory.getInstance();
        registryFactory.scopes().forEach(scope -> {
            String matchingScope = matchingScope(scope);
            if (matchingScope != null) {
                Registry registry = registryFactory.getRegistry(scope);
                registry.getMetadata().forEach((name, metadata) -> {
                    if (matchesName(name)) {

                        Map<String, JsonObjectBuilder> metadataOutputBuilderWithinParent =
                                organizeByScope ? metadataOutputBuildersByScope
                                        .computeIfAbsent(matchingScope, ms -> new HashMap<>())
                                        : metadataOutputBuildersIgnoringScope;

                        JsonObjectBuilder builderForThisName = metadataOutputBuilderWithinParent
                                .computeIfAbsent(name, k -> JSON.createObjectBuilder());
                        addNonEmpty(builderForThisName, "unit", metadata.getUnit());
                        addNonEmpty(builderForThisName, "description", metadata.getDescription());
                        isAnyOutput.set(true);

                        List<List<String>> tagGroups = new ArrayList<>();

                        registry.metricIdsByName(name).forEach(metricId -> {
                            if (registry.enabled(name)) {
                                List<String> tags = metricId.getTags().entrySet().stream()
                                        .map(entry -> jsonEscape(entry.getKey()) + "=" + jsonEscape(entry.getValue()))
                                        .toList();
                                if (!tags.isEmpty()) {
                                    tagGroups.add(tags);
                                }
                            }
                        });
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

    static String jsonEscape(String s) {
        final Matcher m = JSON_ESCAPED_CHARS_REGEX.matcher(s);
        final StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, JSON_ESCAPED_CHARS_MAP.get(m.group()));
        }
        m.appendTail(sb);
        return sb.toString();
    }


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

    private boolean shouldOrganizeByScope() {
        var it = scopeSelection.iterator();
        if (it.hasNext()) {
            it.next();
            // return false if exactly one selection; true if at least two.
            return it.hasNext();
        }
        return true;
    }

    private static String metricOutputKey(Meter meter) {
        return meter instanceof Counter || meter instanceof io.helidon.metrics.api.Gauge
                ? flatNameAndTags(meter.id())
                : structureName(meter.id());
    }

    private static String flatNameAndTags(Meter.Id meterId) {
        StringJoiner sj = new StringJoiner(";");
        sj.add(meterId.name());
        meterId.tags().forEach(tag -> sj.add(tag.key() + "=" + tag.value()));
        return sj.toString();
    }

    private static String structureName(Meter.Id meterId) {
        return meterId.name();
    }

    private String matchingScope(String scope) {
        Iterator<String> scopeIterator = scopeSelection.iterator();
        if (!scopeIterator.hasNext()) {
            return scope;
        }
        if (scope == null) {
            return null;
        }

        while (scopeIterator.hasNext()) {
            if (scopeIterator.next().equals(scope)) {
                return scope;
            }
        }
        return null;
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

        private static MetricOutputBuilder create(Meter meter) {
            return meter instanceof Counter
                    || meter instanceof io.helidon.metrics.api.Gauge
                    ? new Flat(meter)
                    : new Structured(meter);
        }



        private final Meter meter;

        protected MetricOutputBuilder(Meter meter) {
            this.meter = meter;
        }

        protected Meter meter() {
            return meter;
        }

        protected abstract void add(Meter meter);
        protected abstract void apply(JsonObjectBuilder builder);

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
                    builder.add(nameWithTags, gauge.value());
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

                    if (meter() instanceof DistributionSummary summary) {
                        DistributionSummary typedChild = (DistributionSummary) child;
                        sameNameBuilder.add(valueId("count", childID), typedChild.count());
                        sameNameBuilder.add(valueId("max", childID), typedChild.snapshot().max());
                        sameNameBuilder.add(valueId("mean", childID), typedChild.snapshot().mean());
                        sameNameBuilder.add(valueId("total", childID), typedChild.totalAmount());
                    } else if (meter() instanceof Timer timer) {
                        Timer typedChild = (Timer) child;
                        sameNameBuilder.add(valueId("count", childID), typedChild.count());
                        sameNameBuilder.add(valueId("elapsedTime", childID), typedChild.totalTime(TimeUnit.SECONDS));
                        sameNameBuilder.add(valueId("max", childID), typedChild.max(TimeUnit.SECONDS));
                        sameNameBuilder.add(valueId("mean", childID), typedChild.mean(TimeUnit.SECONDS));
                    } else {
                        throw new IllegalArgumentException("Unrecognized meter type "
                                                                   + meter().getClass().getName());
                    }
                });
                builder.add(meterId.name(), sameNameBuilder);
            }


            private static String valueId(String valueName, Meter.Id meterId) {
                return valueName + tagsPortion(meterId);
            }

            private static String tagsPortion(Meter.Id metricID) {
                StringJoiner sj = new StringJoiner(";", ";", "");
                sj.setEmptyValue("");
                metricID.tags().forEach(tag -> sj.add(tag.key() + "=" + tag.value()));
                return sj.toString();
            }
        }
    }

    static class Builder implements io.helidon.common.Builder<Builder, JsonFormatter> {

        private final MeterRegistry meterRegistry;
        private Iterable<String> meterNameSelection = Set.of();
        private String scopeTagName;
        private Iterable<String> scopeSelection = Set.of();

        /**
         * Used only internally.
         */
        private Builder(MeterRegistry meterRegistry) {
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
