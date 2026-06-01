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

import java.util.List;
import java.util.function.Supplier;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.config.Config;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.tracing.Tracer;

import io.opentelemetry.api.OpenTelemetry;

@Service.Singleton
@Weight(Weighted.DEFAULT_WEIGHT - 10)
class HelidonTracingBasedTracerServiceFactory implements Supplier<Tracer> {

    private final ServiceRegistry registry;
    private final Config config;
    private final Supplier<List<OpenTelemetryOwnershipStrategy>> strategies;
    private final Supplier<OpenTelemetry> openTelemetry;

    @Service.Inject
    HelidonTracingBasedTracerServiceFactory(ServiceRegistry registry,
                                            Config config,
                                            Supplier<List<OpenTelemetryOwnershipStrategy>> strategies,
                                            Supplier<OpenTelemetry> openTelemetry) {
        this.registry = registry;
        this.config = config;
        this.strategies = strategies;
        this.openTelemetry = openTelemetry;
    }

    @Override
    public Tracer get() {
        return HelidonOpenTelemetry.applicationTracer(registry, config, strategies.get(), openTelemetry.get());
    }

    @Service.PreDestroy
    void preDestroy() {
        HelidonOpenTelemetry.clearApplicationTelemetry(registry);
    }
}
