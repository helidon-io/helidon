/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates. All rights reserved.
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObjectBuilder;

import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.Tag;

/**
 * Base for our implementations of various metrics.
 */
abstract class MetricImpl implements HelidonMetric {
    static final JsonBuilderFactory JSON = Json.createBuilderFactory(Collections.emptyMap());

    private static final Pattern DOUBLE_UNDERSCORE = Pattern.compile("__");
    private static final Pattern COLON_UNDERSCORE = Pattern.compile(":_");
    private static final Pattern CAMEL_CASE = Pattern.compile("(.)(\\p{Upper})");
    private static final Map<String, Units> PROMETHEUS_CONVERTERS = new HashMap<>();
    private static final long KILOBITS = 1000 / 8;
    private static final long MEGABITS = 1000 * KILOBITS;
    private static final long GIGABITS = 1000 * MEGABITS;
    private static final long KIBIBITS = 1024 / 8;
    private static final long MEBIBITS = 1024 * KIBIBITS;
    private static final long GIBIBITS = 1024 * MEBIBITS;
    private static final long KILOBYTES = 1000;
    private static final long MEGABYTES = 1000 * KILOBYTES;
    private static final long GIGABYTES = 1000 * MEGABYTES;

    private static String bsls(String s) {
        return "\\\\" + s;
    }

    private static final Map<String, String> JSON_ESCAPED_CHARS_MAP = initEscapedCharsMap();

    private static final Pattern JSON_ESCAPED_CHARS_REGEX = Pattern
                .compile(JSON_ESCAPED_CHARS_MAP
                            .keySet()
                            .stream()
                            .map(Pattern::quote)
                            .collect(Collectors.joining("", "[", "]")));

    static {
        //see https://prometheus.io/docs/practices/naming/#base-units
        addTimeConverter(MetricUnits.NANOSECONDS, TimeUnit.NANOSECONDS);
        addTimeConverter(MetricUnits.MICROSECONDS, TimeUnit.MICROSECONDS);
        addTimeConverter(MetricUnits.MILLISECONDS, TimeUnit.MILLISECONDS);
        addTimeConverter(MetricUnits.SECONDS, TimeUnit.SECONDS);
        addTimeConverter(MetricUnits.MILLISECONDS, TimeUnit.MILLISECONDS);
        addTimeConverter(MetricUnits.MINUTES, TimeUnit.MINUTES);
        addTimeConverter(MetricUnits.HOURS, TimeUnit.HOURS);
        addTimeConverter(MetricUnits.DAYS, TimeUnit.DAYS);

        addConverter(new Units(MetricUnits.BITS, "bytes", o -> (double) o / 8));
        addByteConverter(MetricUnits.KILOBITS, KILOBITS);
        addByteConverter(MetricUnits.MEGABITS, MEGABITS);
        addByteConverter(MetricUnits.GIGABITS, GIGABITS);
        addByteConverter(MetricUnits.KIBIBITS, KIBIBITS);
        addByteConverter(MetricUnits.MEBIBITS, MEBIBITS);
        addByteConverter(MetricUnits.GIBIBITS, GIBIBITS);
        addByteConverter(MetricUnits.KILOBYTES, KILOBYTES);
        addByteConverter(MetricUnits.MEGABYTES, MEGABYTES);
        addByteConverter(MetricUnits.GIGABYTES, GIGABYTES);

        addConverter(new Units("fahrenheits", "celsius", o -> ((((Number) o).doubleValue() - 32) * 5) / 9));
        addConverter(new LengthUnits("millimeters", (double) 1 / 1000));
        addConverter(new LengthUnits("centimeters", (double) 1 / 100));
        addConverter(new LengthUnits("kilometers", 1000));
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

    private final String registryType;
    private final Metadata metadata;

    MetricImpl(String registryType, Metadata metadata) {
        this.metadata = metadata;
        this.registryType = registryType;
    }

    private static void addByteConverter(String metricUnit, long toByteRatio) {
        PROMETHEUS_CONVERTERS.put(metricUnit, new Units(metricUnit,
                                                        "bytes",
                                                        o -> ((Number) o).doubleValue() * toByteRatio));
    }

    private static void addConverter(Units units) {
        PROMETHEUS_CONVERTERS.put(units.getMetricUnit(), units);
    }

    private static void addTimeConverter(String metricUnit, TimeUnit timeUnit) {
        PROMETHEUS_CONVERTERS.put(metricUnit, new TimeUnits(metricUnit, timeUnit));
    }

    @Override
    public String getName() {
        return metadata.getName();
    }

    @Override
    public Metadata metadata() {
        return metadata;
    }

    @Override
    public void jsonMeta(JsonObjectBuilder builder, List<MetricID> metricIDs) {
        JsonObjectBuilder metaBuilder =
                new MetricsSupport.MergingJsonObjectBuilder(JSON.createObjectBuilder());

        addNonEmpty(metaBuilder, "unit", metadata.getUnit().orElse(null));
        addNonEmpty(metaBuilder, "type", metadata.getType());
        addNonEmpty(metaBuilder, "description", metadata.getDescription().orElse(null));
        addNonEmpty(metaBuilder, "displayName", metadata.getDisplayName());
        if (metricIDs != null) {
            for (MetricID metricID : metricIDs) {
                boolean tagAdded = false;
                JsonArrayBuilder ab = JSON.createArrayBuilder();
                for (Tag tag : metricID.getTagsAsList()) {
                    tagAdded = true;
                    ab.add(tagForJsonKey(tag));
                }
                if (tagAdded) {
                    metaBuilder.add("tags", ab);
                }
            }
        }
        builder.add(getName(), metaBuilder);
    }

    static String jsonFullKey(String baseName, MetricID metricID) {
        return metricID.getTags().isEmpty() ? baseName
                : String.format("%s;%s", baseName,
                        metricID.getTagsAsList().stream()
                                .map(MetricImpl::tagForJsonKey)
                                .collect(Collectors.joining(";")));
    }

    static String jsonFullKey(MetricID metricID) {
        return jsonFullKey(metricID.getName(), metricID);
    }

    private static String tagForJsonKey(Tag t) {
        return String.format("%s=%s", jsonEscape(t.getTagName()), jsonEscape(t.getTagValue()));
    }

    static String jsonEscape(String s) {
        final Matcher m = JSON_ESCAPED_CHARS_REGEX.matcher(s);
        final StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, JSON_ESCAPED_CHARS_MAP.get(m.group()));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    void prometheusType(StringBuilder sb, String nameWithUnits, String type) {
        sb.append("# TYPE ")
                .append(nameWithUnits)
                .append(" ")
                .append(type)
                .append('\n');
    }

    void prometheusHelp(StringBuilder sb, String nameWithUnits) {
        sb.append("# HELP ")
                .append(nameWithUnits)
                .append(" ")
                .append(metadata.getDescription().orElse(""))
                .append('\n');
    }

    @Override
    public void prometheusData(StringBuilder sb, MetricID metricID) {
        String nameWithUnits = prometheusNameWithUnits(metricID);
        prometheusType(sb, nameWithUnits, metadata.getType());
        prometheusHelp(sb, nameWithUnits);
        sb.append(nameWithUnits).append(prometheusTags(metricID.getTags())).append(" ").append(prometheusValue()).append('\n');
    }

    @Override
    public String prometheusNameWithUnits(MetricID metricID) {
        return prometheusNameWithUnits(metricID.getName(), getUnits().getPrometheusUnit());
    }

    public abstract String prometheusValue();

    protected final void prometheusQuantile(StringBuilder sb,
                                            String tags,
                                            Units units, String nameUnits,
                                            String quantile,
                                            Supplier<Double> value) {
        // application:file_sizes_bytes{quantile="0.5"} 4201
        String quantileTag = "quantile=\"" + quantile + "\"";
        if (tags.isEmpty()) {
            tags = "{" + quantileTag + "}";
        } else {
            tags = tags.substring(0, tags.length() - 1) + "," + quantileTag + "}";
        }

        sb.append(nameUnits)
                .append(tags)
                .append(" ")
                .append(units.convert(value.get()))
                .append("\n");
    }

    final String prometheusNameWithUnits(String name, Optional<String> unit) {
        return prometheusName(name) + unit.map((it) -> "_" + it).orElse("");
    }

    final String prometheusName(String name) {
        return prometheusClean(name, registryType + "_");
    }

    private String prometheusClean(String name, String prefix) {
        name = name.replaceAll("[^a-zA-Z0-9_]", "_");

        //Scope is always specified at the start of the metric name.
        //Scope and name are separated by underscore (_) as of
        // metrics 2.0 (OpenMetrics).
        name = prefix + name;

        String orig;
        do {
            orig = name;
            //Double underscore is translated to single underscore
            name = DOUBLE_UNDERSCORE.matcher(name).replaceAll("_");
        } while (!orig.equals(name));

        do {
            orig = name;
            //Colon-underscore (:_) is translated to single colon
            name = COLON_UNDERSCORE.matcher(name).replaceAll(":");
        } while (!orig.equals(name));

        return name;
    }
    final String prometheusTags(Map<String, String> tags) {
        return (tags == null || tags.isEmpty() ? "" : tags.entrySet().stream()
                .filter(entry -> entry.getKey() != null)
                .map(entry -> String.format("%s=\"%s\"",
                        prometheusClean(entry.getKey(), ""),
                        prometheusTagValue(entry.getValue())))
                .collect(Collectors.joining(",", "{", "}")));
    }

    private String prometheusTagValue(String value) {
        value = value.replace("\\", "\\\\");
        value = value.replace("\"", "\\\"");
        value = value.replace("\n", "\\n");
        return value;
    }

    String camelToSnake(String name) {
        return CAMEL_CASE.matcher(name).replaceAll("$1_$2").toLowerCase();
    }

    void addNonEmpty(JsonObjectBuilder builder, String name, String value) {
        if ((null != value) && !value.isEmpty()) {
            builder.add(name, value);
        }
    }

    // for Gauge and Histogram - must convert
    Units getUnits() {
        String unit = metadata.getUnit().get();
        if ((null == unit) || unit.isEmpty() || MetricUnits.NONE.equals(unit)) {
            return new Units(null);
        }

        Units units = PROMETHEUS_CONVERTERS.get(unit);
        if (null == units) {
            return new Units(unit, unit, o -> o);
        } else {
            return units;
        }
    }

    private static final class LengthUnits extends Units {
        private LengthUnits(String metricUnit, double ratio) {
            super(metricUnit, "meters", o -> ((Number) o).doubleValue() * ratio);
        }
    }

    static final class TimeUnits extends Units {
        private static final long MILLISECONDS = 1000;
        private static final long MICROSECONDS = 1000 * MILLISECONDS;
        private static final long NANOSECONDS = 1000 * MICROSECONDS;
        private static final String DOUBLE_NAN = String.valueOf(Double.NaN);

        // If object is NaN return string and avoid format exception in BigDecimal
        private static final BiFunction<Object, Function<Object, Object>, Object> CHECK_NANS =
                (o, f) -> o instanceof Double && ((Double) o).isNaN() ? DOUBLE_NAN : f.apply(o);

        private TimeUnits(String metricUnit, TimeUnit timeUnit) {
            super(metricUnit, "seconds", timeConverter(timeUnit));
        }

        static Function<Object, Object> timeConverter(TimeUnit from) {
            switch (from) {
                case NANOSECONDS:
                    return (o) -> CHECK_NANS.apply(o, p ->
                            String.valueOf(new BigDecimal(String.valueOf(p)).doubleValue() / NANOSECONDS));
                case MICROSECONDS:
                    return (o) -> CHECK_NANS.apply(o, p ->
                            String.valueOf(new BigDecimal(String.valueOf(o)).doubleValue() / MICROSECONDS));
                case MILLISECONDS:
                    return (o) -> CHECK_NANS.apply(o, p ->
                            String.valueOf(new BigDecimal(String.valueOf(o)).doubleValue() / MILLISECONDS));
                case SECONDS:
                    return (o) -> CHECK_NANS.apply(o, String::valueOf);
                default:
                    return (o) -> CHECK_NANS.apply(o, p ->
                            String.valueOf(TimeUnit.SECONDS.convert(new BigDecimal(String.valueOf(o)).longValue(), from)));
            }
        }
    }

    static class Units {
        private final String metricUnit;
        private final String prometheusUnit;
        private final Function<Object, Object> converter;

        Units(String unit) {
            this.metricUnit = unit;
            this.prometheusUnit = unit;
            this.converter = o -> o;
        }

        private Units(String metricUnit, String prometheusUnit, Function<Object, Object> converter) {
            this.metricUnit = metricUnit;
            this.prometheusUnit = prometheusUnit;
            this.converter = converter;
        }

        String getMetricUnit() {
            return metricUnit;
        }

        Optional<String> getPrometheusUnit() {
            return Optional.ofNullable(prometheusUnit);
        }

        public Object convert(Object value) {
            Object apply = converter.apply(value);
            if (apply instanceof Double) {
                // if this is an integer value, return it as a long (so we do not see the decimal dot in output)
                double num = (Double) apply;
                if (Math.floor(num) == num) {
                    return (long) num;
                }
            }
            return apply;
        }
    }

}
