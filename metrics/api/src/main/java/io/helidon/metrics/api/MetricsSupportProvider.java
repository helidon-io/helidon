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
 * Provider behavior for {@link MetricsSupport.Builder} instances (and, indirectly, for {@link MetricsSupport} instances).
 *
 * @param <T> implementation type of {@link MetricsSupport}
 */
public interface MetricsSupportProvider<T extends MetricsSupport> {

    /**
     *
     * @return a new {@link MetricsSupport.Builder} for a specific implementation type of {@code MetricsSupport}
     */
    T.Builder<T> builder();

    /**
     * Create a new instance of the specific type of {@link MetricsSupport}.
     *
     * @param metricsSettings metrics settings to use in creating the {@code MetricsSupport} instance
     * @return the new {@code MetricsSupport} instance
     */
    T create(MetricsSettings metricsSettings);

}
