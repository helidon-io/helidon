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
package io.helidon.metrics.serviceapi;

import javax.annotation.Priority;

import io.helidon.metrics.api.MetricsSettings;
import io.helidon.metrics.serviceapi.spi.MetricsSupportProvider;
import io.helidon.servicecommon.rest.RestServiceSettings;

/**
 * Provider of minimal web support for metrics.
 */
class MinimalMetricsSupportProviderImpl implements MetricsSupportProvider<MinimalMetricsSupport> {

    @Override
    public MetricsSupport.Builder<MinimalMetricsSupport> builder() {
        return MinimalMetricsSupport.builder();
    }

    @Override
    public MinimalMetricsSupport create(MetricsSettings metricsSettings, RestServiceSettings restServiceSettings) {
        return MinimalMetricsSupport.create(restServiceSettings);
    }
}
