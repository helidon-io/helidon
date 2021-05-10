/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
package io.helidon.webserver;

import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.serviceloader.HelidonServiceLoader;

/**
 * Loader for the highest-priority {@link KeyPerformanceIndicatorMetricsServiceFactory}, if any exist, and a low-priority default
 * no-op one otherwise.
 */
class KeyPerformanceIndicatorMetricsServiceFactoryLoader {

    private static final Logger LOGGER = Logger.getLogger(KeyPerformanceIndicatorMetricsServiceFactoryLoader.class.getName());

    private KeyPerformanceIndicatorMetricsServiceFactoryLoader() {
    }

    static KeyPerformanceIndicatorMetricsServiceFactory load() {
        HelidonServiceLoader<KeyPerformanceIndicatorMetricsServiceFactory> loader =
                HelidonServiceLoader.builder(ServiceLoader.load(KeyPerformanceIndicatorMetricsServiceFactory.class))
                        .addService(new FallBackFactory(), Integer.MAX_VALUE)
                        .build();
        return loader.asList()
                .get(0);
    }

    /**
     * A very low priority last-chance implementation of the factory, in case neither the factory from {@code MetricsSupport}
     * nor {@code JerseySupport} is loadable. This can happen if a test disables service discovery, as an example.
     */
    private static class FallBackFactory implements KeyPerformanceIndicatorMetricsServiceFactory {

        @Override
        public KeyPerformanceIndicatorMetricsService create(String metricsPrefix,
                KeyPerformanceIndicatorMetricsConfig kpiMetricsConfig) {
            LOGGER.log(Level.FINE, "Last-chance KeyPerformanceIndicatorMetricsServiceFactory in use; "
                    + "service loading discovered no other implementation, possibly because metrics is excluded or "
                    + "discovery is disabled");
            return new KeyPerformanceIndicatorMetricsService() {
            };
        }
    }
}
