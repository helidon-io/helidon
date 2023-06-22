/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.helidon.metrics.api.HelidonMetric;
import io.helidon.metrics.api.LabeledSnapshot;
import io.helidon.metrics.api.MetricInstance;
import io.helidon.metrics.api.Registry;
import io.helidon.metrics.api.Sample;
import io.helidon.metrics.api.SampledMetric;
import io.helidon.metrics.api.SnapshotMetric;
import io.helidon.metrics.api.SystemTagsManager;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.Timer;

import static java.lang.System.Logger.Level.WARNING;

/**
 * Support for creating Prometheus responses for metrics endpoints.
 */
public final class PrometheusFormat {

    record PercentileOutput(String promText, String jText, double value) {}

    static final List<PercentileOutput> DEFAULT_PERCENTILES =
            List.of(new PercentileOutput("0.5", "p50", 0.5),
                    new PercentileOutput("0.75", "p75", 0.75),
                    new PercentileOutput("0.95", "p95", 0.95),
                    new PercentileOutput("0.98", "p98", 0.98),
                    new PercentileOutput("0.99", "p99", 0.99),
                    new PercentileOutput("0.999", "p999", 0.999));
    private static final System.Logger LOGGER = System.getLogger(PrometheusFormat.class.getName());

    private static final Pattern DOUBLE_UNDERSCORE = Pattern.compile("__");
    private static final Pattern COLON_UNDERSCORE = Pattern.compile(":_");
    private static final Map<String, Units> PROMETHEUS_CONVERTERS = new HashMap<>();

    private static final int EXEMPLAR_MAX_LENGTH = 128;

    private static final long KILOBITS = 1000 / 8;
    private static final long MEGABITS = 1000 * KILOBITS;
    private static final long GIGABITS = 1000 * MEGABITS;
    private static final long KIBIBITS = 1024 / 8;
    private static final long MEBIBITS = 1024 * KIBIBITS;
    private static final long GIBIBITS = 1024 * MEBIBITS;
    private static final long KILOBYTES = 1000;
    private static final long MEGABYTES = 1000 * KILOBYTES;
    private static final long GIGABYTES = 1000 * MEGABYTES;

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

        addConverter(new Units(MetricUnits.BITS, "bytes", o -> ((Number) o).doubleValue() / 8));
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

    private PrometheusFormat() {
    }

    /**
     * Create Prometheus metric response for specified metric in a specified registry.
     *
     * @param registry   registry
     * @param metricName metric name
     * @return data of the metric
     */
    public static String prometheusDataByName(Registry registry, String metricName) {
        StringBuilder sb = new StringBuilder();
        boolean isFirst = true;
        for (MetricInstance metricEntry : registry.list(metricName)) {
            HelidonMetric metric = metricEntry.metric();
            if (registry.enabled(metricName)) {
                prometheusData(sb, metricEntry.id(), metric, isFirst);
            }
            isFirst = false;
        }
        return sb.toString();
    }

    /**
     * Create Prometheus metric response for specified registries.
     *
     * @param registries registries to use
     * @return data of metrics
     */

    public static String prometheusData(Registry... registries) {
        return Arrays.stream(registries).filter(r -> !r.empty()).map(PrometheusFormat::prometheusData)
                .collect(Collectors.joining());
    }

    /**
     * Create Prometheus metric response for a specific metric instance.
     *
     * @param metricId     metric ID
     * @param value        metric instance
     * @param withHelpType whether to add help information
     * @return data of metric
     */
    public static String prometheusData(MetricID metricId, HelidonMetric value, boolean withHelpType) {
        StringBuilder sb = new StringBuilder();
        prometheusData(sb, metricId, value, withHelpType);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void prometheusData(StringBuilder sb, MetricID key, HelidonMetric value, boolean withHelpType) {
        Metadata metadata = value.metadata();
        if (value instanceof Counter counter) {
            counter(sb, key, metadata, value, counter, withHelpType);
        } else if (value instanceof Gauge<?> gauge) {
            gauge(sb, key, metadata, value, gauge, withHelpType);
        } else if (value instanceof Histogram histogram) {
            histogram(sb, key, metadata, value, histogram, withHelpType);
        } else if (value instanceof Timer timer) {
            timer(sb, key, metadata, value, timer, withHelpType);
        } else {
            throw new IllegalArgumentException("Unexpected metric type " + value.getClass().getName() + " encountered: " + key);
        }
    }

    private static String nameWithUnits(String registryType, Metadata metadata, MetricID metricID) {
        return nameWithUnits(registryType, metricID.getName(), units(metadata));
    }

    private static String nameWithUnits(String registryType, String name, Units units) {
        return prometheusName(registryType, name) + units.getPrometheusUnit().map((it) -> "_" + it).orElse("");
    }

    private static String prometheusName(String registryType, String name) {
        return prometheusClean(name, registryType + "_");
    }

    private static String tags(Map<String, String> tags) {
        StringJoiner sj = new StringJoiner(",", "{", "}").setEmptyValue("");
        SystemTagsManager.instance().allTags(tags).forEach(entry -> {
            if (entry.getKey() != null) {
                sj.add(String.format("%s=\"%s\"", prometheusClean(entry.getKey(), ""), prometheusTagValue(entry.getValue())));
            }
        });
        return sj.toString();
    }

    private static String prometheusData(Registry registry) {
        StringBuilder builder = new StringBuilder();
        Set<String> serialized = new HashSet<>();
        registry.stream().sorted(Comparator.comparing(MetricInstance::id)).forEach(entry -> {
            String name = entry.id().getName();
            if (!serialized.contains(name)) {
                prometheusData(builder, entry.id(), entry.metric(), true);
                serialized.add(name);
            } else {
                prometheusData(builder, entry.id(), entry.metric(), false);
            }
        });
        return builder.toString();
    }

    private static void timer(StringBuilder sb,
                              MetricID metricId,
                              Metadata metadata,
                              HelidonMetric helidonMetric,
                              Timer value,
                              boolean withHelpType) {
        if (!(helidonMetric instanceof SnapshotMetric snapshotable)) {
            return;
        }
        // In Prometheus, times are always expressed in seconds. So force the TimeUnits value accordingly, ignoring
        // whatever units were specified in the timer's metadata.
        String baseName = prometheusClean(metricId.getName(), helidonMetric.registryType() + "_");
        PrometheusName name = PrometheusName.create(helidonMetric.registryType(),
                                                    metadata,
                                                    metricId,
                                                    TimeUnits.PROMETHEUS_TIMER_CONVERSION_TIME_UNITS,
                                                    baseName);

        appendPrometheusTimerStatElement(sb, name, "rate_per_second", withHelpType, "gauge", value.getSnapshot().getMean());

        LabeledSnapshot snap = snapshotable.snapshot();
        histogram(sb,
                  name,
                  metadata,
                  snap,
                  TimeUnits.PROMETHEUS_TIMER_CONVERSION_TIME_UNITS,
                  value.getCount(),
                  value.getElapsedTime().toSeconds(),
                  withHelpType);
    }

    private static void appendPrometheusTimerStatElement(StringBuilder sb,
                                                         PrometheusName name,
                                                         String statName,
                                                         boolean withHelpType,
                                                         String typeName,
                                                         double value) {
        // For the timer stats output, suppress any units conversion; just emit the value directly.
        if (withHelpType) {
            prometheusType(sb, name.nameStat(statName), typeName);
        }
        sb.append(name.nameStatTags(statName)).append(" ").append(value).append("\n");
    }

    private static void histogram(StringBuilder sb,
                                  MetricID metricId,
                                  Metadata metadata,
                                  HelidonMetric helidonMetric,
                                  Histogram value,
                                  boolean withHelpType) {

        if (!(helidonMetric instanceof SnapshotMetric snapshotable)) {
            return;
        }

        String name = metricId.getName();
        String baseName = prometheusClean(name, helidonMetric.registryType() + "_");
        Units units = units(metadata);

        PrometheusName pName = PrometheusName.create(helidonMetric.registryType(), metadata, metricId, units, baseName);
        histogram(sb, pName, metadata, snapshotable.snapshot(), units, value.getCount(), value.getSum(), withHelpType);
    }

    private static void histogram(StringBuilder sb,
                                  PrometheusName name,
                                  Metadata metadata,
                                  LabeledSnapshot snap,
                                  Units units,
                                  long count,
                                  long sum,
                                  boolean withHelpType) {

        // # HELP file_sizes_bytes_max Users file size
        // # TYPE file_sizes_bytes_max gauge
        // file_sizes_bytes_max{mp_scope="application"} 31716
        appendPrometheusElement(sb, name, "max", withHelpType, "gauge", snap.max());

        // # HELP file_sizes_bytes Users file size
        // # TYPE file_sizes_bytes summary
        // file_sizes_bytes{mp_scope="application",quantile="0.5} 4201
        // for each supported quantile
        help(sb, metadata, name.nameUnits(), "summary", withHelpType);

        for (PercentileOutput po : DEFAULT_PERCENTILES) {
            prometheusQuantile(sb, name, units, po.promText, snap.value(po.value));
        }

        // file_sizes_bytes_count{mp_scope="application"} 2037
        // file_sizes_bytes_sum{mp_scope="application"} 514657


        sb.append(name.nameUnitsSuffixTags("count")).append(" ").append(count).append('\n');
        sb.append(name.nameUnitsSuffixTags("sum")).append(" ").append(sum).append('\n');
    }

    private static void prometheusQuantile(StringBuilder sb,
                                           PrometheusName name,
                                           Units units,
                                           String quantile,
                                           Sample.Derived derived) {
        // application:file_sizes_bytes{quantile="0.5"} 4201
        String quantileTag = "quantile=\"" + quantile + "\"";
        String tags = name.prometheusTags();
        if (name.prometheusTags().isEmpty()) {
            tags = "{" + quantileTag + "}";
        } else {
            tags = tags.substring(0, tags.length() - 1) + "," + quantileTag + "}";
        }

        sb.append(name.nameUnits()).append(tags).append(" ").append(units.convert(derived.value()));
        sb.append(prometheusExemplar(units, derived.sample()));
        sb.append("\n");
    }

//    private static void appendPrometheusElement(StringBuilder sb,
//                                                PrometheusName name,
//                                                String statName,
//                                                boolean withHelpType,
//                                                String typeName,
//                                                Sample.Derived derived) {
//        appendPrometheusElement(sb,
//                                name,
//                                () -> name.nameStatUnits(statName),
//                                withHelpType,
//                                typeName,
//                                derived.value(),
//                                derived.sample());
//    }

    private static void appendPrometheusElement(StringBuilder sb,
                                                PrometheusName name,
                                                String statName,
                                                boolean withHelpType,
                                                String typeName,
                                                Sample.Labeled sample) {
        appendPrometheusElement(sb, name, () -> name.nameStatUnits(statName), withHelpType, typeName, sample.value(), sample);
    }

    private static void appendPrometheusElement(StringBuilder sb,
                                                PrometheusName name,
                                                Supplier<String> nameToUse,
                                                boolean withHelpType,
                                                String typeName,
                                                double value,
                                                Sample.Labeled sample) {
        if (withHelpType) {
            prometheusType(sb, nameToUse.get(), typeName);
        }
        Object convertedValue = name.units().convert(value);
        sb.append(nameToUse.get()).append(name.prometheusTags()).append(" ").append(convertedValue)
                .append(prometheusExemplar(name.units(), sample)).append("\n");
    }

    private static void gauge(StringBuilder sb,
                              MetricID metricId,
                              Metadata metadata,
                              HelidonMetric helidonMetric,
                              Gauge<? extends Number> value,
                              boolean withHelpType) {
        String name = nameWithUnits(helidonMetric.registryType(), metadata, metricId);
        help(sb, metadata, name, "gauge", withHelpType);
        sb.append(name).append(tags(metricId.getTags())).append(" ").append(units(metadata).convert(value.getValue()))
                .append('\n');
    }

    private static void counter(StringBuilder sb,
                                MetricID metricId,
                                Metadata metadata,
                                HelidonMetric helidonMetric,
                                Counter value,
                                boolean withHelpType) {
        String name = prometheusName(helidonMetric.registryType(), metricId.getName());
        name = name.endsWith("total") ? name : name + "_total";

        help(sb, metadata, name, "counter", withHelpType);

        sb.append(name).append(tags(metricId.getTags())).append(" ").append(value.getCount());

        if (value instanceof SampledMetric sampled) {
            sampled.sample().ifPresent(it -> sb.append(prometheusExemplar(units(metadata), it)));
        }
        sb.append('\n');
    }

    private static void help(StringBuilder sb, Metadata metadata, String name, String type, boolean withHelpType) {
        if (withHelpType) {
            prometheusType(sb, name, type);
            prometheusHelp(sb, metadata, name);
        }
    }

    private static void prometheusType(StringBuilder sb, String nameWithUnits, String type) {
        sb.append("# TYPE ").append(nameWithUnits).append(" ").append(type).append('\n');
    }

    private static void prometheusHelp(StringBuilder sb, Metadata metadata, String nameWithUnits) {
        sb.append("# HELP ").append(nameWithUnits).append(" ").append(metadata.getDescription()).append('\n');
    }

    private static Units units(Metadata metadata) {
        String unit = metadata.getUnit();
        if ((null == unit) || unit.isEmpty() || MetricUnits.NONE.equals(unit)) {
            return new Units(null);
        }

        Units units = PROMETHEUS_CONVERTERS.get(unit);
        return units == null ? new Units(unit, unit, Function.identity()) : units;
    }

    private static String prometheusExemplar(Units units, Sample.Labeled sample) {
        return sample == null ? "" : prometheusExemplar(units.convert(sample.value()), sample);
    }

    private static String prometheusExemplar(Object value, Sample.Labeled sample) {
        if (sample == null || sample.label().isBlank()) {
            return "";
        }
        // The loaded service provides the entire label, including enclosing braces. For example, {trace_id=xxx}.
        String exemplar = String.format(" # %s %s %f", sample.label(), value, sample.timestamp() / 1000.0);
        if (exemplar.length() <= EXEMPLAR_MAX_LENGTH) {
            return exemplar;
        }
        LOGGER.log(WARNING,
                   String.format("Exemplar string exceeds the maximum length(%d); suppressing '%s'",
                                 exemplar.length(),
                                 exemplar));
        return "";
    }

    private static void addByteConverter(String metricUnit, long toByteRatio) {
        PROMETHEUS_CONVERTERS.put(metricUnit, new Units(metricUnit, "bytes", o -> ((Number) o).doubleValue() * toByteRatio));
    }

    private static void addConverter(Units units) {
        PROMETHEUS_CONVERTERS.put(units.getMetricUnit(), units);
    }

    private static void addTimeConverter(String metricUnit, TimeUnit timeUnit) {
        PROMETHEUS_CONVERTERS.put(metricUnit, new TimeUnits(metricUnit, timeUnit));
    }

    private static String prometheusTagValue(String value) {
        value = value.replace("\\", "\\\\");
        value = value.replace("\"", "\\\"");
        value = value.replace("\n", "\\n");
        return value;
    }

    private static String prometheusClean(String name, String prefix) {
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

    private static String durationPrometheusOutput(Duration duration) {
        return duration == null ? "NaN" : Double.toString(((double) duration.toNanos()) / 1000.0 / 1000.0 / 1000.0);
    }

    private static String exemplarForElapsedTime(Sample.Labeled sample) {
        return sample == null ? "" : prometheusExemplar(sample.value(), sample);
    }

    private static String convertTime(Object o, TimeUnit from) {
        return String.valueOf(TimeUnit.SECONDS.convert(new BigDecimal(String.valueOf(o)).longValue(),
                                                       from));
    }

    private static String convertNanos(Object o) {
        return String.valueOf(new BigDecimal(String.valueOf(o)).doubleValue() / TimeUnits.NANOSECONDS);
    }

    private static String convertMicros(Object o) {
        return String.valueOf(new BigDecimal(String.valueOf(o)).doubleValue() / TimeUnits.MICROSECONDS);
    }

    private static String convertMillis(Object o) {
        return String.valueOf(new BigDecimal(String.valueOf(o)).doubleValue() / TimeUnits.MILLISECONDS);
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

        String getMetricUnit() {
            return metricUnit;
        }

        Optional<String> getPrometheusUnit() {
            return Optional.ofNullable(prometheusUnit);
        }
    }

    static final class TimeUnits extends Units {
        static final TimeUnits PROMETHEUS_TIMER_CONVERSION_TIME_UNITS = new TimeUnits("seconds", TimeUnit.NANOSECONDS);
        private static final long MILLISECONDS = 1000;
        private static final long MICROSECONDS = 1000 * MILLISECONDS;
        private static final long NANOSECONDS = 1000 * MICROSECONDS;
        private static final String DOUBLE_NAN = String.valueOf(Double.NaN);
        // If object is NaN return string and avoid format exception in BigDecimal
        private static final BiFunction<Object, Function<Object, Object>, Object> CHECK_NANS =
                (o, f) -> o instanceof Double && ((Double) o).isNaN()
                        ? DOUBLE_NAN
                        : f.apply(o);

        private TimeUnits(String metricUnit, TimeUnit timeUnit) {
            super(metricUnit, "seconds", timeConverter(timeUnit));
        }

        static Function<Object, Object> timeConverter(TimeUnit from) {
            return switch (from) {
                case NANOSECONDS -> (o) -> CHECK_NANS.apply(o, PrometheusFormat::convertNanos);
                case MICROSECONDS -> (o) -> CHECK_NANS.apply(o, PrometheusFormat::convertMicros);
                case MILLISECONDS -> (o) -> CHECK_NANS.apply(o, PrometheusFormat::convertMillis);
                case SECONDS -> (o) -> CHECK_NANS.apply(o, String::valueOf);
                default -> (o) -> CHECK_NANS.apply(o, it -> convertTime(it, from));
            };
        }
    }

    private static class LengthUnits extends Units {
        private LengthUnits(String metricUnit, double ratio) {
            super(metricUnit, "meters", o -> ((Number) o).doubleValue() * ratio);
        }
    }

    /**
     * Abstraction for a Prometheus metric name, offering various formats of output as required by the Prometheus format.
     */
    static class PrometheusName {
        private final String prometheusTags;
        private final MetricID metricID;
        private final String prometheusNameWithUnits;
        private final String prometheusName;
        private final String prometheusUnit;
        private final Units units;
        private final String registryType;
        private final Metadata metadata;

        private PrometheusName(String registryType,
                               Metadata metadata,
                               MetricID metricID,
                               Units units,
                               String baseName) {
            this.registryType = registryType;
            this.metadata = metadata;
            this.metricID = metricID;
            this.units = units;
            this.prometheusName = baseName;
            this.prometheusTags = tags(metricID.getTags());
            this.prometheusNameWithUnits = nameWithUnits(registryType,
                                                         metadata,
                                                         metricID);
            this.prometheusUnit = units
                    .getPrometheusUnit()
                    .orElse("");
        }

        static PrometheusName create(String registryType,
                                     Metadata metadata,
                                     MetricID metricID,
                                     Units units,
                                     String baseName) {
            return new PrometheusName(registryType, metadata, metricID, units, baseName);
        }

        Units units() {
            return units;
        }

        /**
         * Returns the Prometheus metric name (registry type + metric name) + units.
         *
         * @return name with units
         */
        String nameUnits() {
            return prometheusNameWithUnits;
        }

        String nameUnits(Units units) {
            return nameWithUnits(registryType,
                                 metricID.getName(),
                                 units);
        }

        /**
         * Returns the Prometheus metric name (registry type + metric name) + statistic type + units.
         *
         * @param statName the statistics name (e.g., "mean") to include in the name expression
         * @return name with stat name with units
         */
        String nameStatUnits(String statName) {
            return nameStat(statName) + (prometheusUnit.isBlank() ? "" : "_" + prometheusUnit);
        }

        String nameStat(String statName) {
            return prometheusName + "_" + statName;
        }

        String nameStatTags(String statName) {
            return nameStat(statName) + prometheusTags;
        }

        /**
         * Returns the Prometheus metric name (registry type + metric name) + units + suffix (e.g., "count") + tags.
         *
         * @param nameSuffix suffix to add to the name (after the units)
         * @return name with units with suffix with tags
         */
        String nameUnitsSuffixTags(String nameSuffix) {
            return prometheusNameWithUnits + "_" + nameSuffix + prometheusTags;
        }

        /**
         * Returns the Prometheus format for the tags.
         *
         * @return tags in Prometheus format "{tag=value,tag=value,...}"
         */
        String prometheusTags() {
            return prometheusTags;
        }
    }
}
