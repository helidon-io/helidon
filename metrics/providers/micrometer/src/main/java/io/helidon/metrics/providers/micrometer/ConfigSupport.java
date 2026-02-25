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

import io.helidon.builder.api.Prototype;

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
                Add enabled configured meter registries to any meter registries the app might have explicitly added.
                 */
                target.meterRegistries().addAll(target.registries().stream()
                                                        .filter(ConfiguredMeterRegistry::isEnabled)
                                                        .map(cmr -> cmr.meterRegistry().get())
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
}
