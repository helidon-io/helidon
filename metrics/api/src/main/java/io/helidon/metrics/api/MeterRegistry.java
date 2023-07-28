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

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/**
 * Manages the look-up and registration of meters.
 *
 * <p>
 *     This interface supports two types of retrieval (using {@link io.helidon.metrics.api.Counter} as an example):
 *     <ul>
 *         <li>retrieve or create - {@link #counter(String, Iterable)} - returns the meter if it was previously
 *         registered, otherwise creates and registers the meter</li>
 *         <li>retrieve only - {@link #getCounter(String, Iterable)} - returns an {@link java.util.Optional}
 *         for the meter, empty if the meter has not been registered and non-empty if it has been registered.</li>
 *     </ul>
 * <p>
 *     The meter registry uniquely identifies each meter by its name and tags (if any).
 * </p>
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
    Iterable<Meter> meters(Predicate<Meter> filter);

    /**
     * Creates a new or locates a previously-registered {@link io.helidon.metrics.api.Counter} by its
     * {@link io.helidon.metrics.api.Meter.Id}.
     *
     * @param id {@link io.helidon.metrics.api.Meter.Id} for the counter
     * @return new or previously-registered counter
     */
    Counter counter(Meter.Id id);

    /**
     * Creates a new or locates a previously-registered {@link io.helidon.metrics.api.Counter} by its
     * {@link io.helidon.metrics.api.Meter.Id}.
     *
     * @param id {@link io.helidon.metrics.api.Meter.Id} for the counter
     * @param target object which maintains the counter value
     * @param fn function which, when applied to the target, yields the counter value
     * @return new or previously-registered counter
     * @param <T> type of the target object
     */
    <T> Counter counter(Meter.Id id,
                        T target,
                        ToDoubleFunction<T> fn);
    /**
     * Creates a new or locates a previously-registered {@link io.helidon.metrics.api.Counter} by its name and tags.
     *
     * @param name counter name
     * @param tags tags which further identify the counter
     * @return new or previously-registered counter
     */
    Counter counter(String name,
                    Iterable<Tag> tags);

    /**
     * Creates a new or locates a previously-registered {@link io.helidon.metrics.api.Counter} by its name and tags.
     *
     * @param name counter name
     * @param tags key/value pairs for further identifying the counter; MUST be an even number of arguments
     * @return new or previously-registered counter
     */
    Counter counter(String name,
                    String... tags);

    /**
     * Creates a new or locates a previously-registered {@link io.helidon.metrics.api.Counter} by its name and tags.
     *
     * @param name counter name
     * @param tags tags which further identify the counter
     * @param baseUnit unit for the counter
     * @param description counter description
     * @return new or previously-registered counter
     */
    Counter counter(String name,
                    Iterable<Tag> tags,
                    String baseUnit,
                    String description);

    /**
     * Locates a previously-registered {@link io.helidon.metrics.api.Counter} by its name and tags or registers a new one
     * which wraps an external target object which provides the counter value.
     *
     * <p>
     *     The counter returned rejects attempts to increment its value because the external object, not the counter itself,
     *     maintains the value.
     * </p>
     *
     * @param id {@link io.helidon.metrics.api.Meter.Id} for the counter
     * @param baseUnit unit for the counter
     * @param description counter description
     * @param target object which provides the counter value
     * @param fn function which, when applied to the target, returns the counter value
     * @return the target object
     * @param <T> type of the target object
     */
    <T> Counter counter(Meter.Id id,
                        String baseUnit,
                        String description,
                        T target,
                        ToDoubleFunction<T> fn);

    /**
     * Registers a new or locates a previously-registered counter, using the global registry, which tracks a monotonically
     * increasing value that is maintained by an external object, not a counter furnished by the meter registry itself.
     *
     * <p>
     *     The counter returned rejects attempts to increment its value because the external object, not the counter itself,
     *     maintains the value.
     * </p>
     *
     * @param name counter name
     * @param tags further identification of the counter
     * @param target object which, when the function is applied, yields the counter value
     * @param fn function which produces the counter value
     * @return new or existing counter
     * @param <T> type of the object which furnishes the counter value
     */
    <T> Counter counter(String name,
                        Iterable<Tag> tags,
                        T target,
                        ToDoubleFunction<T> fn);

    /**
     * Locates a previously-registered {@link io.helidon.metrics.api.Counter} by its name and tags or registers a new one
     * which wraps an external target object which provides the counter value.
     *
     * <p>
     *     The counter returned rejects attempts to increment its value because the external object, not the counter itself,
     *     maintains the value.
     * </p>
     *
     * @param name counter name
     * @param tags tags which further identify the counter
     * @param baseUnit unit for the counter
     * @param description counter description
     * @param target object which provides the counter value
     * @param fn function which, when applied to the target, returns the counter value
     * @return the target object
     * @param <T> type of the target object
     */
    <T> Counter counter(String name,
                        Iterable<Tag> tags,
                        String baseUnit,
                        String description,
                        T target,
                        ToDoubleFunction<T> fn);

    /**
     * Locates a previously-registered {@link io.helidon.metrics.api.Counter} by its name and tags.
     *
     * @param name counter name
     * @param tags tags for further identifying the counter
     * @return {@link java.util.Optional} of {@code Counter} if found; {@code Optional.empty()} otherwise
     */
    Optional<Counter> getCounter(String name,
                                 Iterable<Tag> tags);

    /**
     * Locates a previously-registered {@link io.helidon.metrics.api.Counter} by its name and tags.
     *
     * @param name counter name
     * @param tags tags for further identifying the counter
     * @return {@link java.util.Optional} of {@code Counter} if found; {@code Optional.empty()} otherwise
     */
    Optional<Counter> getCounter(String name,
                                 String... tags);

    /**
     * Creates a new or locates a previously-registered {@link io.helidon.metrics.api.DistributionSummary}.
     *
     * @param id {@link io.helidon.metrics.api.Meter.Id} for the summary
     * @param baseUnit unit for the counter
     * @param description counter description
     * @param distributionStatisticsConfig configuration governing distribution statistics calculations
     * @param scale scaling factor to apply to every sample recorded by the summary
     * @return new or existing summary
     */
    DistributionSummary summary(Meter.Id id,
                                String baseUnit,
                                String description,
                                DistributionStatisticsConfig distributionStatisticsConfig,
                                double scale);

    /**
     * Creates a new or locates a previously-registered {@link io.helidon.metrics.api.DistributionSummary}.
     *
     * @param name summary name
     * @param tags tags for further identifying the summary
     * @return new or existing distribution summary
     */
    DistributionSummary summary(String name,
                                Iterable<Tag> tags);

    /**
     * Locates a previously-registered {@link io.helidon.metrics.api.DistributionSummary} by its name and tags.
     *
     * @param name summary name to locate
     * @param tags tags for further identifying the summary
     * @return {@link java.util.Optional} of {@code DistributionSummary} if found; {@code Optional.empty()} otherwise
     */
    Optional<DistributionSummary> getSummary(String name,
                                             Iterable<Tag> tags);

    /**
     * Creates a new or locates a previously-registered {@link io.helidon.metrics.api.DistributionSummary}.
     *
     * @param name summary name
     * @param tags key/value pairs for further identifying the summary; MUST be an even number of arguments
     * @return new or existing distribution summary
     */
    DistributionSummary summary(String name,
                                String... tags);

    /**
     * Locates a previously-registered {@link io.helidon.metrics.api.DistributionSummary} by its name and tags.
     *
     * @param name summary name to locate
     * @param tags tags for further identifying the summary
     * @return {@link java.util.Optional} of {@code DistributionSummary} if found; {@code Optional.empty()} otherwise
     */
    Optional<DistributionSummary> getSummary(String name,
                                             Tag... tags);

    /**
     * Creates a new or locates a previously-registered {@link io.helidon.metrics.api.Timer}.
     *
     * @param name timer name
     * @param tags tags for further identifying the timer
     * @param baseUnit unit for the timer
     * @param description timer description
     * @param distributionStatisticsConfig configuration governing distribution statistics calculations
     * @return new or existing timer
     */
    Timer timer(String name,
                Iterable<Tag> tags,
                String baseUnit,
                String description,
                DistributionStatisticsConfig distributionStatisticsConfig);

    /**
     * Creates a new or locates a previously-registered {@link io.helidon.metrics.api.Timer}.
     *
     * @param name timer name
     * @param tags tags for further identifying the timer
     * @return new or existing timer
     */
    Timer timer(String name,
                Iterable<Tag> tags);

    /**
     * Locates a previously-registered {@link io.helidon.metrics.api.Timer} by its name and tags.
     *
     * @param name timer name
     * @param tags tags for further identifying the timer
     * @return {@link java.util.Optional} of {@code Timer} if found; {@code Optional.empty()} otherwise
     */
    Optional<Timer> getTimer(String name,
                             Iterable<Tag> tags);

    /**
     * Creates a new or locates a previously-registered {@link io.helidon.metrics.api.Timer}.
     *
     * @param name timer name
     * @param tags tag key/value pairs for further identifying the timer; MUST be an even number of arguments
     * @return new or existing timer
     */
    Timer timer(String name,
                String... tags);

    /**
     * Locates a previously-registered {@link io.helidon.metrics.api.Timer} by its name and tags.
     *
     * @param name timer name
     * @param tags tags for further identifying the timer
     * @return {@link java.util.Optional} of {@code Timer} if found; {@code Optional.empty()} otherwise
     */
    Optional<Timer> getTimer(String name,
                             Tag... tags);

    /**
     * Locates or registers a {@link io.helidon.metrics.api.Gauge} that reports the value of the object returned by applying
     * the specified {@code valueFunction}.
     *
     * @param name name of the gauge
     * @param tags tags for further identifying the gauge
     * @param baseUnit base unit for the gauge
     * @param description  gauge description
     * @param stateObject object to which the {@code valueFunction} is applied to obtain the gauge's value
     * @param valueFunction function which, when applied to the {@code stateObject}, produces an instantaneous gauge value
     * @param <T> type of the state object which yields the gauge's value
     * @return state object
     */
    <T> T gauge(String name,
                Iterable<Tag> tags,
                String baseUnit,
                String description,
                T stateObject,
                ToDoubleFunction<T> valueFunction);

    /**
     * Locates a previously-registered {@link io.helidon.metrics.api.Gauge} by its name and tags.
     *
     * @param name name of the gauge
     * @param tags tags for further identifying the gauge
     * @return {@link java.util.Optional} of {@code Gauge} if found; {@code Optional.empty()} otherwise
     */
    Optional<Gauge> getGauge(String name,
                             Iterable<Tag> tags);

    /**
     * Locates a previously-registered {@link io.helidon.metrics.api.Gauge} by its name and tags.
     *
     * @param name name of the gauge
     * @param tags tags for further identifying the gauge
     * @return {@link java.util.Optional} of {@code Gauge} if found; {@code Optional.empty()} otherwise
     */
    Optional<Gauge> getGauge(String name,
                             Tag... tags);

    /**
     * Locates or registers a {@link io.helidon.metrics.api.Gauge} that reports the value of the specified {@link Number}
     * instance.
     *
     * @param name name of the gauge
     * @param tags tags for further identifying the gauge
     * @param number thread-safe implementation of {@link Number} used to access the value
     * @param <T> type of the number from which the gauge value is extracted
     * @return number argument passed (so the registration can be done as part of an assignment statement)
     */
    <T extends Number> T gauge(String name,
                               Iterable<Tag> tags,
                               T number);

    /**
     * Locates or registers a {@link io.helidon.metrics.api.Gauge} that reports the value of the {@link Number}.
     *
     * @param name name of the gauge
     * @param number thread-safe implementation of {@link Number} used to access the gauge's value
     * @param <T> type of the state object from which the gauge value is extracted
     * @return number argument passed (so the registration can be done as part of an assignment statement)
     */
    <T extends Number> T gauge(String name,
                               T number);

    /**
     * Locates or registers a {@link io.helidon.metrics.api.Gauge} that reports the value of the object by applying the specified
     * function.
     *
     * @param name name of the gauge
     * @param tags tags for further identifying the gauge
     * @param stateObject state object used to compute a value
     * @param valueFunction function which, when applied to the {@code stateObject}, yields an instantaneous gauge value
     * @param <T> type of the state object from which the gauge value is extracted
     * @return state object argument passed (so the registration can be done as part
     * of an assignment statement)
     */
    <T> T gauge(String name,
                Iterable<Tag> tags,
                T stateObject,
                ToDoubleFunction<T> valueFunction);

    /**
     * Locates or registers a {@link io.helidon.metrics.api.Gauge} that reports the value of the object by applying the specified
     * function.
     *
     * @param name name of the gauge
     * @param stateObject state object used to compute a value
     * @param valueFunction function which, when applied to the {@code stateObject}, yields an instantaneous gauge value
     * @param <T> type of the state object from which the gauge value is extracted
     * @return state object argument passed (so the registration can be done as part
     * of an assignment statement)
     */
    <T> T gauge(String name,
                T stateObject,
                ToDoubleFunction<T> valueFunction);
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
