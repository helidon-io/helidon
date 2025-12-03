/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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
package io.helidon.metrics.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.helidon.service.registry.Interception;

/**
 * A main entry point for developers to the Helidon metrics system, allowing access to the global meter registry and providing
 * shortcut methods to register and locate meters in the global registry and remove meters from it.
 */
public class Metrics {
    private Metrics() {
    }

    /**
     * Returns the global meter registry.
     *
     * @return the global meter registry
     * @deprecated global instances are deprecated in general, and {@link io.helidon.service.registry.Services} can be used
     * to get such an instance; until these methods are removed, the behavior may differ
     */
    @Deprecated(since = "4.4.0", forRemoval = true)
    public static MeterRegistry globalRegistry() {
        return MetricsFactory.getInstance().globalRegistry();
    }

    /**
     * Creates a meter registry, not saved as the global registry, based on the provided metrics config.
     *
     * @param metricsConfig metrics config
     * @return new meter registry
     * @deprecated use {@link io.helidon.metrics.api.MeterRegistry#create(MetricsConfig)} instead
     */
    @Deprecated(since = "4.4.0", forRemoval = true)
    public static MeterRegistry createMeterRegistry(MetricsConfig metricsConfig) {
        return MeterRegistry.create(metricsConfig);
    }

    /**
     * Creates a meter registry, not saved as the global registry, using default metrics config information based on global
     * config.
     *
     * @return new meter registry
     * @deprecated use {@link MeterRegistry#create()} instead
     */
    @Deprecated(since = "4.4.0", forRemoval = true)
    public static MeterRegistry createMeterRegistry() {
        return createMeterRegistry(MetricsConfig.create());
    }

    /**
     * Locates a previously-registered meter using the name and tags in the provided builder or, if not found, registers a new
     * one using the provided builder, both using the metrics factory's global registry.
     *
     * @param builder builder to use in finding or creating a meter
     * @param <M>     type of the meter
     * @param <B>     builder for the meter
     * @return the previously-registered meter with the same name and tags or, if none, the newly-registered one
     * @deprecated use the {@link io.helidon.metrics.api.MeterRegistry} API instead, as this method is just a shortcut
     *  to {@link #globalRegistry()}; this method will be removed without a replacement
     */
    @Deprecated(since = "4.4.0", forRemoval = true)
    public static <M extends Meter, B extends Meter.Builder<B, M>> M getOrCreate(B builder) {
        return globalRegistry().getOrCreate(builder);
    }

    /**
     * Locates a previously-registered counter.
     *
     * @param name name to match
     * @param tags tags to match
     * @return {@link java.util.Optional} of the previously-registered counter; empty if not found
     * @deprecated use the {@link io.helidon.metrics.api.MeterRegistry} API instead, as this method is just a shortcut
     *  to {@link #globalRegistry()}; this method will be removed without a replacement
     */
    @Deprecated(since = "4.4.0", forRemoval = true)
    public static Optional<io.helidon.metrics.api.Counter> getCounter(String name, Iterable<io.helidon.metrics.api.Tag> tags) {
        return globalRegistry().meter(io.helidon.metrics.api.Counter.class, name, tags);
    }

    /**
     * Locates a previously-registerec counter.
     *
     * @param name name to match
     * @return {@link java.util.Optional} of the previously-registered counter; empty if not found
     * @deprecated use the {@link io.helidon.metrics.api.MeterRegistry} API instead, as this method is just a shortcut
     *  to {@link #globalRegistry()}; this method will be removed without a replacement
     */
    @Deprecated(since = "4.4.0", forRemoval = true)
    public static Optional<Counter> getCounter(String name) {
        return getCounter(name, Set.of());
    }

    /**
     * Locates a previously-registered distribution summary.
     *
     * @param name name to match
     * @param tags tags to match
     * @return {@link java.util.Optional} of the previously-registered distribution summary; empty if not found
     * @deprecated use the {@link io.helidon.metrics.api.MeterRegistry} API instead, as this method is just a shortcut
     *  to {@link #globalRegistry()}; this method will be removed without a replacement
     */
    @Deprecated(since = "4.4.0", forRemoval = true)
    public static Optional<DistributionSummary> getSummary(String name, Iterable<io.helidon.metrics.api.Tag> tags) {
        return globalRegistry().meter(DistributionSummary.class, name, tags);
    }

    /**
     * Locates a previously-registered distribution summary.
     *
     * @param name name to match
     * @return {@link java.util.Optional} of the previously-registered distribution summary; empty if not found
     * @deprecated use the {@link io.helidon.metrics.api.MeterRegistry} API instead, as this method is just a shortcut
     *  to {@link #globalRegistry()}; this method will be removed without a replacement
     */
    @Deprecated(since = "4.4.0", forRemoval = true)
    public static Optional<DistributionSummary> getSummary(String name) {
        return getSummary(name, Set.of());
    }

    /**
     * Locates a previously-registered gauge.
     *
     * @param name name to match
     * @param tags tags to match
     * @return {@link java.util.Optional} of the previously-registered gauge; empty if not found
     * @deprecated use the {@link io.helidon.metrics.api.MeterRegistry} API instead, as this method is just a shortcut
     *  to {@link #globalRegistry()}; this method will be removed without a replacement
     */
    @SuppressWarnings("rawtypes")
    @Deprecated(since = "4.4.0", forRemoval = true)
    public static Optional<io.helidon.metrics.api.Gauge> getGauge(String name, Iterable<io.helidon.metrics.api.Tag> tags) {
        return globalRegistry().meter(io.helidon.metrics.api.Gauge.class, name, tags);
    }

    /**
     * Locates a previously-registered gauge.
     *
     * @param name name to match
     * @return {@link java.util.Optional} of the previously-registered gauge; empty if not found
     * @deprecated use the {@link io.helidon.metrics.api.MeterRegistry} API instead, as this method is just a shortcut
     *  to {@link #globalRegistry()}; this method will be removed without a replacement
     */
    @SuppressWarnings("rawtypes")
    @Deprecated(since = "4.4.0", forRemoval = true)
    public static Optional<io.helidon.metrics.api.Gauge> getGauge(String name) {
        return getGauge(name, Set.of());
    }

    /**
     * Locates a previously-registered timer.
     *
     * @param name name to match
     * @param tags tags to match
     * @return {@link java.util.Optional} of the previously-registered timer; empty if not found
     * @deprecated use the {@link io.helidon.metrics.api.MeterRegistry} API instead, as this method is just a shortcut
     *  to {@link #globalRegistry()}; this method will be removed without a replacement
     */
    @Deprecated(since = "4.4.0", forRemoval = true)
    public static Optional<Timer> getTimer(String name, Iterable<io.helidon.metrics.api.Tag> tags) {
        return globalRegistry().meter(Timer.class, name, tags);
    }

    /**
     * Locates a previously-registered timer.
     *
     * @param name name to match
     * @return {@link java.util.Optional} of the previously-registered timer; empty if not found
     * @deprecated use the {@link io.helidon.metrics.api.MeterRegistry} API instead, as this method is just a shortcut
     *  to {@link #globalRegistry()}; this method will be removed without a replacement
     */
    @Deprecated(since = "4.4.0", forRemoval = true)
    public static Optional<Timer> getTimer(String name) {
        return getTimer(name, Set.of());
    }

    /**
     * Locates a previously-registered meter of the specified type, matching the name and tags.
     * <p>
     * The method throws an {@link java.lang.IllegalArgumentException} if a meter exists with
     * the name and tags but is not type-compatible with the provided class.
     * </p>
     *
     * @param mClass type of the meter to find
     * @param name   name to match
     * @param tags   tags to match
     * @param <M>    type of the meter to find
     * @return {@link java.util.Optional} of the previously-regsitered meter; empty if not found
     * @deprecated use the {@link io.helidon.metrics.api.MeterRegistry} API instead, as this method is just a shortcut
     *  to {@link #globalRegistry()}; this method will be removed without a replacement
     */
    @Deprecated(since = "4.4.0", forRemoval = true)
    public static <M extends Meter> Optional<M> get(Class<M> mClass, String name, Iterable<io.helidon.metrics.api.Tag> tags) {
        return globalRegistry().meter(mClass, name, tags);
    }

    /**
     * Creates a {@link io.helidon.metrics.api.Tag} for the specified key and value.
     *
     * @param key   tag key
     * @param value tag value
     * @return new tag
     * @deprecated use {@link io.helidon.metrics.api.Tag#create(String, String)} instead
     */
    @Deprecated(since = "4.4.0", forRemoval = true)
    public static io.helidon.metrics.api.Tag tag(String key, String value) {
        return MetricsFactory.getInstance().tagCreate(key, value);
    }

    /**
     * Returns an {@link java.lang.Iterable} of {@link io.helidon.metrics.api.Tag} by interpreting the provided strings as
     * tag name/tag value pairs.
     *
     * @param keyValuePairs pairs of tag name/tag value pairs
     * @return tags corresponding to the tag name/tag value pairs
     * @deprecated use {@link io.helidon.metrics.api.Tag#create(String, String)} instead
     */
    @Deprecated(since = "4.4.0", forRemoval = true)
    public static Iterable<io.helidon.metrics.api.Tag> tags(String... keyValuePairs) {
        if (keyValuePairs.length % 2 != 0) {
            throw new IllegalArgumentException("Must pass an even number of strings so keys and values are evenly matched");
        }
        List<io.helidon.metrics.api.Tag> result = new ArrayList<>();
        for (int slot = 0; slot < keyValuePairs.length / 2; slot++) {
            result.add(io.helidon.metrics.api.Tag.create(keyValuePairs[slot * 2], keyValuePairs[slot * 2 + 1]));
        }
        return result;
    }

    /**
     * The annotated method will be counted.
     */
    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.METHOD)
    @Documented
    @Interception.Intercepted
    public @interface Counted {
        /**
         * Name of the counter.
         *
         * @return counter name
         */
        String value() default "";

        /**
         * Scope of the metric, defaults to {@link io.helidon.metrics.api.Meter.Scope#APPLICATION}.
         *
         * @return metric scope
         */
        String scope() default Meter.Scope.APPLICATION;

        /**
         * Description of the metric.
         *
         * @return metric description
         */
        String description() default "";

        /**
         * Additional tags of the metric.
         *
         * @return metric tags
         */
        Tag[] tags() default {};

        /**
         * Whether the defined name in {@link #value()} is an absolute name, or relative to the
         * class containing the annotated element.
         *
         * @return {@code true} if the name is absolute, {@code false} otherwise.
         */
        boolean absoluteName() default false;

        /**
         * Unit of the metric, defaults to {@link io.helidon.metrics.api.Meter.BaseUnits#NONE}.
         *
         * @return metric unit
         */
        String unit() default Meter.BaseUnits.NONE;
    }

    /**
     * The annotated method will be timed.
     */
    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.METHOD)
    @Documented
    @Interception.Intercepted
    public @interface Timed {
        /**
         * Name of the timer.
         *
         * @return timer name
         */
        String value() default "";

        /**
         * Scope of the metric, defaults to {@link io.helidon.metrics.api.Meter.Scope#APPLICATION}.
         *
         * @return metric scope
         */
        String scope() default Meter.Scope.APPLICATION;

        /**
         * Description of the metric.
         *
         * @return metric description
         */
        String description() default "";

        /**
         * Additional tags of the metric.
         *
         * @return metric tags
         */
        Tag[] tags() default {};

        /**
         * Whether the defined name in {@link #value()} is an absolute name, or relative to the
         * class containing the annotated element.
         *
         * @return {@code true} if the name is absolute, {@code false} otherwise.
         */
        boolean absoluteName() default false;

        /**
         * Unit of the metric, defaults to {@link io.helidon.metrics.api.Meter.BaseUnits#NANOSECONDS}.
         *
         * @return metric unit
         */
        String unit() default Meter.BaseUnits.NANOSECONDS;
    }

    /**
     * The annotated method will be a gauge source.
     */
    @Retention(RetentionPolicy.CLASS)
    @Documented
    @Interception.Intercepted
    @Target(ElementType.METHOD)
    public @interface Gauge {
        /**
         * Name of the gauge.
         *
         * @return gauge name
         */
        String value() default "";

        /**
         * Scope of the metric, defaults to {@link io.helidon.metrics.api.Meter.Scope#APPLICATION}.
         *
         * @return metric scope
         */
        String scope() default Meter.Scope.APPLICATION;

        /**
         * Description of the metric.
         *
         * @return metric description
         */
        String description() default "";

        /**
         * Additional tags of the metric.
         *
         * @return metric tags
         */
        Tag[] tags() default {};

        /**
         * Whether the defined name in {@link #value()} is an absolute name, or relative to the
         * class containing the annotated element.
         *
         * @return {@code true} if the name is absolute, {@code false} otherwise.
         */
        boolean absoluteName() default false;

        /**
         * Unit of the gauge must be provided.
         *
         * @return metric unit
         */
        String unit();
    }

    /**
     * Metric tag.
     * <p>
     * Tags can be declared on:
     * <ul>
     *     <li>The type that contains a metered method, such tags will be applied to all meters within this type</li>
     *     <li>The method that has a metric annotation, such tags will be applied to all meters on the method</li>
     *     <li>In meters annotations {@code tags} property, such tags will be applied only to the single meter</li>
     * </ul>
     */
    @Repeatable(Tags.class)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.CLASS)
    public @interface Tag {
        /**
         * Tag key.
         *
         * @return tag key
         */
        String key();

        /**
         * Tag value.
         *
         * @return tag value
         */
        String value();
    }

    /**
     * Container for {@link Tag} repeating annotation.
     */
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.CLASS)
    public @interface Tags {
        /**
         * Tags of this repeating container.
         *
         * @return tags
         */
        Tag[] value();
    }
}
