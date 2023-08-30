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
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

import io.helidon.common.Builder;

/**
 * Manages the look-up and registration of meters.
 */
public interface MeterRegistry extends Wrapper {

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
     * Returns previously-registered meters which match one of the specified scopes.
     *
     * @param scopeSelection scopes to match
     * @return matching meters
     */
    Iterable<Meter> meters(Iterable<String> scopeSelection);

    /**
     * Returns the scopes (if any) as represented by tags on meter IDs.
     *
     * @return scopes across all registered meters
     */
    Iterable<String> scopes();

    /**
     * Clears all registered meters from the meter registry.
     */
    default void clear() {
        meters().forEach(this::remove);
    }

    /**
     * Returns whether the specified meter is enabled or not, based on whether the meter registry as a whole is enabled and also
     * whether the config settings for filtering include and exclude indicate the specific meter is enabled.
     *
     * @param name  name of the meter to check
     * @param tags  tags of the meter to check
     * @param scope scope, if present, of the meter to check
     * @return true if the meter (and its meter registry) are enabled; false otherwise
     */
    boolean isMeterEnabled(String name, Map<String, String> tags, Optional<String> scope);

    /**
     * Returns the default {@link io.helidon.metrics.api.Clock} in use by the registry.
     *
     * @return default clock
     */
    Clock clock();

    /**
     * Locates a previously-registered meter using the name and tags in the provided builder or, if not found, registers a new
     * one using the provided builder.
     *
     * @param builder builder to use in finding or creating a meter
     * @param <B>     builder for the meter
     * @param <M>     type of the meter
     * @return the previously-registered meter with the same name and tags or, if none, the newly-registered one
     */
    <B extends Meter.Builder<B, M>, M extends Meter> M getOrCreate(B builder);

    /**
     * Locates a previously-registered counter.
     *
     * @param name name to match
     * @param tags tags to match
     * @return {@link java.util.Optional} of the previously-registered counter; empty if not found
     */
    default Optional<Counter> counter(String name, Iterable<Tag> tags) {
        return meter(Counter.class, name, tags);
    }

    /**
     * Locates a previously-registered distribution summary.
     *
     * @param name name to match
     * @param tags tags to match
     * @return {@link java.util.Optional} of the previously-registered distribution summary; empty if not found
     */
    default Optional<DistributionSummary> summary(String name, Iterable<Tag> tags) {
        return meter(DistributionSummary.class, name, tags);
    }

    /**
     * Locates a previously-registered gauge.
     *
     * @param name name to match
     * @param tags tags to match
     * @return {@link java.util.Optional} of the previously-registered gauge; empty if not found
     */
    default Optional<Gauge> gauge(String name, Iterable<Tag> tags) {
        return meter(Gauge.class, name, tags);
    }

    /**
     * Locates a previously-registered timer.
     *
     * @param name name to match
     * @param tags tags to match
     * @return {@link java.util.Optional} of the previously-registered timer; empty if not found
     */
    default Optional<Timer> timer(String name, Iterable<Tag> tags) {
        return meter(Timer.class, name, tags);
    }

    /**
     * Locates a previously-registered meter of the specified type, matching the name and tags.
     * <p>
     * The method throws an {@link java.lang.ClassCastException} if a meter exists with
     * the name and tags but is not type-compatible with the provided class.
     * </p>
     *
     * @param mClass type of the meter to find
     * @param name   name to match
     * @param tags   tags to match
     * @param <M>    type of the meter to find
     * @return {@link java.util.Optional} of the previously-regsitered meter; empty if not found
     */
    <M extends Meter> Optional<M> meter(Class<M> mClass, String name, Iterable<Tag> tags);

    /**
     * Removes a previously-registered meter.
     *
     * @param meter the meter to remove
     * @return the removed meter; empty if the meter is not currently registered
     */
    Optional<Meter> remove(Meter meter);

    /**
     * Removes a previously-registered meter with the specified ID.
     *
     * @param id ID for the meter to remove
     * @return the removed meter; empty if the meter is not currently registered
     */
    Optional<Meter> remove(Meter.Id id);

    /**
     * Removes a previously-registered meter with the specified ID and scope.
     *
     * @param id    ID for the meter to remove
     * @param scope scope of the meter to remove
     * @return the removed meter; empty if the specified ID and scope do not correspond to a registered meter
     */
    Optional<Meter> remove(Meter.Id id, String scope);

    /**
     * Removes a previously-registered meter with the specified name and tags.
     *
     * @param name meter name
     * @param tags tags for further identifying the meter
     * @return the removed meter; empty if the specified name and tags do not correspond to a registered meter
     */
    Optional<Meter> remove(String name, Iterable<Tag> tags);

    /**
     * Removes a previously-registered meter with the specified name, tags, and scope.
     *
     * @param name  meter name
     * @param tags  tags for further identifying the meter
     * @param scope scope within which to locate the meter
     * @return the removed meter; empty if the specified name, tags, and scope do not correspond to a registered meter
     */
    Optional<Meter> remove(String name, Iterable<Tag> tags, String scope);

    /**
     * Indicates if the meter has been deleted.
     *
     * @param meter {@link io.helidon.metrics.api.Meter} to check
     * @return true if the meter has been deleted; false if it is still active
     */
    boolean isDeleted(Meter meter);

    /**
     * Enroll a listener to be notified when a {@link io.helidon.metrics.api.Meter} is added.
     *
     * @param onAddListener listener to invoke upon each meter registration
     * @return the meter registry
     */
    MeterRegistry onMeterAdded(Consumer<Meter> onAddListener);

    /**
     * Enroll a listener to be notified when a {@link io.helidon.metrics.api.Meter} is removed.
     *
     * @param onRemoveListener listener to invoke upon each meter removal
     * @return the meter registry
     */
    MeterRegistry onMeterRemoved(Consumer<Meter> onRemoveListener);

    /**
     * Builder for creating a new meter registry.
     *
     * @param <B> builder type
     * @param <R> meter registry type
     */
    interface Builder<B extends Builder<B, R>, R extends MeterRegistry> extends io.helidon.common.Builder<B, R> {

        /**
         * Assigns the clock to be used within the meter registry (e.g., in timers), defaulting to a system clock.
         *
         * @param clock the {@link io.helidon.metrics.api.Clock} to be used
         * @return updated builder
         */
        B clock(Clock clock);

        /**
         * Sets the {@link io.helidon.metrics.api.MetricsConfig} for the meter registry, defaulting to the
         * metrics config with which the {@link io.helidon.metrics.api.MetricsFactory} was created.
         *
         * @param metricsConfig metrics config to control the meter registry
         * @return updated builder
         */
        B metricsConfig(MetricsConfig metricsConfig);

        /**
         * Records a subscriber to meter-added events.
         *
         * @param addListener listener for meter-added events
         * @return updated builder
         */
        B onMeterAdded(Consumer<Meter> addListener);

        /**
         * Records a subscriber to meter-removed events.
         *
         * @param removeListener listener for meter-removal events
         * @return updated builder
         */
        B onMeterRemoved(Consumer<Meter> removeListener);
    }
}
