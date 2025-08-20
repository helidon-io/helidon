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
public enum SamplerType {

    /*
    Enum values are chosen to be the upper-case version of the OTel setting values so Helidon's built-in enum config mapping
    works.
     */

    /**
     * Always on sampler.
     */
    ALWAYS_ON,

    /**
     * Always off sampler.
     */
    ALWAYS_OFF,

    /**
     * Trace ID ratio-based sampler.
     */
    TRACEIDRATIO,

    /**
     * Parent-based always-on sampler.
     */
    PARENTBASED_ALWAYS_ON,

    /**
     * Parent-based always-off sampler.
     */
    PARENTBASED_ALWAYS_OFF,

    /**
     * Parent-based trace ID ration-based sampler.
     */
    PARENTBASED_TRACEIDRATIO;

    static final SamplerType DEFAULT = PARENTBASED_ALWAYS_ON;

}
