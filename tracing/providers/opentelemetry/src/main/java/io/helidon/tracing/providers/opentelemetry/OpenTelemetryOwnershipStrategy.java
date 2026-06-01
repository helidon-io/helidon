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

package io.helidon.tracing.providers.opentelemetry;

import java.util.Map;
import java.util.Objects;

import io.helidon.common.Api;
import io.helidon.config.Config;
import io.helidon.service.registry.Service;
import io.helidon.tracing.Tracer;

import io.opentelemetry.api.OpenTelemetry;

/**
 * Internal strategy for contributing an application-wide OpenTelemetry runtime candidate.
 */
@Api.Internal
@Service.Contract
public interface OpenTelemetryOwnershipStrategy {

    /**
     * Whether this strategy should own the application-wide OpenTelemetry runtime for the current configuration.
     *
     * @param rootConfig root configuration
     * @return whether this strategy is active
     */
    boolean active(Config rootConfig);

    /**
     * Service name configured for this ownership strategy.
     *
     * @param rootConfig root configuration
     * @return service name
     */
    String serviceName(Config rootConfig);

    /**
     * Creates this strategy's OpenTelemetry runtime candidate.
     *
     * @param rootConfig root configuration
     * @return OpenTelemetry runtime candidate
     */
    OpenTelemetry create(Config rootConfig);

    /**
     * Whether the selected application-wide {@link OpenTelemetry} instance should also be published to
     * {@link io.opentelemetry.api.GlobalOpenTelemetry}.
     *
     * @param rootConfig root configuration
     * @return whether Helidon should publish the selected instance to the OpenTelemetry global
     */
    default boolean globalOpenTelemetry(Config rootConfig) {
        Objects.requireNonNull(rootConfig);
        return false;
    }

    /**
     * Creates the Helidon tracer backed by the canonical OpenTelemetry runtime.
     *
     * @param rootConfig root configuration
     * @param openTelemetry canonical OpenTelemetry runtime
     * @return Helidon tracer
     */
    default Tracer createTracer(Config rootConfig, OpenTelemetry openTelemetry) {
        Objects.requireNonNull(rootConfig);
        Objects.requireNonNull(openTelemetry);
        String serviceName = serviceName(rootConfig);
        return HelidonOpenTelemetry.create(openTelemetry,
                                           openTelemetry.getTracer(serviceName),
                                           Map.of());
    }

    /**
     * Invoked once this strategy's runtime has been selected.
     *
     * @param rootConfig root configuration
     * @param openTelemetry canonical OpenTelemetry runtime
     */
    default void selected(Config rootConfig, OpenTelemetry openTelemetry) {
        Objects.requireNonNull(rootConfig);
        Objects.requireNonNull(openTelemetry);
    }
}
