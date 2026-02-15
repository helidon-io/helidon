/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.telemetry.otelconfig;

import java.util.Optional;
import java.util.function.Predicate;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

import io.opentelemetry.sdk.metrics.Aggregation;
import io.opentelemetry.sdk.metrics.InstrumentSelector;

/**
 * Settings for an OpenTelemetry metrics view registration.
 */
@Prototype.Blueprint
@Prototype.Configured
@Prototype.CustomMethods(ViewRegistrationConfigSupport.CustomMethods.class)
interface ViewRegistrationConfigBlueprint {

    /**
     * Metrics view name.
     *
     * @return metric view name
     */
    @Option.Configured
    Optional<String> name();

    /**
     * Metric view description.
     *
     * @return metric view description
     */
    @Option.Configured
    Optional<String> description();

    /**
     * Aggregation for the metric view, configurable as an {@link io.helidon.telemetry.otelconfig.AggregationType}:
     * {@code DROP, DEFAULT, SUM, LAST_VALUE, EXPLICIT_BUCKET_HISTOGRAM, BASE2_EXPONENTIAL_BUCKET_HISTOGRAM}.
     *
     * @return aggregation for the metric view
     */
    @Option.Configured
    Aggregation aggregation();

    /**
     * Attribute name filter, configurable as a string compiled as a regular expression using {@link java.util.regex.Pattern}.
     *
     * @return attribute name filter
     */
    @Option.Configured
    Optional<Predicate<String>> attributeFilter();

    /**
     * Instrument selector, configurable using {@link io.helidon.telemetry.otelconfig.InstrumentSelectorConfig}.
     *
     * @return instrument selector
     */
    @Option.Configured
    InstrumentSelector instrumentSelector();

    /**
     * Cardinality limit.
     *
     * @return cardinality limit
     */
    @Option.Configured
    Optional<Integer> cardinalityLimit();

}
