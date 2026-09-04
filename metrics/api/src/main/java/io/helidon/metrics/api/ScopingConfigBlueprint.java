/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

import java.util.Map;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Scoping configuration retained for compatibility; core metrics ignores its settings.
 *
 * @deprecated Core metrics ignores these settings, and this type will be removed.
 */
@Deprecated(forRemoval = true, since = "27.0.0")
@Prototype.Annotated("java.lang.Deprecated(forRemoval = true, since = \"27.0.0\")")
@Prototype.Configured(metadata = false)
@Prototype.Blueprint
interface ScopingConfigBlueprint {

    /**
     * Legacy scope tag name retained for compatibility.
     *
     * @deprecated Core metrics ignores scope tag names, and this constant will be removed.
     */
    @Deprecated(forRemoval = true, since = "27.0.0")
    String SCOPE_TAG_NAME_DEFAULT = "scope";

    /**
     * Returns the configured default scope value.
     *
     * @return configured default scope value
     * @deprecated Core metrics ignores this value, and this method will be removed.
     */
    @Deprecated(forRemoval = true, since = "27.0.0")
    @Option.Configured("default")
    @Option.Default("application")
    Optional<String> defaultValue();

    /**
     * Returns the configured scope tag name.
     *
     * @return configured scope tag name
     * @deprecated Core metrics ignores this value, and this method will be removed.
     */
    @Deprecated(forRemoval = true, since = "27.0.0")
    @Option.Configured
    @Option.Default(SCOPE_TAG_NAME_DEFAULT)
    Optional<String> tagName();

    /**
     * Returns the configured settings for individual scopes.
     *
     * @return configured scope settings
     * @deprecated Core metrics ignores these settings, and this method will be removed.
     */
    @Deprecated(forRemoval = true, since = "27.0.0")
    @Option.Configured
    @Option.Singular
    Map<String, ScopeConfig> scopes();
}
