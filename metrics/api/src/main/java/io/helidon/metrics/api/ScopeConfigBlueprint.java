/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

import java.util.Optional;

import io.helidon.builder.api.Prototype;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;

/**
 * Configuration settings for a scope within the {@value MetricsConfigBlueprint#METRICS_CONFIG_KEY} config section.
 */
@Configured
@Prototype.Blueprint
@Prototype.CustomMethods(ScopeConfigSupport.class)
interface ScopeConfigBlueprint {

    /**
     * Name of the scope to which the configuration applies.
     *
     * @return scope name
     */
    @ConfiguredOption
    String name();

    /**
     * Whether the scope is enabled.
     *
     * @return if the scope is enabled
     */
    @ConfiguredOption(value = "true")
    boolean enabled();

    /**
     * Regular expression for meter names to include.
     *
     * @return include expression
     */
    @ConfiguredOption(key = "filter.include")
    Optional<String> include();

    /**
     * Regular expression for meter names to exclude.
     *
     * @return exclude expression
     */
    @ConfiguredOption(key = "filter.exclude")
    Optional<String> exclude();

    /**
     * Returns whether the specified meter name within the current scope is enabled according to the scope settings.
     *
     * @param name meter name
     * @return if the meter is enabled
     */
    boolean isMeterEnabled(String name);
}
