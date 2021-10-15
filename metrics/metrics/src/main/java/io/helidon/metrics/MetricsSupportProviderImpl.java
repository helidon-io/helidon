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
import io.helidon.metrics.serviceapi.spi.MetricsSupportProvider;
import io.helidon.servicecommon.rest.RestServiceSettings;

/**
 * Provider which furnishes a builder for {@link MetricsSupport} instances.
 */
public class MetricsSupportProviderImpl implements MetricsSupportProvider<MetricsSupport> {

    @Override
    public MetricsSupport.Builder builder() {
        return MetricsSupport.builder();
    }

    @Override
    public MetricsSupport create(MetricsSettings metricsSettings, RestServiceSettings restServiceSettings) {
        // Don't use create because that delegates to the API MetricsSupport class which would delegate right back here.
        return new MetricsSupport(metricsSettings, restServiceSettings);
    }
}
