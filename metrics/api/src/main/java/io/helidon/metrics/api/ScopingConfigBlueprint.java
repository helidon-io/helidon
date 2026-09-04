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
 * No-op, will be removed.
 *
 * @deprecated No-op, will be removed.
 */
@Deprecated(forRemoval = true, since = "27.0.0")
@Prototype.Annotated("java.lang.Deprecated(forRemoval = true, since = \"27.0.0\")")
@Prototype.Configured(metadata = false)
@Prototype.Blueprint
interface ScopingConfigBlueprint {

    /**
     * No-op, will be removed.
     *
     * @deprecated No-op, will be removed.
     */
    @Deprecated(forRemoval = true, since = "27.0.0")
    String SCOPE_TAG_NAME_DEFAULT = "scope";

    /**
     * No-op, will be removed.
     *
     * @return ignored value
     * @deprecated No-op, will be removed.
     */
    @Deprecated(forRemoval = true, since = "27.0.0")
    @Option.Configured("default")
    @Option.Default("application")
    Optional<String> defaultValue();

    /**
     * No-op, will be removed.
     *
     * @return ignored value
     * @deprecated No-op, will be removed.
     */
    @Deprecated(forRemoval = true, since = "27.0.0")
    @Option.Configured
    @Option.Default(SCOPE_TAG_NAME_DEFAULT)
    Optional<String> tagName();

    /**
     * No-op, will be removed.
     *
     * @return ignored settings
     * @deprecated No-op, will be removed.
     */
    @Deprecated(forRemoval = true, since = "27.0.0")
    @Option.Configured
    @Option.Singular
    Map<String, ScopeConfig> scopes();
}
