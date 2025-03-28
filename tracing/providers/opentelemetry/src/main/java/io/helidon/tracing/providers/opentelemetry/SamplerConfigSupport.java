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

package io.helidon.tracing.providers.opentelemetry;

import io.helidon.builder.api.Prototype;

import io.opentelemetry.sdk.trace.samplers.Sampler;

class SamplerConfigSupport {

    /**
     * Creates an OpenTelemetry {@link io.opentelemetry.sdk.trace.samplers.Sampler} from the Helidon OpenTelemetry
     * {@linkplain io.helidon.tracing.providers.opentelemetry.SamplerConfig sampler samplerConfig}.
     *
     * @param samplerConfig config for the sampler
     * @return OpenTelemetry {@code Sampler}
     */
    @Prototype.PrototypeMethod
    static Sampler sampler(SamplerConfig samplerConfig) {
        return switch (samplerConfig.type()) {
            case ALWAYS_ON -> Sampler.alwaysOn();
            case ALWAYS_OFF -> Sampler.alwaysOff();
            case TRACE_ID_RATIO -> Sampler.traceIdRatioBased(ensureParam(samplerConfig).doubleValue());
            case PARENT_BASED_ALWAYS_OFF -> Sampler.parentBased(Sampler.alwaysOff());
            case PARENT_BASED_ALWAYS_ON -> Sampler.parentBased(Sampler.alwaysOn());
            case PARENT_BASED_TRACE_ID_RATIO -> Sampler.parentBased(Sampler.traceIdRatioBased(ensureParam(samplerConfig).doubleValue()));
        };
    }

    private static Number ensureParam(SamplerConfig config) {
        if (config.param() == null) {
            throw new IllegalArgumentException("Sampler param is required for sampler type " + config.type());
        }
        return config.param();
    }
}
