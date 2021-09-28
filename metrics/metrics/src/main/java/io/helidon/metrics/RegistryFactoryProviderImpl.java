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
package io.helidon.metrics;

import io.helidon.metrics.api.MetricsSettings;
import io.helidon.metrics.api.NoOpRegistryFactory;

/**
 * Full-featured metrics implementation of {@link io.helidon.metrics.api.RegistryFactoryProvider}.
 */
public class RegistryFactoryProviderImpl implements io.helidon.metrics.api.RegistryFactoryProvider {

    @Override
    public io.helidon.metrics.api.RegistryFactory newRegistryFactory(MetricsSettings metricsSettings) {
        return metricsSettings.isEnabled() ? RegistryFactory.create(metricsSettings)
                : NoOpRegistryFactory.create();
    }
}
