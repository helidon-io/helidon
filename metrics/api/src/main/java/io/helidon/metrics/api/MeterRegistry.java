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

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Manages the look-up and registration of meters.
 */
public interface MeterRegistry extends Wrapped {

    /**
     * Returns all previously-registered meters.
     *
     * @return registered meters
     */
    List<Meter> meters();

    /**
     * Returns previously-registered meters which match the specified {@link java.util.function.Predicate}.
     *
     * @param filter the predicate with which to evaluate each {@link io.helidon.metrics.api.Meter}
     * @return meters which match the predicate
     */
    Collection<Meter> meters(Predicate<Meter> filter);

    /**
     * Locates a previously-registered meter using the name and tags in the provided builder or, if not found, registers a new
     * one using the provided builder.
     *
     * @param builder builder to use in finding or creating a meter
     * @return the previously-registered meter with the same name and tags or, if none, the newly-registered one
     * @param <M> type of the meter
     * @param <B> builder for the meter
     */
    <M extends Meter, B extends Meter.Builder<B, M>> M getOrCreate(B builder);

    /**
     * Locates a previously-registered counter.
     *
     * @param name name to match
     * @param tags tags to match
     * @return {@link java.util.Optional} of the previously-registered counter; empty if not found
     */
    default Optional<Counter> getCounter(String name, Iterable<Tag> tags) {
        return get(Counter.class, name, tags);
    }

    /**
     * Locates a previously-registered distribution summary.
     *
     * @param name name to match
     * @param tags tags to match
     * @return {@link java.util.Optional} of the previously-registered distribution summary; empty if not found
     */
    default Optional<DistributionSummary> getSummary(String name, Iterable<Tag> tags) {
        return get(DistributionSummary.class, name, tags);
    }

    /**
     * Locates a previously-registered gauge.
     *
     * @param name name to match
     * @param tags tags to match
     * @return {@link java.util.Optional} of the previously-registered gauge; empty if not found
     */
    default Optional<Gauge> getGauge(String name, Iterable<Tag> tags) {
        return get(Gauge.class, name, tags);
    }

    /**
     * Locates a previously-registered timer.
     *
     * @param name name to match
     * @param tags tags to match
     * @return {@link java.util.Optional} of the previously-registered timer; empty if not found
     */
    default Optional<Timer> getTimer(String name, Iterable<Tag> tags) {
        return get(Timer.class, name, tags);
    }

    /**
     * Locates a previously-registered meter of the specified type, matching the name and tags.
     * <p>
     *     The method throws an {@link java.lang.ClassCastException} if a meter exists with
     *     the name and tags but is not type-compatible with the provided class.
     * </p>
     *
     * @param mClass type of the meter to find
     * @param name name to match
     * @param tags tags to match
     * @return {@link java.util.Optional} of the previously-regsitered meter; empty if not found
     * @param <M> type of the meter to find
     */
    <M extends Meter> Optional<M> get(Class<M> mClass, String name, Iterable<Tag> tags);



    /**
     * Removes a previously-registered meter.
     *
     * @param meter the meter to remove
     * @return the removed meter; null if the meter is not currently registered
     */
    Meter remove(Meter meter);

    /**
     * Removes a previously-registered meter with the specified ID.
     *
     * @param id ID for the meter to remove
     * @return the removed meter; null if the meter is not currently registered
     */
    Meter remove(Meter.Id id);

    /**
     * Removes a previously-registered meter with the specified name and tags.
     *
     * @param name counter name
     * @param tags tags for further identifying the meter
     * @return the removed meter; null if the specified name and tags does not correspond to a registered meter
     */
    Meter remove(String name,
                 Iterable<Tag> tags);
}
