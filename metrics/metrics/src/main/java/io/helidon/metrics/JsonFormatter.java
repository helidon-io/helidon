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
package io.helidon.metrics;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAccumulator;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.concurrent.atomic.LongAdder;

import io.helidon.metrics.api.MetricInstance;
import io.helidon.metrics.api.Registry;
import io.helidon.metrics.api.RegistryFactory;
import io.helidon.metrics.api.SystemTagsManager;

import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.Timer;

/**
 * JSON formatter for a Micrometer meter registry.
 */
class JsonFormatter {

    /**
     * Returns a new builder for a formatter.
     *
     * @return new builder
     */
    static JsonFormatter.Builder builder() {
        return new Builder();
    }

    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Map.of());

    private final Iterable<String> meterNameSelection;
    private final Iterable<String> scopeSelection;
    private final String scopeTagName;

    private JsonFormatter(Builder builder) {
        meterNameSelection = builder.meterNameSelection;
        scopeSelection = builder.scopeSelection;
        scopeTagName = builder.scopeTagName;
    }

    /**
     * Returns a JSON object conveying all the meter identification and data (but no metadata), organized by scope.
     *
     * @return meter data
     */
    public JsonObject data(boolean isByScopeRequested) {

        boolean organizeByScope = shouldOrganizeByScope(isByScopeRequested);

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

        RegistryFactory registryFactory = RegistryFactory.getInstance();
        registryFactory.scopes().forEach(scope -> {
            String matchingScope = matchingScope(scope);
            if (matchingScope != null) {
                Registry registry = registryFactory.getRegistry(scope);
                registry.stream().forEach(metric -> {
                    if (registry.enabled(metric.id().getName())) {
                        MetricInstance adjustedMetric = new MetricInstance(new MetricID(metric.id().getName(),
                                                                                        tags(metric.id().getTags(), scope)),
                                                                           metric.metric());
                        if (matchesName(adjustedMetric.id())) {

                            Map<String, MetricOutputBuilder> meterOutputBuildersWithinParent =
                                    organizeByScope ? meterOutputBuildersByScope
                                            .computeIfAbsent(matchingScope,
                                                             ms -> new HashMap<>())
                                            : meterOutputBuildersIgnoringScope;

                            // Find the output builder for the key relevant to this meter and then add this meter's contribution
                            // to the output.
                            MetricOutputBuilder metricOutputBuilder = meterOutputBuildersWithinParent
                                    .computeIfAbsent(metricOutputKey(adjustedMetric),
                                                     k -> MetricOutputBuilder.create(adjustedMetric));
                            metricOutputBuilder.add(adjustedMetric);

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
        return top.build();
    }

    private static Tag[] tags(Map<String, String> tagMap, String scope) {
        List<Tag> result = new ArrayList<>();
        SystemTagsManager.instance()
                .allTags(tagMap.entrySet())
                .forEach(entry -> result.add(new Tag(entry.getKey(), entry.getValue())));
        return result.toArray(new Tag[0]);
    }


    private boolean shouldOrganizeByScope(boolean isByScope) {
        if (isByScope) {
            var it = scopeSelection.iterator();
            if (it.hasNext()) {
                it.next();
                // return false if exactly one selection; true if at least two.
                return it.hasNext();
            }
        }
        return isByScope;
    }

//    private static String meterOutputKey(Meter meter) {
//        return meter instanceof Counter || meter instanceof Gauge
//            ? flatNameAndTags(meter.getId())
//            : structureName(meter.getId());
//    }

    private static String metricOutputKey(MetricInstance metric) {
        return metric.metric() instanceof org.eclipse.microprofile.metrics.Counter
                || metric.metric() instanceof org.eclipse.microprofile.metrics.Gauge<?>
                ? flatNameAndTags(metric.id())
                : structureName(metric.id());
    }

//    private static String flatNameAndTags(Meter.Id meterId) {
//        StringJoiner sj = new StringJoiner(";");
//        sj.add(meterId.getName());
//        meterId.getTagsAsIterable()
//                .forEach(t -> sj.add(t.getKey() + "=" + t.getValue()));
//        return sj.toString();
//    }

    private static String flatNameAndTags(MetricID metricID) {
        StringJoiner sj = new StringJoiner(";");
        sj.add(metricID.getName());
        metricID.getTags().forEach((k, v) -> sj.add(k + "=" + v));
        return sj.toString();
    }

//    private static String structureName(Meter.Id meterId) {
//        return meterId.getName();
//    }
//
    private static String structureName(MetricID metricID) {
        return metricID.getName();
    }

//    private String matchingScope(Meter.Id meterId) {
//        String scope = scope(meterId);
//
//        Iterator<String> scopeIterator = scopeSelection.iterator();
//        if (!scopeIterator.hasNext()) {
//            return scope;
//        }
//        if (scope == null) {
//            return null;
//        }
//
//        while (scopeIterator.hasNext()) {
//            if (scopeIterator.next().equals(scope)) {
//                return scope;
//            }
//        }
//        return null;
//    }

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

//    private String matchingScope(MetricID metricId) {
//        String scope = scope(metricId);
//
//        Iterator<String> scopeIterator = scopeSelection.iterator();
//        if (!scopeIterator.hasNext()) {
//            return scope;
//        }
//        if (scope == null) {
//            return null;
//        }
//
//        while (scopeIterator.hasNext()) {
//            if (scopeIterator.next().equals(scope)) {
//                return scope;
//            }
//        }
//        return null;
//    }

//    private String scope(Meter.Id meterId) {
//        return meterId.getTag(scopeTagName);
//    }

    private String scope(MetricID metricId) {
        return metricId.getTags().get(scopeTagName);
    }

//    private boolean matchesName(Meter.Id meterId) {
//        Iterator<String> nameIterator = meterNameSelection.iterator();
//        if (!nameIterator.hasNext()) {
//            return true;
//        }
//        while (nameIterator.hasNext()) {
//            if (nameIterator.next().equals(meterId.getName())) {
//                return true;
//            }
//        }
//        return false;
//    }

    private boolean matchesName(MetricID metricId) {
        Iterator<String> nameIterator = meterNameSelection.iterator();
        if (!nameIterator.hasNext()) {
            return true;
        }
        while (nameIterator.hasNext()) {
            if (nameIterator.next().equals(metricId.getName())) {
                return true;
            }
        }
        return false;
    }

//    private abstract static class MeterOutputBuilder {
//
//        private static MeterOutputBuilder create(Meter meter) {
//            return meter instanceof Counter || meter instanceof Gauge
//                    ? new Flat(meter)
//                    : new Structured(meter);
//        }
//
//        private final Meter meter;
//
//        protected MeterOutputBuilder(Meter meter) {
//            this.meter = meter;
//        }
//
//        protected Meter meter() {
//            return meter;
//        }
//
//        protected abstract void add(Meter meter);
//        protected abstract void apply(JsonObjectBuilder builder);
//
//        private static class Flat extends MeterOutputBuilder {
//
//            private Flat(Meter meter) {
//                super(meter);
//            }
//
//            @Override
//            protected void apply(JsonObjectBuilder builder) {
//                if (meter() instanceof Counter counter) {
//                    builder.add(flatNameAndTags(counter.getId()), counter.count());
//                    return;
//                }
//                if (meter() instanceof Gauge gauge) {
//                    builder.add(flatNameAndTags(gauge.getId()), gauge.value());
//                    return;
//                }
//                throw new IllegalArgumentException("Attempt to format meter with structured data as flat JSON "
//                                                           + meter().getClass().getName());
//            }
//
//            @Override
//            protected void add(Meter meter) {
//            }
//        }
//
//        private static class Structured extends MeterOutputBuilder {
//
//            private final List<Meter> children = new ArrayList<>();
//            private final JsonObjectBuilder sameNameBuilder = JSON.createObjectBuilder();
//
//            Structured(Meter meter) {
//                super(meter);
//            }
//
//            @Override
//            protected void add(Meter meter) {
//                if (!meter().getClass().isInstance(meter)) {
//                    throw new IllegalArgumentException("Attempt to add metric of type " + meter.getClass().getName()
//                    + " to existing output for a meter of type " + meter().getClass().getName());
//                }
//                children.add(meter);
//            }
//
//            @Override
//            protected void apply(JsonObjectBuilder builder) {
//                Meter.Id meterId = meter().getId();
//                children.forEach(child -> {
//                    Meter.Id childId = child.getId();
//                    if (meter() instanceof DistributionSummary distributionSummary) {
//                        sameNameBuilder.add(valueId("count", childId), distributionSummary.count());
//                        sameNameBuilder.add(valueId("max", childId), distributionSummary.max());
//                        sameNameBuilder.add(valueId("mean", childId), distributionSummary.mean());
//                        sameNameBuilder.add(valueId("total", childId), distributionSummary.totalAmount());
//                    } else if (meter() instanceof Timer timer) {
//                        sameNameBuilder.add(valueId("count", childId), timer.count());
//                        sameNameBuilder.add(valueId("elapsedTime", childId), timer.totalTime(TimeUnit.SECONDS));
//                        sameNameBuilder.add(valueId("max", childId), timer.max(TimeUnit.SECONDS));
//                        sameNameBuilder.add(valueId("mean", childId), timer.mean(TimeUnit.SECONDS));
//                    } else {
//                        throw new IllegalArgumentException("Unrecognized meter type " + meter().getClass().getName());
//                    }
//                });
//                builder.add(meterId.getName(), sameNameBuilder);
//            }
//
//
//            private static String valueId(String valueName, Meter.Id meterId) {
//                return valueName + tagsPortion(meterId);
//            }
//
//            private static String tagsPortion(Meter.Id meterId) {
//                StringJoiner sj = new StringJoiner(";", ";", "");
//                sj.setEmptyValue("");
//                meterId.getTagsAsIterable().forEach(tag -> sj.add(tag.getKey() + "=" + tag.getValue()));
//                return sj.toString();
//            }
//        }
//    }


    private abstract static class MetricOutputBuilder {

                private static MetricOutputBuilder create(MetricInstance metric) {
                    return metric.metric() instanceof org.eclipse.microprofile.metrics.Counter
                            || metric.metric() instanceof org.eclipse.microprofile.metrics.Gauge<?>
                            ? new Flat(metric)
                            : new Structured(metric);
                }

                private final MetricInstance metric;

                protected MetricOutputBuilder(MetricInstance metric) {
                    this.metric = metric;
                }

                protected MetricInstance metric() {
                    return metric;
                }

                protected abstract void add(MetricInstance metric);
                protected abstract void apply(JsonObjectBuilder builder);

                private static class Flat extends MetricOutputBuilder {

                    private Flat(MetricInstance metric) {
                        super(metric);
                    }

                    @Override
                    protected void apply(JsonObjectBuilder builder) {
                        if (metric().metric() instanceof Counter counter) {
                            builder.add(flatNameAndTags(metric().id()), counter.getCount());
                            return;
                        }
                        if (metric().metric() instanceof Gauge<?> gauge) {
                            Number value = gauge.getValue();
                            String nameWithTags = flatNameAndTags(metric().id());
                            if (value instanceof AtomicInteger) {
                                builder.add(nameWithTags, value.doubleValue());
                            } else if (value instanceof AtomicLong) {
                                builder.add(nameWithTags, value.longValue());
                            } else if (value instanceof BigDecimal) {
                                builder.add(nameWithTags, (BigDecimal) value);
                            } else if (value instanceof BigInteger) {
                                builder.add(nameWithTags, (BigInteger) value);
                            } else if (value instanceof Byte) {
                                builder.add(nameWithTags, value.intValue());
                            } else if (value instanceof Double) {
                                builder.add(nameWithTags, (Double) value);
                            } else if (value instanceof DoubleAccumulator) {
                                builder.add(nameWithTags, value.doubleValue());
                            } else if (value instanceof DoubleAdder) {
                                builder.add(nameWithTags, value.doubleValue());
                            } else if (value instanceof Float) {
                                builder.add(nameWithTags, value.floatValue());
                            } else if (value instanceof Integer) {
                                builder.add(nameWithTags, (Integer) value);
                            } else if (value instanceof Long) {
                                builder.add(nameWithTags, (Long) value);
                            } else if (value instanceof LongAccumulator) {
                                builder.add(nameWithTags, value.longValue());
                            } else if (value instanceof LongAdder) {
                                builder.add(nameWithTags, value.longValue());
                            } else if (value instanceof Short) {
                                builder.add(nameWithTags, value.intValue());
                            } else {
                                // Might be a developer-provided class which extends Number.
                                builder.add(nameWithTags, value.doubleValue());
                            }
                            return;
                        }
                        throw new IllegalArgumentException("Attempt to format meter with structured data as flat JSON "
                                                                   + metric().metric().getClass().getName());
                    }

                    @Override
                    protected void add(MetricInstance metric) {
                    }
                }

                private static class Structured extends MetricOutputBuilder {

                    private final List<MetricInstance> children = new ArrayList<>();
                    private final JsonObjectBuilder sameNameBuilder = JSON.createObjectBuilder();

                    Structured(MetricInstance metric) {
                        super(metric);
                    }

                    @Override
                    protected void add(MetricInstance metric) {
                        if (!metric().metric().getClass().isInstance(metric.metric())) {
                            throw new IllegalArgumentException("Attempt to add metric of type " + metric.getClass().getName()
                            + " to existing output for a meter of type " + metric().metric().getClass().getName());
                        }
                        children.add(metric);
                    }

                    @Override
                    protected void apply(JsonObjectBuilder builder) {
                        MetricID metricID = metric().id();
                        children.forEach(child -> {
                            MetricID childID = child.id();

                            if (metric().metric() instanceof Histogram histogram) {
                                Histogram typedChild = (Histogram) child.metric();
                                sameNameBuilder.add(valueId("count", childID), typedChild.getCount());
                                sameNameBuilder.add(valueId("max", childID), typedChild.getSnapshot().getMax());
                                sameNameBuilder.add(valueId("mean", childID), typedChild.getSnapshot().getMean());
                                sameNameBuilder.add(valueId("total", childID), typedChild.getSum());
                            } else if (metric().metric() instanceof Timer timer) {
                                Timer typedChild = (Timer) child.metric();
                                sameNameBuilder.add(valueId("count", childID), typedChild.getCount());
                                sameNameBuilder.add(valueId("elapsedTime", childID), typedChild.getElapsedTime().toSeconds());
                                sameNameBuilder.add(valueId("max", childID), typedChild.getSnapshot().getMax());
                                sameNameBuilder.add(valueId("mean", childID), typedChild.getSnapshot().getMean());
                            } else {
                                throw new IllegalArgumentException("Unrecognized meter type "
                                                                           + metric().metric().getClass().getName());
                            }
                        });
                        builder.add(metricID.getName(), sameNameBuilder);
                    }


                    private static String valueId(String valueName, MetricID metricID) {
                        return valueName + tagsPortion(metricID);
                    }

                    private static String tagsPortion(MetricID metricID) {
                        StringJoiner sj = new StringJoiner(";", ";", "");
                        sj.setEmptyValue("");
                        metricID.getTags().forEach((k, v) -> sj.add(k + "=" + v));
                        return sj.toString();
                    }
                }
            }

    static class Builder implements io.helidon.common.Builder<Builder, JsonFormatter> {

        private Iterable<String> meterNameSelection = Set.of();
        private String scopeTagName;
        private Iterable<String> scopeSelection = Set.of();

        /**
         * Used only internally.
         */
        private Builder() {
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
