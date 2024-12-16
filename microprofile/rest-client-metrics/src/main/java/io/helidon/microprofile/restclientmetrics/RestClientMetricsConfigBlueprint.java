/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.microprofile.restclientmetrics;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Configuration settings for MP REST client metrics.
 */
@Prototype.Blueprint
@Prototype.Configured(RestClientMetricsConfigBlueprint.CONFIG_KEY)
interface RestClientMetricsConfigBlueprint {

    /**
     * Root=level config key for REST client metrics settings.
     */
    String CONFIG_KEY = "rest-client.metrics";

    /**
     * Whether REST client metrics functionality is enabled.
     *
     * @return if REST client metrics are configured to be enabled
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean enabled();
}
