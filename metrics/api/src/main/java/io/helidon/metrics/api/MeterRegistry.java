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
 *         <li>retrieve or create - {@link #counter(io.helidon.metrics.api.Meter.Id)} - returns the meter if it was previously
 *         registered, otherwise creates and registers the meter</li>
 *         <li>retrieve only - {@link #getCounter(io.helidon.metrics.api.Meter.Id)}</li> - returns an {@link java.util.Optional}
 *         for the meter, empty if the meter has not been registered and non-empty if it has been registered.
 *     </ul>
 * </p>
 * <p>
 *     For most meter types, this interface provides two general variants of the retrieve-or-create-method for each meter
 *     (again using {@link io.helidon.metrics.api.Counter} as an example):
 *     <ul>
 *         <li>by ID - {@link #counter(io.helidon.metrics.api.Meter.Id)} - the caller prepares the ID</li>
 *         <li>by name and tags - {@link #counter(String, Iterable)} and {@link #counter(String, String...)}- the caller need not
 *     prepare the ID</li>
 *     </ul>
 * </p>
 */
public interface MeterRegistry {

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
     * Creates a new or locates a previously-registered {@link io.helidon.metrics.api.Counter} by its ID.
     *
     * @param id {@link Meter.Id} to register or locate
     * @return new or previously-registered counter
     */
    Counter counter(Meter.Id id);

    /**
     * Locates a previously-registered {@link io.helidon.metrics.api.Counter} by its ID or registers a new one
     * which wraps an external target object which provides the counter value.
     *
     * <p>
     *     The counter returned rejects attempts to increment its value because the external object, not the counter itself,
     *     maintains the value.
     * </p>
     *
     * @param id {@link Meter.Id} to register or locate
     * @param target object which provides the counter value
     * @param fn function which, when applied to the target, returns the counter value
     * @return the target object
     * @param <T> type of the target object
     */
    <T> Counter counter(Meter.Id id, T target, ToDoubleFunction<T> fn);

    /**
     * Locates a previous-registered {@link io.helidon.metrics.api.Counter} by its ID.
     *
     * @param id {@link io.helidon.metrics.api.Meter.Id} to locate
     * @return {@link java.util.Optional} of {@code Counter} if found; {@code Optional.empty()} otherwise
     */
    Optional<Counter> getCounter(Meter.Id id);

    /**
     * Creates a new or locates a previously-registered {@link io.helidon.metrics.api.Counter}.
     *
     * @param name counter name
     * @param tags tags for further identifying the counter
     * @return new or existing counter
     */
    Counter counter(String name, Iterable<Tag> tags);

    /**
     * Locates a previously-registered {@link io.helidon.metrics.api.Counter} by its name and tags.
     *
     * @param name counter name
     * @param tags counter {@link io.helidon.metrics.api.Tag} instances which further identify the counter
     * @return {@link java.util.Optional} of {@code Counter} if found; {@code Optional.empty()} otherwise
     */
    Optional<Counter> getCounter(String name, Iterable<Tag> tags);

    /**
     * Creates a new or locates a previously-registered {@link io.helidon.metrics.api.Counter}.
     *
     * @param name counter name
     * @param tags key/value pairs; MUST be an even number of arguments
     * @return new or existing counter
     */
    Counter counter(String name, String... tags);

    /**
     * Locates a previously-registered {@link io.helidon.metrics.api.Counter} by its name and tags.
     *
     * @param name counter name
     * @param tags counter {@link io.helidon.metrics.api.Tag} instances which further identify the counter
     * @return {@link java.util.Optional} of {@code Counter} if found; {@code Optional.empty()} otherwise
     */
    Optional<Counter> getCounter(String name, String... tags);

    /**
     * Locates a previously-registered {@link io.helidon.metrics.api.Counter} by its name dnd tags or registers a new one
     * which wraps an external target object which provides the counter value.
     *
     * <p>
     *     The counter returned rejects attempts to increment its value because the external object, not the counter itself,
     *     maintains the value.
     * </p>
     *
     * @param name counter name
     * @param tags {@link io.helidon.metrics.api.Tag} instances which further identify the counter
     * @param target object which provides the counter value
     * @param fn function which, when applied to the target, returns the counter value
     * @return the target object
     * @param <T> type of the target object
     */
    <T> Counter counter(String name, Iterable<Tag> tags, T target, ToDoubleFunction<T> fn);

    /**
     * Creates a new or locates a previously-registered {@link io.helidon.metrics.api.DistributionSummary}.
     *
     * @param id {@link Meter.Id} for the summary
     * @param distributionStatisticsConfig configuration governing distribution statistics calculations
     * @param scale scaling factor to apply to every sample recorded by the summary
     * @return new or existing summary
     */
    DistributionSummary summary(Meter.Id id,
                                DistributionStatisticsConfig distributionStatisticsConfig,
                                double scale);

    /**
     * Locates a previously-registered {@link io.helidon.metrics.api.DistributionSummary} by its ID.
     *
     * @param id {@link io.helidon.metrics.api.Meter.Id} to locate
     * @return {@link java.util.Optional} of {@code DistributionSummary} if found; {@code Optional.empty()} otherwise
     */
    Optional<DistributionSummary> getSummary(Meter.Id id);

    /**
     * Creates a new or locates a previously-registered {@link io.helidon.metrics.api.DistributionSummary}.
     *
     * @param name summary name
     * @param tags tags for further identifying the summary
     * @return new or existing distribution summary
     */
    DistributionSummary summary(String name, Iterable<Tag> tags);

    /**
     * Locates a previously-registered {@link io.helidon.metrics.api.DistributionSummary} by its name and tags.
     *
     * @param name summary name to locate
     * @param tags {@link io.helidon.metrics.api.Tag} instances which further identify the summary
     * @return {@link java.util.Optional} of {@code DistributionSummary} if found; {@code Optional.empty()} otherwise
     */
    Optional<DistributionSummary> getSummary(String name, Iterable<Tag> tags);

    /**
     * Creates a new or locates a previously-registered {@link io.helidon.metrics.api.DistributionSummary}.
     *
     * @param name summary name
     * @param tags key/value pairs; MUST be an even number of arguments
     * @return new or existing distribution summary
     */
    DistributionSummary summary(String name, String... tags);

    /**
     * Locates a previously-registered {@link io.helidon.metrics.api.DistributionSummary} by its name and tags.
     *
     * @param name summary name to locate
     * @param tags {@link io.helidon.metrics.api.Tag} instances which further identify the summary
     * @return {@link java.util.Optional} of {@code DistributionSummary} if found; {@code Optional.empty()} otherwise
     */
    Optional<DistributionSummary> getSummary(String name, Tag... tags);

    /**
     * Creates a new or locates a previously-registered {@link io.helidon.metrics.api.Timer}.
     *
     * @param id ID for the timer
     * @param distributionStatisticsConfig configuration governing distribution statistics calculations
     * @return new or existing timer
     */
    Timer timer(Meter.Id id, DistributionStatisticsConfig distributionStatisticsConfig);

    /**
     * Locates a previously-registered {@link io.helidon.metrics.api.Timer} by its ID.
     *
     * @param id ID for the timer
     * @return {@link java.util.Optional} of {@code Timer} if found; {@code Optional.empty()} otherwise
     */
    Optional<Timer> getTimer(Meter.Id id);

    /**
     * Creates a new or locates a previously-registered {@link io.helidon.metrics.api.Timer}.
     *
     * @param name timer name
     * @param tags tags for further identifying the timer
     * @return new or existing timer
     */
    Timer timer(String name, Iterable<Tag> tags);

    /**
     * Locates a previously-registered {@link io.helidon.metrics.api.Timer} by its name and tags.
     *
     * @param name timer name
     * @param tags {@link io.helidon.metrics.api.Tag} instances which further identify the timer
     * @return {@link java.util.Optional} of {@code Timer} if found; {@code Optional.empty()} otherwise
     */
    Optional<Timer> getTimer(String name, Iterable<Tag> tags);

    /**
     * Creates a new or locates a previously-registered {@link io.helidon.metrics.api.Timer}.
     *
     * @param name timer name
     * @param tags key/value pairs; MUST be an even number of arguments
     * @return new or existing timer
     */
    Timer timer(String name, String... tags);

    /**
     * Locates a previously-registered {@link io.helidon.metrics.api.Timer} by its name and tags.
     *
     * @param name timer name
     * @param tags {@link io.helidon.metrics.api.Tag} instances which further identify the timer
     * @return {@link java.util.Optional} of {@code Timer} if found; {@code Optional.empty()} otherwise
     */
    Optional<Timer> getTimer(String name, Tag... tags);

    /**
     * Locates or registers a {@link io.helidon.metrics.api.Gauge} that reports the value of the object returned by applying
     * the specified {@code valueFunction}.
     *
     * @param id {@link io.helidon.metrics.api.Meter.Id} of the gauge
     * @param stateObject object to which the {@code valueFunction} is applied to obtain the gauge's value
     * @param fn function which, when applied to the {@code stateObject}, produces an instantaneous gauge value
     * @param <T> type of the state object which yields the gauge's value
     * @return state object
     */
    <T> T gauge(Meter.Id id,
                T stateObject,
                ToDoubleFunction<T> fn);

    /**
     * Locates a previously-registered {@link io.helidon.metrics.api.Gauge} by its ID.
     *
     * @param id ID for the gauge
     * @return {@link java.util.Optional} of {@code Gauge} if found; {@code Optional.empty()} otherwise
     */
    Optional<Gauge> getGauge(Meter.Id id);

    /**
     * Locates or registers a {@link io.helidon.metrics.api.Gauge} that reports the value of the object returned by applying
     * the specified {@code valueFunction}.
     *
     * @param name name of the gauge
     * @param tags further identification of the gauge
     * @param stateObject object to which the {@code valueFunction} is applied to obtain the gauge's value
     * @param valueFunction function which, when applied to the {@code stateObject}, produces an instantaneous gauge value
     * @param <T> type of the state object which yields the gauge's value
     * @return state object
     */
    <T> T gauge(String name,
                Iterable<Tag> tags,
                T stateObject,
                ToDoubleFunction<T> valueFunction);

    /**
     * Locates a previously-registered {@link io.helidon.metrics.api.Gauge} by its name and tags.
     *
     * @param name name of the gauge
     * @param tags {@link Tag} instances which further identify the gauge
     * @return {@link java.util.Optional} of {@code Gauge} if found; {@code Optional.empty()} otherwise
     */
    Optional<Gauge> getGauge(String name, Iterable<Tag> tags);

    /**
     * Locates a previously-registered {@link io.helidon.metrics.api.Gauge} by its name and tags.
     *
     * @param name name of the gauge
     * @param tags {@link Tag} instances which further identify the gauge
     * @return {@link java.util.Optional} of {@code Gauge} if found; {@code Optional.empty()} otherwise
     */
    Optional<Gauge> getGauge(String name, Tag... tags);

    /**
     * Locates or registers a {@link io.helidon.metrics.api.Gauge} that reports the value of the specified {@link Number}
     * instance.
     *
     * @param name name of the gauge
     * @param tags further identifies the gauge
     * @param number thread-safe implementation of {@link Number} used to access the value
     * @param <T> type of the number from which the gauge value is extracted
     * @return number argument passed (so the registration can be done as part of an assignment statement)
     */
    <T extends Number> T gauge(String name, Iterable<Tag> tags, T number);

    /**
     * Locates or registers a {@link io.helidon.metrics.api.Gauge} that reports the value of the {@link Number}.
     *
     * @param name name of the gauge
     * @param number thread-safe implementation of {@link Number} used to access the gauge's value
     * @param <T> type of the state object from which the gauge value is extracted
     * @return number argument passed (so the registration can be done as part of an assignment statement)
     */
    <T extends Number> T gauge(String name, T number);

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
     * @param id {@link Meter.Id} of the meter to remove
     * @return the removed meter; null if the specified meter ID does not correspond to a registered meter
     */
    Meter remove(Meter.Id id);
}
