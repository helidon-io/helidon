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

import java.util.function.Supplier;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.config.Config;
import io.helidon.service.registry.Service;
import io.helidon.tracing.Tracer;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;

@Service.Singleton
@Service.RunLevel(Service.RunLevel.STARTUP)
@Weight(Weighted.DEFAULT_WEIGHT - 80)
class HelidonTracingBasedOpenTelemetryServiceFactory implements Supplier<OpenTelemetry> {

    private final Config config;

    @Service.Inject
    HelidonTracingBasedOpenTelemetryServiceFactory(Config config) {
        this.config = config;
    }

    @Override
    public OpenTelemetry get() {
        OpenTelemetry result;
        if (config.get(OpenTelemetryTracerConfigBlueprint.TRACING_CONFIG_KEY).exists()) {
            /*
            Creating the impl also initializes the global tracer if the config specifies global = true.
             */
            var helidonOtelTracer = OpenTelemetryTracerBuilder.create()
                    .config(config.get(OpenTelemetryTracerConfigBlueprint.TRACING_CONFIG_KEY))
                    .build();

            try {
                result = helidonOtelTracer.prototype().openTelemetry();
            } catch (Exception e) {
                /*
                The configuration set global to "true" and so OpenTelemetryTracerImpl tried to tell OTel to use the
                OpenTelemetry instance created from the tracing config as the global one. The exception probably is because
                the OTel global instance was already set. In that case, register the current global instance in our
                service registry so code that retrieves it from there uses the actual OTel global instance.
                 */
                result = GlobalOpenTelemetry.get();
            }
        } else {
            /*
            For backward compatibility with unconfigured OpenTelemetry tracing support, if the tracing config node is absent
            let OpenTelemetry decide (probably either the no-op implementation or one using the OpenTelemetry auto-configure
            feature.)
             */

            result = GlobalOpenTelemetry.get();
            var oTelTracerConfig = OpenTelemetryTracerBuilder.create()
                    .serviceName("helidon-service")
                    .openTelemetry(result)
                    .delegate(result.getTracer("helidon-service"))
                    .build();
            Tracer.global(oTelTracerConfig);
        }

        return result;
    }
}
