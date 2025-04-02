/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.common.features.metadata;

import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * AOT (Ahead of time compilation) support. Defines support for GraalVM native image.
 */
@Prototype.Blueprint
interface AotBlueprint {
    /**
     * Whether this module supports AOT, defaults to {@code true}.
     *
     * @return is deprecated
     */
    @Option.DefaultBoolean(true)
    boolean supported();

    /**
     * AOT description.
     *
     * @return description
     */
    Optional<String> description();
}
