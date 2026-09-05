/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

import io.helidon.common.DeprecationSupport;
import io.helidon.service.registry.Services;

/**
 * Manages the look-up and registration of meters.
 * <p>
 * The shared registry obtained from {@link Services} or an
 * {@link io.helidon.service.registry.ServiceRegistry} is owned and closed by that service registry; application code must not
 * close it. A custom registry created using {@link MetricsFactory} is owned by the caller, which must close it to release the
 * registry and any publisher resources it owns.
 */
public interface MeterRegistry extends Wrapper {
    /**
     * Creates a new meter registry.
     * For general case where you just need a {@link io.helidon.metrics.api.MeterRegistry}, use
     * {@link io.helidon.service.registry.Services#get(java.lang.Class) Services.get(MeterRegistry.class)}.
     *
     * @return new meter registry
     * @deprecated either use {@link io.helidon.service.registry.ServiceRegistry#get(Class)} to get the global meter registry,
     * or get the {@link MetricsFactory#createMeterRegistry(MetricsConfig)} to get a custom instance
     */
    @Deprecated(forRemoval = true, since = "27.0.0")
    static MeterRegistry create() {
        return create(MetricsConfig.create());
    }

    /**
     * Creates a meter registry, not saved as the global registry, based on the provided metrics config.
     *
     * @param metricsConfig metrics config
     * @return new meter registry
     * @deprecated either use {@link io.helidon.service.registry.ServiceRegistry#get(Class)} to get the global meter registry,
     * or get the {@link MetricsFactory#createMeterRegistry(MetricsConfig)} to get a custom instance
     */
    @Deprecated(forRemoval = true, since = "27.0.0")
    static MeterRegistry create(MetricsConfig metricsConfig) {
        return Services.get(MetricsFactory.class).createMeterRegistry(metricsConfig);
    }

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
     * Returns all previously-registered meters, ignoring the scope selection.
     *
     * @param scopeSelection ignored; must not be {@code null}
     * @return all registered meters
     * @deprecated Use {@link #meters()}. Scope selection is ignored and this method will be removed.
     */
    @Deprecated(forRemoval = true, since = "27.0.0")
    default Iterable<Meter> meters(Iterable<String> scopeSelection) {
        Objects.requireNonNull(scopeSelection);
        return meters();
    }

    /**
     * Always returns an empty iterable and will be removed.
     *
     * @return empty iterable
     * @deprecated Always returns an empty iterable and will be removed.
     */
    @Deprecated(forRemoval = true, since = "27.0.0")
    default Iterable<String> scopes() {
        return List.of();
    }

    /**
     * Closes this meter registry and the resources it owns, including publisher registries.
     * Callers must close custom registries they create using {@link MetricsFactory}. The
     * {@link io.helidon.service.registry.ServiceRegistry} closes its shared registry; application code must not close that
     * registry.
     */
    void close();

    /**
     * Returns whether the specified meter is enabled.
     * <p>
     * The default implementation delegates to the deprecated overload for compatibility with existing implementations.
     * Implementations should override this method to apply provider-neutral enablement rules. Legacy implementations
     * which override the deprecated overload continue to work.
     *
     * @param name name of the meter to check
     * @param tags tags of the meter to check
     * @return true if the meter is enabled; false otherwise
     */
    default boolean isMeterEnabled(String name, Map<String, String> tags) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(tags);

        DeprecationSupport.requireOverride(this,
                                           MeterRegistry.class,
                                           "isMeterEnabled",
                                           String.class,
                                           Map.class,
                                           Optional.class);

        return isMeterEnabled(name, tags, Optional.empty());
    }

    /**
     * Returns whether the specified meter is enabled, ignoring the scope.
     * <p>
     * The default implementation delegates to {@link #isMeterEnabled(String, Map)}. Implementations must override either
     * this method or the scope-free overload.
     *
     * @param name  name of the meter to check
     * @param tags  tags of the meter to check
     * @param scope ignored; must not be {@code null}
     * @return true if the meter is enabled; false otherwise
     * @deprecated Use {@link #isMeterEnabled(String, Map)}. Scope is ignored and this method will be removed.
     */
    @Deprecated(forRemoval = true, since = "27.0.0")
    default boolean isMeterEnabled(String name, Map<String, String> tags, Optional<String> scope) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(tags);
        Objects.requireNonNull(scope);
        return isMeterEnabled(name, tags);
    }

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
     * Removes a previously-registered meter with the specified ID, ignoring the scope.
     *
     * @param id    ID for the meter to remove
     * @param scope ignored; must not be {@code null}
     * @return the removed meter; empty if the specified ID does not correspond to a registered meter
     * @deprecated Use {@link #remove(Meter.Id)}. Scope is ignored and this method will be removed.
     */
    @Deprecated(forRemoval = true, since = "27.0.0")
    default Optional<Meter> remove(Meter.Id id, String scope) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(scope);
        return remove(id);
    }

    /**
     * Removes a previously-registered meter with the specified name and tags.
     *
     * @param name meter name
     * @param tags tags for further identifying the meter
     * @return the removed meter; empty if the specified name and tags do not correspond to a registered meter
     */
    Optional<Meter> remove(String name, Iterable<Tag> tags);

    /**
     * Removes a previously-registered meter with the specified name and tags, ignoring the scope.
     *
     * @param name  meter name
     * @param tags  tags for further identifying the meter
     * @param scope ignored; must not be {@code null}
     * @return the removed meter; empty if the specified name and tags do not correspond to a registered meter
     * @deprecated Use {@link #remove(String, Iterable)}. Scope is ignored and this method will be removed.
     */
    @Deprecated(forRemoval = true, since = "27.0.0")
    default Optional<Meter> remove(String name, Iterable<Tag> tags, String scope) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(tags);
        Objects.requireNonNull(scope);
        return remove(name, tags);
    }

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
     * Metrics factory that created this registry.
     * <p>
     * The default implementation returns the shared metrics factory from the service registry for compatibility.
     * Implementations created by a different factory must override this method and return that factory.
     *
     * @return metrics factory
     */
    default MetricsFactory metricsFactory() {
        return Services.get(MetricsFactory.class);
    }

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
