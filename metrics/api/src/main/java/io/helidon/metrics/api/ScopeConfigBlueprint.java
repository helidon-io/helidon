/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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
import java.util.regex.Pattern;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Configuration settings for a scope within the {@value MetricsConfigBlueprint#METRICS_CONFIG_KEY} config section.
 */
@Prototype.Configured
@Prototype.Blueprint
@Prototype.CustomMethods(ScopeConfigSupport.class)
interface ScopeConfigBlueprint {

    /**
     * Name of the scope to which the configuration applies.
     *
     * @return scope name
     */
    @Option.Configured
    String name();

    /**
     * Whether the scope is enabled.
     *
     * @return if the scope is enabled
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean enabled();

    /**
     * Regular expression for meter names to include.
     *
     * @return include expression
     */
    @Option.Configured("filter.include")
    Optional<Pattern> include();

    /**
     * Regular expression for meter names to exclude.
     *
     * @return exclude expression
     */
    @Option.Configured("filter.exclude")
    Optional<Pattern> exclude();

    /**
     * Returns whether the specified meter name within the current scope is enabled according to the scope settings.
     *
     * @param name meter name
     * @return if the meter is enabled
     */
    boolean isMeterEnabled(String name);
}
