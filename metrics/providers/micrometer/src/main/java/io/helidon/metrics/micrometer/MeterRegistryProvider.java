/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.metrics.micrometer;

import java.lang.reflect.Constructor;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;

/**
 * Provides the Micrometer meter registry to use as a delegate for the implementation of the Helidon metrics API.
 */
class MeterRegistryProvider {

    private static final String PROMETHEUS_CONFIG_CLASS_NAME = PrometheusConfig.class.getName();
    private static final String PROMETHEUS_METER_REGISTRY_CLASS_NAME = PrometheusMeterRegistry.class.getName();
    private static final MeterRegistry prometheusMeterRegistry;

    static {
        try {
            Class<?> prometheusConfigClass = Class.forName(PROMETHEUS_CONFIG_CLASS_NAME);
            Class<?> prometheusMeterRegistryClass = Class.forName(PROMETHEUS_METER_REGISTRY_CLASS_NAME);
            try {
                Constructor<?> ctor = prometheusMeterRegistryClass.getConstructor(PrometheusConfig.class);
                prometheusMeterRegistry = (PrometheusMeterRegistry) ctor.newInstance()
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("Found " + PrometheusMeterRegistry.class.getName()
                                           + " but unable to locate the expected constructor", e);
            }
        } catch (ClassNotFoundException e) {
            prometheusMeterRegistry = null;
        }
    }
    static MeterRegistry meterRegistry() {

    }
}
