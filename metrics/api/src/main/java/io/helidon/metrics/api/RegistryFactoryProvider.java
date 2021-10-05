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
 * Provides {@link RegistryFactory} instances.
 * <p>A component that contains an implementation of {@code RegistryFactoryProvider} should identify it using the service
 * loader mechanism.</p>
 */
public interface RegistryFactoryProvider {

    /**
     * Creates a new {@link RegistryFactory} according to the specified metrics settings.
     * <p>
     *     Implementations of this method should assume that {@code MetricsSettings.isEnabled()} is {@code true} and do not
     *     need to check that themselves.
     * </p>
     * @param metricsSettings the {@link MetricsSettings} to use in creating the new registry factory
     * @return a new {@link RegistryFactory} based on the provided metrics settings
     */
    RegistryFactory create(MetricsSettings metricsSettings);
}
