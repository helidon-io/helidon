/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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
import io.helidon.common.Api;

/**
 * Legacy GraalVM Native Image compatibility metadata.
 *
 * @deprecated GraalVM Native Image is no longer supported. Remove uses of this metadata.
 */
@Api.Internal
@Prototype.Blueprint
@Prototype.Annotated("java.lang.Deprecated(since = \"28.0.0\", forRemoval = true)")
@Deprecated(since = "28.0.0", forRemoval = true)
interface AotBlueprint {
    /**
     * Whether this module supports AOT, defaults to {@code true}.
     *
     * @return whether AOT is supported
     * @deprecated GraalVM Native Image is no longer supported. Remove uses of this metadata.
     */
    @Option.DefaultBoolean(true)
    @Deprecated(since = "28.0.0", forRemoval = true)
    boolean supported();

    /**
     * AOT description.
     *
     * @return description
     * @deprecated GraalVM Native Image is no longer supported. Remove uses of this metadata.
     */
    @Deprecated(since = "28.0.0", forRemoval = true)
    Optional<String> description();
}
