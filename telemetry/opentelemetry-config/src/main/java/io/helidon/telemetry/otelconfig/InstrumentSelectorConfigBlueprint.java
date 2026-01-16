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

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

import io.opentelemetry.sdk.metrics.InstrumentType;

/**
 * Settings for OpenTelemetry instrument selectors.
 * <p>
 * OpenTelemetry allows all values to be null, so this blueprint declares all as optional.
 */
@Prototype.Blueprint
@Prototype.Configured
interface InstrumentSelectorConfigBlueprint {

    /**
     * Instrument type.
     *
     * @return instrument type
     */
    @Option.Configured
    Optional<InstrumentType> type();

    /**
     * Instrument name.
     *
     * @return name
     */
    @Option.Configured
    Optional<String> name();

    /**
     * Instrument unit.
     *
     * @return instrument unit
     */
    @Option.Configured
    Optional<String> unit();

    /**
     * Meter name.
     *
     * @return meter name
     */
    @Option.Configured
    Optional<String> meterName();

    /**
     * Meter version.
     *
     * @return meter version
     */
    @Option.Configured
    Optional<String> meterVersion();

    /**
     * Meter schema URL.
     *
     * @return meter schema URL
     */
    @Option.Configured
    Optional<String> meterSchemaUrl();

}
