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

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Scope configuration retained for compatibility; core metrics ignores its settings.
 *
 * @deprecated Core metrics ignores these settings, and this type will be removed.
 */
@Deprecated(forRemoval = true, since = "27.0.0")
@Prototype.Annotated("java.lang.Deprecated(forRemoval = true, since = \"27.0.0\")")
@Prototype.Configured(metadata = false)
@Prototype.Blueprint
@Prototype.CustomMethods(ScopeConfigSupport.class)
interface ScopeConfigBlueprint {

    /**
     * Returns the configured scope name.
     *
     * @return configured scope name
     * @deprecated Core metrics ignores this value, and this method will be removed.
     */
    @Deprecated(forRemoval = true, since = "27.0.0")
    @Option.Configured
    String name();

    /**
     * Returns whether the scope is configured as enabled.
     *
     * @return configured value
     * @deprecated Core metrics ignores this value, and this method will be removed.
     */
    @Deprecated(forRemoval = true, since = "27.0.0")
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean enabled();

    /**
     * Returns the configured meter-name inclusion pattern.
     *
     * @return configured inclusion pattern
     * @deprecated Core metrics ignores this value, and this method will be removed.
     */
    @Deprecated(forRemoval = true, since = "27.0.0")
    @Option.Configured("filter.include")
    Optional<Pattern> include();

    /**
     * Returns the configured meter-name exclusion pattern.
     *
     * @return configured exclusion pattern
     * @deprecated Core metrics ignores this value, and this method will be removed.
     */
    @Deprecated(forRemoval = true, since = "27.0.0")
    @Option.Configured("filter.exclude")
    Optional<Pattern> exclude();

    /**
     * Always returns {@code true} because core metrics does not apply scope filtering.
     *
     * @param name ignored; must not be {@code null}
     * @return true
     * @deprecated Core metrics does not apply scope filtering, and this method will be removed.
     */
    @Deprecated(forRemoval = true, since = "27.0.0")
    default boolean isMeterEnabled(String name) {
        Objects.requireNonNull(name);
        return true;
    }
}
