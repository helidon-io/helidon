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
     * @param metricsSettings the {@link MetricsSettings} to use in creating the new registry factory
     * @return a new {@link RegistryFactory}.
     */
    RegistryFactory newRegistryFactory(MetricsSettings metricsSettings);
}
