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

package io.helidon.metrics.providers.helidon;

import io.helidon.common.Api;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsPublisher;

/**
 * Publisher of metrics recorded by the Helidon metrics provider.
 * Each enabled registry starts a separate publishing session and closes that session when the registry closes.
 * Implementations must support independent sessions for multiple registries.
 */
@Api.Preview
public interface HelidonMetricsPublisher extends MetricsPublisher {

    /**
     * Starts publishing metrics from the supplied registry.
     * The registry owns the returned session. If startup fails, the implementation must release any resources it acquired
     * before throwing an exception.
     *
     * @param registry registry whose meters to publish; must not be {@code null}
     * @param metricsConfig configuration of this registry, including its system tags; must not be {@code null}
     * @return non-null publishing session owned by the registry
     */
    Session start(MeterRegistry registry, MetricsConfig metricsConfig);

    /**
     * Publishing session for one registry.
     */
    @FunctionalInterface
    interface Session extends AutoCloseable {

        /**
         * Stops publishing and releases this session's resources.
         * The registry calls this method once, before removing its meters, so a publisher can perform a final export.
         * Implementations must cancel further scheduling and bound any wait for in-flight publishing, requesting
         * cancellation if the bound expires. An application-supplied meter callback can continue running if it does not
         * respond to cancellation.
         */
        @Override
        void close();
    }
}
