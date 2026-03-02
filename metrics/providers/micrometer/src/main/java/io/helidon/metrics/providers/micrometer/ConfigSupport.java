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

package io.helidon.metrics.providers.micrometer;

import java.util.Objects;
import java.util.Properties;

import io.helidon.builder.api.Prototype;

import io.micrometer.prometheusmetrics.PrometheusConfig;

class ConfigSupport {

    private ConfigSupport() {
    }

    static class MicrometerMetricsConfigSupport {

        private MicrometerMetricsConfigSupport() {
        }

        static class BuilderDecorator implements Prototype.BuilderDecorator<MicrometerMetricsConfig.BuilderBase<?, ?>> {

            @Override
            public void decorate(MicrometerMetricsConfig.BuilderBase<?, ?> target) {
                /*
                Later code that sets up the Micrometer global meter registry needs to be able to create Prometheus registries
                (in particular) at that time (if we need to set up exemplar handling). So create suppliers for any
                explicitly-added meter registries.
                 */
                target.meterRegistries().stream()
                        .map(ConfiguredMeterRegistry::create)
                        .forEach(r -> target.registries().add(r));

                /*
                If no meter registries were configured or added explicitly make sure we have a Prometheus one for backward
                compatibility.
                 */
                if (target.registries().isEmpty()) {
                    var defaultPrometheusRegistryConfig = PrometheusMeterRegistryConfig.create();
                    target.registries()
                            .add(ConfiguredPrometheusMeterRegistryProvider.ConfiguredPrometheusMeterRegistry.create(
                                    defaultPrometheusRegistryConfig
                            ));
                }
                target.meterRegistries().addAll(target.registries().stream()
                                                        .filter(ConfiguredMeterRegistry::isEnabled)
                                                        .map(cmr -> cmr.meterRegistrySupplier().get())
                                                        .toList());
            }
        }
    }

    static class OtlpMeterRegistrySupport {

        private OtlpMeterRegistrySupport() {
        }

        static class CustomMethods {

            private CustomMethods() {
            }

            @Prototype.PrototypeMethod
            public static String get(OtlpMeterRegistryConfig config, String key) {
                return config.properties().get(key);
            }
        }
    }

    static class PrometheusMeterRegistrySupport {

        private static final PrometheusConfig DEFAULT_PROMETHEUS_CONFIG = PrometheusConfig.DEFAULT;

        private PrometheusMeterRegistrySupport() {
        }

        static Properties defaultProperties() {
            // Slightly expensive but it allows the builder to use the Micrometer-provided defaults in case they change.
            return Objects.requireNonNullElse(DEFAULT_PROMETHEUS_CONFIG.prometheusProperties(), new Properties());
        }

        static class CustomMethods {
            private CustomMethods() {
            }

            @Prototype.PrototypeMethod
            public static String get(PrometheusMeterRegistryConfig config, String key) {
                return config.properties().get(key);
            }

        }
    }
}
