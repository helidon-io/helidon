/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.metrics.serviceapi;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAccumulator;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.helidon.metrics.api.HelidonMetric;
import io.helidon.metrics.api.MetricInstance;
import io.helidon.metrics.api.Registry;
import io.helidon.metrics.api.SystemTagsManager;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import org.eclipse.microprofile.metrics.ConcurrentGauge;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Meter;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.SimpleTimer;
import org.eclipse.microprofile.metrics.Snapshot;
import org.eclipse.microprofile.metrics.Timer;

/**
 * Support for creating MicroProfile JSON responses for metrics endpoints.
 */
public final class JsonFormat {
    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Map.of());
    private static final Map<String, String> JSON_ESCAPED_CHARS_MAP = initEscapedCharsMap();

    private static final Pattern JSON_ESCAPED_CHARS_REGEX = Pattern
            .compile(JSON_ESCAPED_CHARS_MAP
                             .keySet()
                             .stream()
                             .map(Pattern::quote)
                             .collect(Collectors.joining("", "[", "]")));

    private JsonFormat() {
    }

    /**
     * Create JSON metric response for specified registries.
     *
     * @param registries registries to use
     * @return JSON with data of metrics
     */
    public static JsonObject jsonData(Registry... registries) {
        return toJson(JsonFormat::toJsonData, registries);
    }

    /**
     * JSON for a single registry.
     *
     * @param registry registry
     * @return JSON with data of a single registry
     */
    public static JsonObject jsonData(Registry registry) {
        return toJson((builder, entry) -> jsonData(builder, entry.id(), entry.metric()),
                      registry);
    }

    /**
     * Create JSON metric response for specified metric in a specified registry.
     *
     * @param registry   registry
     * @param metricName metric name
     * @return JSON with data of the metric
     */
    public static JsonObject jsonDataByName(Registry registry, String metricName) {
        JsonObjectBuilder builder = new MergingJsonObjectBuilder(JSON.createObjectBuilder());
        for (MetricInstance metricEntry : registry.list(metricName)) {
            HelidonMetric metric = metricEntry.metric();
            if (registry.enabled(metricName)) {
                jsonData(builder, metricEntry.id(), metric);
            }
        }
        return builder.build();
    }

    /**
     * Update JSON metric metadata response for specified metric and its ids.
     *
     * @param builder       JSON builder to update
     * @param helidonMetric metric instance
     * @param metricIds     metric IDs
     */
    public static void jsonMeta(JsonObjectBuilder builder, HelidonMetric helidonMetric, List<MetricID> metricIds) {
        JsonObjectBuilder metaBuilder =
                new MergingJsonObjectBuilder(JSON.createObjectBuilder());

        Metadata metadata = helidonMetric.metadata();
        addNonEmpty(metaBuilder, "unit", metadata.getUnit());
        addNonEmpty(metaBuilder, "type", metadata.getType());
        addNonEmpty(metaBuilder, "description", metadata.getDescription());
        addNonEmpty(metaBuilder, "displayName", metadata.getDisplayName());
        if (metricIds != null) {
            for (MetricID metricID : metricIds) {
                boolean tagAdded = false;
                JsonArrayBuilder ab = JSON.createArrayBuilder();
                for (Map.Entry<String, String> tag : SystemTagsManager.instance().allTags(metricID)) {
                    tagAdded = true;
                    ab.add(tagForJsonKey(tag));
                }
                if (tagAdded) {
                    metaBuilder.add("tags", ab);
                }
            }
        }
        builder.add(metadata.getName(), metaBuilder);
    }

    /**
     * Create JSON metric metadata response for specified registries.
     *
     * @param registries registries to use
     * @return JSON with all metadata
     */
    public static JsonObject jsonMeta(Registry... registries) {
        return toJson(JsonFormat::jsonMeta, registries);
    }

    /**
     * Create JSON metric metadata response for a single registry.
     *
     * @param registry registry to use
     * @return JSON with all metadata for metrics in the specified registry
     */
    public static JsonObject jsonMeta(Registry registry) {
        return toJson((builder, entry) -> {
            MetricID metricID = entry.id();
            HelidonMetric metric = entry.metric();
            List<MetricID> sameNamedIDs = registry.metricIdsByName(metricID.getName());
            jsonMeta(builder, metric, sameNamedIDs);
        }, registry);
    }

    @SuppressWarnings("unchecked")
    private static void jsonData(JsonObjectBuilder builder, MetricID key, HelidonMetric value) {
        switch (value.metadata().getTypeRaw()) {
        case CONCURRENT_GAUGE -> concurrentGauge(builder, key, (ConcurrentGauge) value);
        case COUNTER -> counter(builder, key, (Counter) value);
        case GAUGE -> gauge(builder, key, (Gauge<? extends Number>) value);
        case METERED -> meter(builder, key, (Meter) value);
        case HISTOGRAM -> histogram(builder, key, (Histogram) value);
        case TIMER -> timer(builder, key, value, (Timer) value);
        case SIMPLE_TIMER -> simpleTimer(builder, key, value, (SimpleTimer) value);
        case INVALID -> throw new IllegalArgumentException("Invalid metric encountered: " + key);
        default -> throw new IllegalArgumentException("Invalid metric type encountered: " + value.metadata().getTypeRaw()
                                                              + ", key" + key);
        }
    }

    private static String jsonFullKey(MetricID metricID) {
        return jsonFullKey(metricID.getName(), metricID);
    }

    private static long conversionFactor(HelidonMetric helidonMetric) {
        String unit = helidonMetric.metadata().getUnit();
        if (unit == null || unit.isEmpty() || MetricUnits.NONE.equals(unit)) {
            return 1;
        }
        return switch (unit) {
            case MetricUnits.MICROSECONDS -> 1000L;
            case MetricUnits.MILLISECONDS -> 1000L * 1000;
            case MetricUnits.SECONDS -> 1000L * 1000 * 1000;
            case MetricUnits.MINUTES -> 1000L * 1000 * 1000 * 60;
            case MetricUnits.HOURS -> 1000L * 1000 * 1000 * 60 * 60;
            case MetricUnits.DAYS -> 1000L * 1000 * 1000 * 60 * 60 * 24;
            default -> 1;
        };
    }

    private static JsonObject toJsonData(Registry registry) {
        return toJson(
                (builder, entry) -> jsonData(builder, entry.id(), entry.metric()),
                registry);
    }

    private static void simpleTimer(JsonObjectBuilder builder,
                                    MetricID metricID,
                                    HelidonMetric helidonMetric,
                                    SimpleTimer value) {
        long divisor = conversionFactor(helidonMetric);
        JsonObjectBuilder myBuilder = JSON.createObjectBuilder()
                .add(jsonFullKey("count", metricID), value.getCount())
                .add(jsonFullKey("elapsedTime", metricID), jsonDuration(value.getElapsedTime(), divisor))
                .add(jsonFullKey("maxTimeDuration", metricID), jsonDuration(value.getMaxTimeDuration(), divisor))
                .add(jsonFullKey("minTimeDuration", metricID), jsonDuration(value.getMinTimeDuration(), divisor));
        builder.add(metricID.getName(), myBuilder);
    }

    private static JsonValue jsonDuration(Duration duration, long conversionFactor) {
        if (duration == null) {
            return JsonObject.NULL;
        }
        double result = ((double) duration.toNanos()) / conversionFactor;
        return Json.createValue(result);
    }

    private static void timer(JsonObjectBuilder builder, MetricID metricID, HelidonMetric helidonMetric, Timer value) {
        Snapshot snapshot = value.getSnapshot();
        // Convert snapshot output according to units.
        long divisor = conversionFactor(helidonMetric);
        JsonObjectBuilder myBuilder = JSON.createObjectBuilder()
                .add(jsonFullKey("count", metricID), value.getCount())
                .add(jsonFullKey("elapsedTime", metricID), jsonDuration(value.getElapsedTime(), divisor))
                .add(jsonFullKey("meanRate", metricID), value.getMeanRate())
                .add(jsonFullKey("oneMinRate", metricID), value.getOneMinuteRate())
                .add(jsonFullKey("fiveMinRate", metricID), value.getFiveMinuteRate())
                .add(jsonFullKey("fifteenMinRate", metricID), value.getFifteenMinuteRate())
                .add(jsonFullKey("min", metricID), snapshot.getMin() / divisor)
                .add(jsonFullKey("max", metricID), snapshot.getMax() / divisor)
                .add(jsonFullKey("mean", metricID), snapshot.getMean() / divisor)
                .add(jsonFullKey("stddev", metricID), snapshot.getStdDev() / divisor)
                .add(jsonFullKey("p50", metricID), snapshot.getMedian() / divisor)
                .add(jsonFullKey("p75", metricID), snapshot.get75thPercentile() / divisor)
                .add(jsonFullKey("p95", metricID), snapshot.get95thPercentile() / divisor)
                .add(jsonFullKey("p98", metricID), snapshot.get98thPercentile() / divisor)
                .add(jsonFullKey("p99", metricID), snapshot.get99thPercentile() / divisor)
                .add(jsonFullKey("p999", metricID), snapshot.get999thPercentile() / divisor);

        builder.add(metricID.getName(), myBuilder);
    }

    private static void histogram(JsonObjectBuilder builder, MetricID metricId, Histogram value) {
        JsonObjectBuilder myBuilder = JSON.createObjectBuilder()
                .add(jsonFullKey("count", metricId), value.getCount())
                .add(jsonFullKey("sum", metricId), value.getSum());
        Snapshot snapshot = value.getSnapshot();
        myBuilder = myBuilder.add(jsonFullKey("min", metricId), snapshot.getMin())
                .add(jsonFullKey("max", metricId), snapshot.getMax())
                .add(jsonFullKey("mean", metricId), snapshot.getMean())
                .add(jsonFullKey("stddev", metricId), snapshot.getStdDev())
                .add(jsonFullKey("p50", metricId), snapshot.getMedian())
                .add(jsonFullKey("p75", metricId), snapshot.get75thPercentile())
                .add(jsonFullKey("p95", metricId), snapshot.get95thPercentile())
                .add(jsonFullKey("p98", metricId), snapshot.get98thPercentile())
                .add(jsonFullKey("p99", metricId), snapshot.get99thPercentile())
                .add(jsonFullKey("p999", metricId), snapshot.get999thPercentile());

        builder.add(metricId.getName(), myBuilder);
    }

    private static void meter(JsonObjectBuilder builder, MetricID metricId, Meter value) {
        /*
        From spec:
        {
         "requests": {
         "count": 29382,
         "meanRate": 12.223,
         "oneMinRate": 12.563,
         "fiveMinRate": 12.364,
         "fifteenMinRate": 12.126,
         }
        }
        */
        JsonObjectBuilder myBuilder = JSON.createObjectBuilder()
                .add(jsonFullKey("count", metricId), value.getCount())
                .add(jsonFullKey("meanRate", metricId), value.getMeanRate())
                .add(jsonFullKey("oneMinRate", metricId), value.getOneMinuteRate())
                .add(jsonFullKey("fiveMinRate", metricId), value.getFiveMinuteRate())
                .add(jsonFullKey("fifteenMinRate", metricId), value.getFifteenMinuteRate());

        builder.add(metricId.getName(), myBuilder);
    }

    private static void gauge(JsonObjectBuilder builder, MetricID metricId, Gauge<? extends Number> gauge) {
        Number value = gauge.getValue();
        String nameWithTags = jsonFullKey(metricId);

        if (value instanceof AtomicInteger it) {
            builder.add(nameWithTags, it.longValue());
        } else if (value instanceof AtomicLong it) {
            builder.add(nameWithTags, it.longValue());
        } else if (value instanceof BigDecimal it) {
            builder.add(nameWithTags, it);
        } else if (value instanceof BigInteger it) {
            builder.add(nameWithTags, it);
        } else if (value instanceof Byte it) {
            builder.add(nameWithTags, it.intValue());
        } else if (value instanceof Double it) {
            builder.add(nameWithTags, it);
        } else if (value instanceof DoubleAccumulator it) {
            builder.add(nameWithTags, it.doubleValue());
        } else if (value instanceof DoubleAdder it) {
            builder.add(nameWithTags, it.doubleValue());
        } else if (value instanceof Float it) {
            builder.add(nameWithTags, it);
        } else if (value instanceof Integer it) {
            builder.add(nameWithTags, it);
        } else if (value instanceof Long it) {
            builder.add(nameWithTags, it);
        } else if (value instanceof LongAccumulator it) {
            builder.add(nameWithTags, it.longValue());
        } else if (value instanceof LongAdder it) {
            builder.add(nameWithTags, it.longValue());
        } else if (value instanceof Short it) {
            builder.add(nameWithTags, it.intValue());
        } else {
            // Might be a developer-provided class which extends Number.
            builder.add(nameWithTags, value.doubleValue());
        }
    }

    private static void counter(JsonObjectBuilder builder, MetricID metricId, Counter value) {
        builder.add(jsonFullKey(metricId), value.getCount());
    }

    private static void concurrentGauge(JsonObjectBuilder builder, MetricID metricId, ConcurrentGauge value) {
        JsonObjectBuilder myBuilder = JSON.createObjectBuilder()
                .add(jsonFullKey("current", metricId), value.getCount())
                .add(jsonFullKey("max", metricId), value.getMax())
                .add(jsonFullKey("min", metricId), value.getMin());
        builder.add(metricId.getName(), myBuilder);
    }

    private static String jsonEscape(String s) {
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

    private static String jsonFullKey(String baseName, MetricID metricID) {
        return baseName + tagsToJsonFormat(SystemTagsManager.instance().allTags(metricID));
    }

    private static String tagsToJsonFormat(Iterable<Map.Entry<String, String>> it) {
        StringJoiner sj = new StringJoiner(";", ";", "").setEmptyValue("");
        it.forEach(entry -> sj.add(tagForJsonKey(entry)));
        return sj.toString();
    }

    private static String tagForJsonKey(Map.Entry<String, String> tagEntry) {
        return String.format("%s=%s", jsonEscape(tagEntry.getKey()), jsonEscape(tagEntry.getValue()));
    }

    private static void addNonEmpty(JsonObjectBuilder builder, String name, String value) {
        if ((null != value) && !value.isEmpty()) {
            builder.add(name, value);
        }
    }

    private static JsonObject toJson(
            BiConsumer<JsonObjectBuilder, MetricInstance> accumulator,
            Registry registry) {

        return registry.stream()
                .sorted(Comparator.comparing(MetricInstance::id))
                .collect(() -> new MergingJsonObjectBuilder(JSON.createObjectBuilder()),
                         accumulator,
                         JsonObjectBuilder::addAll
                )
                .build();
    }

    private static JsonObject toJson(Function<Registry, JsonObject> fn, Registry... registries) {
        return Arrays.stream(registries)
                .filter(r -> !r.empty())
                .collect(JSON::createObjectBuilder,
                         (builder, registry) -> accumulateJson(builder, registry, fn),
                         JsonObjectBuilder::addAll)
                .build();
    }

    private static void accumulateJson(JsonObjectBuilder builder, Registry registry,
                                       Function<Registry, JsonObject> fn) {
        builder.add(registry.type(), fn.apply(registry));
    }

    /**
     * A {@code JsonObjectBuilder} that aggregates, rather than overwrites, when
     * the caller adds objects or arrays with the same name.
     * <p>
     * This builder is tuned to the needs of reporting metrics metadata. Metrics
     * which share the same name but have different tags and have multiple
     * values (called samples) need to appear in the data output as one
     * object with the common name. The name of each sample in the output is
     * decorated with the tags for the sample's parent metric. For example:
     * <p>
     * <pre><code>
     * "carsMeter": {
     * "count;colour=red" : 0,
     * "meanRate;colour=red" : 0,
     * "oneMinRate;colour=red" : 0,
     * "fiveMinRate;colour=red" : 0,
     * "fifteenMinRate;colour=red" : 0,
     * "count;colour=blue" : 0,
     * "meanRate;colour=blue" : 0,
     * "oneMinRate;colour=blue" : 0,
     * "fiveMinRate;colour=blue" : 0,
     * "fifteenMinRate;colour=blue" : 0
     * }
     * </code></pre>
     * <p>
     * The metadata output (as opposed to the data output) must collect tag
     * information from actual instances of the metric under the overall metadata
     * object. This example reflects two instances of the {@code barVal} gauge
     * which have tags of "store" and "component."
     * <pre><code>
     * "barVal": {
     * "unit": "megabytes",
     * "type": "gauge",
     * "tags": [
     *   [
     *     "store=webshop",
     *     "component=backend"
     *   ],
     *   [
     *     "store=webshop",
     *     "component=frontend"
     *   ]
     * ]
     * }
     * </code></pre>
     */
    static final class MergingJsonObjectBuilder implements JsonObjectBuilder {

        private final JsonObjectBuilder delegate;

        private final Map<String, List<JsonObject>> subValuesMap = new HashMap<>();
        private final Map<String, List<JsonArray>> subArraysMap = new HashMap<>();

        MergingJsonObjectBuilder(JsonObjectBuilder delegate) {
            this.delegate = delegate;
        }

        @Override
        public JsonObjectBuilder add(String arg0, JsonValue arg1) {
            delegate.add(arg0, arg1);
            return this;
        }

        @Override
        public JsonObjectBuilder add(String arg0, String arg1) {
            delegate.add(arg0, arg1);
            return this;
        }

        @Override
        public JsonObjectBuilder add(String arg0, BigInteger arg1) {
            delegate.add(arg0, arg1);
            return this;
        }

        @Override
        public JsonObjectBuilder add(String arg0, BigDecimal arg1) {
            delegate.add(arg0, arg1);
            return this;
        }

        @Override
        public JsonObjectBuilder add(String arg0, int arg1) {
            delegate.add(arg0, arg1);
            return this;
        }

        @Override
        public JsonObjectBuilder add(String arg0, long arg1) {
            delegate.add(arg0, arg1);
            return this;
        }

        @Override
        public JsonObjectBuilder add(String arg0, double arg1) {
            if (Double.isNaN(arg1)) {
                delegate.add(arg0, String.valueOf(Double.NaN));
            } else {
                delegate.add(arg0, arg1);
            }
            return this;
        }

        @Override
        public JsonObjectBuilder add(String arg0, boolean arg1) {
            delegate.add(arg0, arg1);
            return this;
        }

        @Override
        public JsonObjectBuilder addNull(String arg0) {
            delegate.addNull(arg0);
            return this;
        }

        @Override
        public JsonObjectBuilder add(String name, JsonObjectBuilder subBuilder) {
            JsonObject ob = subBuilder.build();
            delegate.add(name, JSON.createObjectBuilder(ob));
            List<JsonObject> subValues;
            if (subValuesMap.containsKey(name)) {
                subValues = subValuesMap.get(name);
            } else {
                subValues = new ArrayList<>();
                subValuesMap.put(name, subValues);
            }
            subValues.add(ob);
            return this;
        }

        @Override
        public JsonObjectBuilder add(String name, JsonArrayBuilder arrayBuilder) {
            JsonArray array = arrayBuilder.build();
            delegate.add(name, JSON.createArrayBuilder(array));
            List<JsonArray> subArrays;
            if (subArraysMap.containsKey(name)) {
                subArrays = subArraysMap.get(name);
            } else {
                subArrays = new ArrayList<>();
                subArraysMap.put(name, subArrays);
            }
            subArrays.add(array);
            return this;
        }

        @Override
        public JsonObjectBuilder addAll(JsonObjectBuilder builder) {
            delegate.addAll(builder);
            return this;
        }

        @Override
        public JsonObjectBuilder remove(String name) {
            delegate.remove(name);
            return this;
        }

        @Override
        public JsonObject build() {
            JsonObject beforeMerging = delegate.build();
            if (subValuesMap.isEmpty() && subArraysMap.isEmpty()) {
                return beforeMerging;
            }
            JsonObjectBuilder mainBuilder = JSON.createObjectBuilder(beforeMerging);
            subValuesMap.forEach((key, value) -> {
                JsonObjectBuilder metricBuilder = JSON.createObjectBuilder();
                for (JsonObject subObject : value) {
                    JsonObjectBuilder subBuilder = JSON.createObjectBuilder(subObject);
                    metricBuilder.addAll(subBuilder);
                }
                mainBuilder.add(key, metricBuilder);
            });

            subArraysMap.forEach((key, value) -> {
                JsonArrayBuilder arrayBuilder = JSON.createArrayBuilder();
                for (JsonArray subArray : value) {
                    JsonArrayBuilder subArrayBuilder = JSON.createArrayBuilder(subArray);
                    arrayBuilder.add(subArrayBuilder);
                }
                mainBuilder.add(key, arrayBuilder);
            });

            return mainBuilder.build();
        }

        @Override
        public String toString() {
            return delegate.toString();
        }
    }
}
