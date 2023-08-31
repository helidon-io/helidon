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

import java.util.Map;
import java.util.Optional;

import io.helidon.builder.api.Prototype;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;

@Configured
@Prototype.Blueprint
interface ScopingConfigBlueprint {

    /**
     * Default tag name for recording a meter's scope as a tag.
     */
    String SCOPE_TAG_NAME_DEFAULT = "scope";

    /**
     * Default scope value to associate with meters that are registered without an explicit setting; no setting means meters
     * receive no default scope value.
     *
     * @return default scope value
     */
    @ConfiguredOption(key = "default")
    Optional<String> defaultValue();

    /**
     * Tag name for storing meter scope values in the underlying implementation meter registry.
     *
     * @return tag name for storing scope values
     */
    @ConfiguredOption(value = SCOPE_TAG_NAME_DEFAULT)
    Optional<String> tagName();

    /**
     * Settings for individual scopes.
     *
     * @return scope settings
     */
    @ConfiguredOption
    @Prototype.Singular
    Map<String, ScopeConfig> scopes();
}
