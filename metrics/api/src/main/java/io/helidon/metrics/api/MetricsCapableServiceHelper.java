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
package io.helidon.metrics.api;

/**
 * Helper for metrics-capable REST services.
 *
 * <p>
 *     Metrics-capable services should use this helper to manage the {@link RegistryFactory} which the service should use. This
 *     helper accounts for component-level metrics settings which can influence the {@code RegistryFactory} that the service
 *     should use.
 * </p>
 */
public class MetricsCapableRestServiceHelper {

    /**
     * Creates a new instance based on the component-level metrics settings.
     *
     * @param componentMetricsSettings metrics settings for the component
     * @return new helper
     */
    public static MetricsCapableRestServiceHelper create(ComponentMetricsSettings componentMetricsSettings) {
        return new MetricsCapableRestServiceHelper(componentMetricsSettings);
    }

    private final RegistryFactory registryFactory;

    private MetricsCapableRestServiceHelper(ComponentMetricsSettings componentMetricsSettings) {
        registryFactory = RegistryFactory.getInstance(componentMetricsSettings);
    }

    /**
     * Provides easy access to the properly-prepared {@link RegistryFactory}.
     *
     * @return the {@code RegistryFactory} appropriate for the service to use
     */
    protected RegistryFactory registryFactory() {
        return registryFactory;
    }
}
