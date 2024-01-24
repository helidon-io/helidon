/*
 * Copyright (c) 2020, 2024 Oracle and/or its affiliates.
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
package io.helidon.microprofile.metrics.tck;

import java.util.Set;

import io.helidon.microprofile.metrics.RegistryFactory;
import io.helidon.microprofile.server.CatchAllExceptionMapper;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.BeforeShutdown;
import jakarta.enterprise.inject.spi.Extension;
import org.eclipse.microprofile.metrics.MetricRegistry;

public class MetricsTckCdiExtension implements Extension {

    void before(@Observes BeforeBeanDiscovery discovery) {
        discovery.addAnnotatedType(ArrayParamConverterProvider.class, ArrayParamConverterProvider.class.getSimpleName());
        discovery.addAnnotatedType(CatchAllExceptionMapper.class, CatchAllExceptionMapper.class.getSimpleName());
    }

    void clear(@Observes BeforeShutdown shutdown) {
        // Erase the metric registries for all scopes so they are clear at the start of the next TCK test.

        RegistryFactory.getInstance().scopes().stream()
                .map(RegistryFactory.getInstance()::getRegistry)
                .forEach(metricRegistry ->
                                 metricRegistry.getNames().forEach(metricRegistry::remove)
                );
    }
}
