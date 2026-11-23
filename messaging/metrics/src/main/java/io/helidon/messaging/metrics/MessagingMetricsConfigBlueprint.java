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

package io.helidon.messaging.metrics;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.Api;
import io.helidon.messaging.spi.MessagingServiceProvider;

/**
 * Configuration of the messaging metrics service.
 */
@Prototype.Blueprint
@Prototype.Sealed
@Prototype.Configured(value = "metrics", root = false)
@Prototype.Provides(MessagingServiceProvider.class)
@Api.Preview
interface MessagingMetricsConfigBlueprint extends Prototype.Factory<MessagingMetrics> {
    /**
     * Whether to record channel processing metrics when this service is selected.
     *
     * @return whether the service is enabled
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean enabled();
}
