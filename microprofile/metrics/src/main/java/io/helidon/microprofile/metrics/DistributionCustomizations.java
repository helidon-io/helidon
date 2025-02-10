/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.microprofile.metrics;

import java.lang.reflect.Array;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

import io.helidon.common.Errors;
import io.helidon.common.LazyValue;
import io.helidon.metrics.api.DistributionStatisticsConfig;
import io.helidon.metrics.api.DistributionSummary;
import io.helidon.metrics.api.Timer;

import org.eclipse.microprofile.config.Config;

/**
 * Internal logic for handling config-based customization of percentiles and buckets.
 * <p>
 * MicroProfile Metrics 5.1 permits users to configure percentile and bucket settings for timers and histograms in
 * {@code META-INF/microprofile-config.properties}. This class encapsulates the processing to apply those defaults.
 * <p>
 * The general format of the config value is a semicolon-separated list of {@code nameExpression=values}
 * where each {@code values} is a comma-separated list of values. The {@code nameExpression} can be:
 *     <ul>
 *         <li>exact metric name</li>
 *         <li>metric name prefix followed by {@code *}</li>
 *         <li>{@code *}</li>
 *         <li>nothing.</li>
 *     </ul>
 * <p>
 *     Further, Micrometer provides a default set of histogram buckets which users can choose to apply to meters following the
 *     same {@code nameExpression} pattern.
 * </p>
 * </p>
 */
class DistributionCustomizations {

    private static final System.Logger LOGGER = System.getLogger(DistributionCustomizations.class.getName());

    private static final double DEFAULT_SUMMARY_MIN = 0.05d;
    private static final double DEFAULT_SUMMARY_MAX = 10000d;
    private static final Duration DEFAULT_TIMER_MIN = Duration.ofMillis(5);
    private static final Duration DEFAULT_TIMER_MAX = Duration.ofSeconds(10);

    private static DistributionCustomizations instance = new DistributionCustomizations();
    private final List<Percentiles> percentileCustomizations;
    private final List<SummaryBuckets> summaryBucketCustomizations;
    private final List<TimerBuckets> timerBucketCustomizations;
    private final List<SingleValuedCustomization<Boolean>> summaryBucketDefaultCustomizations;

    private DistributionCustomizations(Config mpConfig) {

        percentileCustomizations = collect(mpConfig, "mp.metrics.distribution.percentiles", Percentiles::new);

        summaryBucketCustomizations = collect(mpConfig, "mp.metrics.distribution.histogram.buckets", SummaryBuckets::new);

        timerBucketCustomizations = collect(mpConfig, "mp.metrics.distribution.timer.buckets", TimerBuckets::new);

        summaryBucketDefaultCustomizations = collect(mpConfig,
                                                     "mp.metrics.distribution.percentiles-histogram.enabled",
                                                     expression -> new SingleValuedCustomization<>(expression,
                                                                                                   Boolean::parseBoolean));
    }

    private DistributionCustomizations() {
        percentileCustomizations = List.of();
        summaryBucketCustomizations = List.of();
        timerBucketCustomizations = List.of();
        summaryBucketDefaultCustomizations = List.of();
    }

    static void init(Config mpConfig) {
        instance = new DistributionCustomizations(mpConfig);
    }

    static DistributionSummary.Builder apply(DistributionSummary.Builder builder) {
        DistributionStatisticsConfig.Builder statsBuilder = builder.distributionStatisticsConfig()
                .orElseGet(() -> {
                    DistributionStatisticsConfig.Builder newBuilder = DistributionStatisticsConfig.builder();
                    builder.distributionStatisticsConfig(newBuilder);
                    return newBuilder;
                });
        instance.percentileCustomizations.stream()
                .filter(p -> p.matches(builder.name()))
                .forEach(p -> statsBuilder.percentiles(p.percentiles()));

        AtomicBoolean matched = new AtomicBoolean();
        instance.summaryBucketCustomizations.stream()
                .filter(b -> b.matches(builder.name()))
                .forEach(b -> {
                    matched.set(true);
                    statsBuilder.buckets(b.buckets());
                });

        if (!matched.get()) {
            instance.summaryBucketDefaultCustomizations.stream()
                    .filter(def -> def.matches(builder.name()))
                    .map(SingleValuedCustomization::value)
                    .reduce((a, b) -> b) // Always take the latest match.
                    .ifPresent(def -> {
                        builder.publishPercentileHistogram(def);
                        statsBuilder.minimumExpectedValue(DEFAULT_SUMMARY_MIN)
                                .maximumExpectedValue(DEFAULT_SUMMARY_MAX);

                    });
        }

        return builder;
    }

    static Timer.Builder apply(Timer.Builder builder) {
        instance.percentileCustomizations.stream()
                .filter(p -> p.matches(builder.name()))
                .forEach(p -> builder.percentiles(p.percentiles()));

        AtomicBoolean matched = new AtomicBoolean();
        instance.timerBucketCustomizations.stream()
                .filter(b -> b.matches(builder.name()))
                .forEach(b -> {
                    matched.set(true);
                    builder.buckets(b.buckets());
                });

        if (!matched.get()) {
            instance.summaryBucketDefaultCustomizations.stream()
                    .filter(def -> def.matches(builder.name()))
                    .map(SingleValuedCustomization::value)
                    .reduce((a, b) -> b) // Always take the latest match.
                    .ifPresent(def -> {
                        builder.publishPercentileHistogram(def);
                        builder.minimumExpectedValue(DEFAULT_TIMER_MIN)
                                .maximumExpectedValue(DEFAULT_TIMER_MAX);
                    });
        }

        return builder;
    }

    private static <U extends Customization> List<U> collect(Config mpConfig,
                                                             String configKey,
                                                             Function<String, U> factory) {
        return mpConfig.getOptionalValue(configKey, String.class)
                .stream()
                .flatMap(s -> Arrays.stream(s.split(";")))
                .map(factory)
                .toList();
    }

    private static double[] doubles(Double[] doubles) {
        double[] result = new double[doubles.length];
        for (int i = 0; i < doubles.length; i++) {
            result[i] = doubles[i];
        }
        return result;
    }

    abstract static class Customization {
        private final String namePrefix;
        private final boolean hasTrailingWildcard;
        private final String valuesExpression;

        protected Customization(String nameExpressionAndValues) {
            int eq = nameExpressionAndValues.indexOf('=');
            if (eq <= 0) {
                valuesExpression = "";
                namePrefix = "";
                hasTrailingWildcard = false;
                return;
            }
            String namePrefixMaybeWithWildcard = nameExpressionAndValues.substring(0, eq);
            hasTrailingWildcard = namePrefixMaybeWithWildcard.endsWith("*");
            namePrefix = namePrefixMaybeWithWildcard.substring(0, hasTrailingWildcard ? eq - 1 : eq);
            valuesExpression = nameExpressionAndValues.substring(eq + 1);
        }

        protected String namePrefix() {
            return namePrefix;
        }

        protected boolean hasTrailingWildcard() {
            return hasTrailingWildcard;
        }

        protected String valuesExpression() {
            return valuesExpression;
        }

        protected boolean matches(String metricName) {
            return hasTrailingWildcard() ? metricName.startsWith(namePrefix())
                    : metricName.equals(namePrefix());
        }
    }

    static class SingleValuedCustomization<T> extends Customization {

        private final T value;

        protected SingleValuedCustomization(String nameAndValuesExpression, Function<String, T> valueParser) {
            super(nameAndValuesExpression);
            value = valueParser.apply(valuesExpression());
        }

        protected T value() {
            return value;
        }
    }

    abstract static class MultiValuedCustomization<T extends Comparable<T>> extends Customization {

        private final T[] values;

        protected MultiValuedCustomization(String nameExpressionAndValues,
                                           Class<T> type,
                                           Function<String, T> valueParser,
                                           Consumer<T> valueChecker) {
            super(nameExpressionAndValues);
            if (valuesExpression().isBlank()) {
                values = (T[]) Array.newInstance(type, 0);
                return;
            }
            values = values(valuesExpression(), type, valueParser, valueChecker);
        }

        protected T[] values() {
            return values;
        }

        private T[] values(String valuesString,
                           Class<T> type,
                           Function<String, T> valueParser,
                           Consumer<T> valueChecker) {
            String[] valueStrings = valuesString.split(",");
            T[] result = (T[]) Array.newInstance(type, valueStrings.length);
            int next = 0;
            T prev = null;
            boolean valuesInOrder = true;
            LazyValue<Errors.Collector> collector = LazyValue.create(Errors::collector);
            for (String valueString : valueStrings) {
                if (!valueString.isBlank()) {
                    T value;
                    try {
                        value = valueParser.apply(valueString);
                    } catch (Exception ex) {
                        // The spec says to ignore invalid values but we'll warn about it.
                        collector.get().warn("ignoring invalid value: " + ex.getMessage());
                        continue;
                    }
                    valuesInOrder &= prev != null && prev.compareTo(value) < 0;
                    try {
                        valueChecker.accept(value);
                    } catch (Exception ex) {
                        collector.get().warn("ignoring value for "
                                                     + namePrefix()
                                                     + (hasTrailingWildcard() ? "*" : ": ")
                                                     + ex.getMessage());
                        continue;
                    }
                    result[next++] = value;
                    prev = value;
                }
            }
            if (!valuesInOrder) {
                collector.get().warn("Values for "
                                             + namePrefix()
                                             + (hasTrailingWildcard() ? "*" : "")
                                             + "should be in strictly increasing order but are not: "
                                             + Arrays.toString(valueStrings));
            }

            if (collector.isLoaded()) {
                collector.get().collect().log(LOGGER);
            }
            return Arrays.copyOf(result, next);
        }
    }

    static class Percentiles extends MultiValuedCustomization<Double> {

        private final double[] values;

        private Percentiles(String nameExpressionAndValues) {
            super(nameExpressionAndValues, Double.class, Double::parseDouble, d -> {
                if (d < 0.0d || d > 1.0d) {
                    throw new IllegalArgumentException("Value " + d + " not in required range [0.0, 1.0]");
                }
            });
            values = doubles(values());
        }

        double[] percentiles() {
            return values;
        }
    }

    static class SummaryBuckets extends MultiValuedCustomization<Double> {

        private final double[] values;

        private SummaryBuckets(String nameExpressionAndValues) {
            super(nameExpressionAndValues, Double.class, Double::parseDouble, d -> {
                if (d <= 0) {
                    throw new IllegalArgumentException("Value must be > 0 but " + d + " is not");
                }
            });
            values = doubles(values());
        }

        double[] buckets() {
            return values;
        }
    }

    static class TimerBuckets extends MultiValuedCustomization<Duration> {

        private TimerBuckets(String nameExpressionAndValues) {
            super(nameExpressionAndValues, Duration.class, TimerBuckets::parseTimerBucketExpression, d -> {
            });
        }

        protected Duration[] buckets() {
            return values();
        }

        private static Duration parseTimerBucketExpression(String expr) {
            TimeUnit timeUnit;
            int suffixSize = 1;
            String lcExpr = expr.toLowerCase(Locale.ROOT);
            if (lcExpr.endsWith("ms")) {
                timeUnit = TimeUnit.MILLISECONDS;
                suffixSize = 2;
            } else if (lcExpr.endsWith("s")) {
                timeUnit = TimeUnit.SECONDS;
            } else if (lcExpr.endsWith("m")) {
                timeUnit = TimeUnit.MINUTES;
            } else if (lcExpr.endsWith("h")) {
                timeUnit = TimeUnit.HOURS;
            } else {
                timeUnit = TimeUnit.MILLISECONDS;
                suffixSize = 0;
            }
            int amount = Integer.parseInt(expr.substring(0, expr.length() - suffixSize));
            return Duration.of(amount, timeUnit.toChronoUnit());
        }
    }

}
