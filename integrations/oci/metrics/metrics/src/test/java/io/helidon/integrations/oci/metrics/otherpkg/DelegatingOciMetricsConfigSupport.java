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

package io.helidon.integrations.oci.metrics.otherpkg;

import java.util.Optional;

import io.helidon.builder.api.Prototype;
import io.helidon.common.Errors;
import io.helidon.integrations.oci.metrics.OciMetricsConfig;
import io.helidon.service.registry.Services;

import com.oracle.bmc.monitoring.Monitoring;

final class DelegatingOciMetricsConfigSupport {
    private DelegatingOciMetricsConfigSupport() {
    }

    static final class BuilderDecorator implements Prototype.BuilderDecorator<DelegatingOciMetricsConfig.BuilderBase<?, ?>> {
        @Override
        public void decorate(DelegatingOciMetricsConfig.BuilderBase<?, ?> builder) {
            Errors.Collector collector = Errors.collector();
            OciMetricsConfig.Builder delegateBuilder = OciMetricsConfig.builder();

            if (builder.monitoringClient().isEmpty()) {
                builder.monitoringClient(Services.get(Monitoring.class));
            }

            builder.name().ifPresent(delegateBuilder::name);
            builder.compartmentId().ifPresent(delegateBuilder::compartmentId);
            delegateBuilder.initialDelay(builder.initialDelay());
            delegateBuilder.delay(builder.delay());
            delegateBuilder.batchDelay(builder.batchDelay());
            delegateBuilder.descriptionEnabled(builder.descriptionEnabled());
            delegateBuilder.scopes(builder.scopes());
            delegateBuilder.enabled(builder.enabled());
            delegateBuilder.batchSize(builder.batchSize());
            delegateBuilder.nameFormatter(builder.nameFormatter());
            builder.monitoringClient().ifPresent(delegateBuilder::monitoringClient);

            Optional<String> namespace = builder.namespace();
            Optional<String> project = builder.project();
            warnIfDifferent(collector, builder.getClass(), "project", project, "namespace", namespace);
            project.or(() -> namespace).ifPresent(delegateBuilder::namespace);

            Optional<String> resourceGroup = builder.resourceGroup();
            Optional<String> fleet = builder.fleet();
            warnIfDifferent(collector, builder.getClass(), "fleet", fleet, "resource-group", resourceGroup);
            fleet.or(() -> resourceGroup).ifPresent(delegateBuilder::resourceGroup);

            builder.delegate(delegateBuilder.buildPrototype());
            collector.collect().checkValid();
        }

        private static void warnIfDifferent(Errors.Collector collector,
                                            Class<?> source,
                                            String firstKey,
                                            Optional<String> firstValue,
                                            String secondKey,
                                            Optional<String> secondValue) {
            if (firstValue.isPresent() && secondValue.isPresent() && !firstValue.get().equals(secondValue.get())) {
                collector.warn(source,
                               "Conflicting settings for \"" + firstKey + "\" and \"" + secondKey + "\": \""
                                       + firstValue.get() + "\" != \"" + secondValue.get() + "\"");
            }
        }
    }
}
