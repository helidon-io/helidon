/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

import java.util.Arrays;

import io.helidon.common.config.Config;

/**
 * Sampler types valid for OpenTelemetry tracing.
 * <p>
 * This enum intentionally omits {@code jaeger-remote} as that requires an additional library.
 * Users who want to use that sampler can add the dependency themselves and prepare the OpenTelemetry
 * objects explicitly rather than using this builder.
 * <p>
 * Helidon recognizes the string values as documented in the OpenTelemetry documentation
 * <a href="https://opentelemetry.io/docs/languages/java/configuration/#properties-traces">Properties: traces; Properties
 * for sampler</a>.
 */
enum SamplerType {
    /**
     * Always on sampler.
     */
    ALWAYS_ON("always_on"),

    /**
     * Always off sampler.
     */
    ALWAYS_OFF("always_off"),

    /**
     * Trace ID ratio-based sampler.
     */
    TRACE_ID_RATIO("traceidratio"),

    /**
     * Parent-based always-on sampler.
     */
    PARENT_BASED_ALWAYS_ON("parentbased_always_on"),

    /**
     * Parent-based always-off sampler.
     */
    PARENT_BASED_ALWAYS_OFF("parentbased_always_off"),

    /**
     * Parent-based trace ID ration-based sampler.
     */
    PARENT_BASED_TRACE_ID_RATIO("parentbased_traceidratio");

    static final String DEFAULT_NAME = "PARENT_BASED_ALWAYS_ON";
    static final SamplerType DEFAULT = PARENT_BASED_ALWAYS_ON;

    private final String name;

    SamplerType(String name) {
        this.name = name;
    }

    static SamplerType from(String value) {
        for (SamplerType samplerType : SamplerType.values()) {
            if (samplerType.name.equals(value) || samplerType.name().equals(value)) {
                return samplerType;
            }
        }
        throw new IllegalArgumentException("Unknown sampler type: " + value + "; expected one of "
                                                   + Arrays.toString(SamplerType.values())
                                                   + "(or in lower case)");
    }

    static SamplerType from(Config config) {
        return config.asString().map(SamplerType::from).orElseThrow();
    }
}
