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

package io.helidon.integrations.openapi.ui;

import java.util.Map;
import java.util.Optional;

import io.helidon.builder.api.Prototype;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;

/**
 * {@link OpenApiUi} prototype.
 */
@Prototype.Blueprint
@Configured
interface OpenApiUiConfigBlueprint extends Prototype.Factory<OpenApiUi> {
    /**
     * Merges implementation-specific UI options.
     *
     * @return options for the UI to merge
     */
    @ConfiguredOption(kind = ConfiguredOption.Kind.MAP)
    Map<String, String> options();

    /**
     * Sets whether the service should be enabled.
     *
     * @return {@code true} if enabled, {@code false} otherwise
     */
    @ConfiguredOption(key = "enabled", value = "true")
    boolean isEnabled();

    /**
     * Full web context (not just the suffix).
     *
     * @return full web context path
     */
    @ConfiguredOption
    Optional<String> webContext();
}
