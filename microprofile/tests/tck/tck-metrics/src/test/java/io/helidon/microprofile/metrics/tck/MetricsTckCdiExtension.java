/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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

import io.helidon.microprofile.cdi.RuntimeStart;

import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.Extension;
import org.eclipse.microprofile.metrics.MetricRegistry;

public class MetricsTckCdiExtension implements Extension {

    void before(@Observes BeforeBeanDiscovery discovery) {
        discovery.addAnnotatedType(ArrayParamConverterProvider.class, ArrayParamConverterProvider.class.getSimpleName());
    }

    void clear(@Observes @RuntimeStart Object event, MetricRegistry appRegistry) {

        // Erase the application registry so it is clear at the start of each TCK test.
        appRegistry.getNames()
                .forEach(appRegistry::remove);
    }
}
