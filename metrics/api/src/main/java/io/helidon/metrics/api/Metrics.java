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
package io.helidon.metrics.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A main entry point for developers to the Helidon metrics system, allowing access to the global meter registry and providing
 * shortcut methods to register and locate meters in the global registry and remove meters from it.
 */
public interface Metrics {

    /**
     * Returns the global meter registry.
     *
     * @return the global meter registry
     */
    static MeterRegistry globalRegistry() {
        return MetricsFactory.getInstance().globalRegistry();
    }

    /**
     * Creates a meter registry, not saved as the global registry, based on the provided metrics config.
     *
     * @param metricsConfig metrics config
     * @return new meter registry
     */
    static MeterRegistry createMeterRegistry(MetricsConfig metricsConfig) {
        return MetricsFactory.getInstance().createMeterRegistry(metricsConfig);
    }

    /**
     * Creates a meter registry, not saved as the global registry, using default metrics config information based on global
     * config.
     *
     * @return new meter registry
     */
    static MeterRegistry createMeterRegistry() {
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
     */
    static <M extends Meter, B extends Meter.Builder<B, M>> M getOrCreate(B builder) {
        return globalRegistry().getOrCreate(builder);
    }

    /**
     * Locates a previously-registered counter.
     *
     * @param name name to match
     * @param tags tags to match
     * @return {@link java.util.Optional} of the previously-registered counter; empty if not found
     */
    static Optional<Counter> getCounter(String name, Iterable<Tag> tags) {
        return globalRegistry().meter(Counter.class, name, tags);
    }

    /**
     * Locates a previously-registerec counter.
     *
     * @param name name to match
     * @return {@link java.util.Optional} of the previously-registered counter; empty if not found
     */
    static Optional<Counter> getCounter(String name) {
        return getCounter(name, Set.of());
    }

    /**
     * Locates a previously-registered distribution summary.
     *
     * @param name name to match
     * @param tags tags to match
     * @return {@link java.util.Optional} of the previously-registered distribution summary; empty if not found
     */
    static Optional<DistributionSummary> getSummary(String name, Iterable<Tag> tags) {
        return globalRegistry().meter(DistributionSummary.class, name, tags);
    }

    /**
     * Locates a previously-registered distribution summary.
     *
     * @param name name to match
     * @return {@link java.util.Optional} of the previously-registered distribution summary; empty if not found
     */
    static Optional<DistributionSummary> getSummary(String name) {
        return getSummary(name, Set.of());
    }

    /**
     * Locates a previously-registered gauge.
     *
     * @param name name to match
     * @param tags tags to match
     * @return {@link java.util.Optional} of the previously-registered gauge; empty if not found
     */
    static Optional<Gauge> getGauge(String name, Iterable<Tag> tags) {
        return globalRegistry().meter(Gauge.class, name, tags);
    }

    /**
     * Locates a previously-registered gauge.
     *
     * @param name name to match
     * @return {@link java.util.Optional} of the previously-registered gauge; empty if not found
     */
    static Optional<Gauge> getGauge(String name) {
        return getGauge(name, Set.of());
    }

    /**
     * Locates a previously-registered timer.
     *
     * @param name name to match
     * @param tags tags to match
     * @return {@link java.util.Optional} of the previously-registered timer; empty if not found
     */
    static Optional<Timer> getTimer(String name, Iterable<Tag> tags) {
        return globalRegistry().meter(Timer.class, name, tags);
    }

    /**
     * Locates a previously-registered timer.
     *
     * @param name name to match
     * @return {@link java.util.Optional} of the previously-registered timer; empty if not found
     */
    static Optional<Timer> getTimer(String name) {
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
     */
    static <M extends Meter> Optional<M> get(Class<M> mClass, String name, Iterable<Tag> tags) {
        return globalRegistry().meter(mClass, name, tags);
    }

    /**
     * Creates a {@link Tag} for the specified key and value.
     *
     * @param key   tag key
     * @param value tag value
     * @return new tag
     */
    static Tag tag(String key, String value) {
        return MetricsFactory.getInstance().tagCreate(key, value);
    }

    /**
     * Returns an {@link java.lang.Iterable} of {@link io.helidon.metrics.api.Tag} by interpreting the provided strings as
     * tag name/tag value pairs.
     *
     * @param keyValuePairs pairs of tag name/tag value pairs
     * @return tags corresponding to the tag name/tag value pairs
     */
    static Iterable<Tag> tags(String... keyValuePairs) {
        if (keyValuePairs.length % 2 != 0) {
            throw new IllegalArgumentException("Must pass an even number of strings so keys and values are evenly matched");
        }
        List<Tag> result = new ArrayList<>();
        for (int slot = 0; slot < keyValuePairs.length / 2; slot++) {
            result.add(Tag.create(keyValuePairs[slot * 2], keyValuePairs[slot * 2 + 1]));
        }
        return result;
    }
}
