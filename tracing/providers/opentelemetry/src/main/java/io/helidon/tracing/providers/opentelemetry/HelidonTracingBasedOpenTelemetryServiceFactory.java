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
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.common.types.TypeName;
import io.helidon.config.Config;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistry;

import io.opentelemetry.api.OpenTelemetry;

@Service.Singleton
@Service.RunLevel(Service.RunLevel.STARTUP)
class HelidonTracingBasedOpenTelemetryServiceFactory implements Supplier<OpenTelemetry> {
    private static final TypeName SERVICE_TYPE = TypeName.create(HelidonTracingBasedOpenTelemetryServiceFactory.class);

    private final ServiceRegistry registry;
    private final Config config;
    private final Supplier<List<OpenTelemetryOwnershipStrategy>> strategies;

    @Service.Inject
    HelidonTracingBasedOpenTelemetryServiceFactory(ServiceRegistry registry,
                                                   Config config,
                                                   Supplier<List<OpenTelemetryOwnershipStrategy>> strategies) {
        this.registry = registry;
        this.config = config;
        this.strategies = strategies;
    }

    @Override
    public OpenTelemetry get() {
        return HelidonOpenTelemetry.applicationOpenTelemetry(registry,
                                                            config,
                                                            strategies.get(),
                                                            () -> registryOpenTelemetry().orElse(null));
    }

    private Optional<OpenTelemetry> registryOpenTelemetry() {
        return registry.allServices(OpenTelemetry.class)
                .stream()
                .filter(service -> !SERVICE_TYPE.equals(service.serviceType()))
                .findFirst()
                .flatMap(service -> registry.get(service));
    }

    @Service.PreDestroy
    void preDestroy() {
        HelidonOpenTelemetry.clearApplicationTelemetry(registry);
    }
}
