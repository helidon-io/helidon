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

package io.helidon.common.features.codegen;

import io.helidon.common.types.TypeName;

final class FeatureCodegenTypes {
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.common.features.api.Features.Name}.
     */
    static final TypeName NAME = TypeName.create("io.helidon.common.features.api.Features.Name");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.common.features.api.Features.Description}.
     */
    static final TypeName DESCRIPTION = TypeName.create("io.helidon.common.features.api.Features.Description");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.common.features.api.Features.Since}.
     */
    static final TypeName SINCE = TypeName.create("io.helidon.common.features.api.Features.Since");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.common.features.api.Features.Path}.
     */
    static final TypeName PATH = TypeName.create("io.helidon.common.features.api.Features.Path");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.common.features.api.Features.Flavor}.
     */
    static final TypeName FLAVOR = TypeName.create("io.helidon.common.features.api.Features.Flavor");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.common.features.api.Features.InvalidFlavor}.
     */
    static final TypeName INVALID_FLAVOR = TypeName.create("io.helidon.common.features.api.Features.InvalidFlavor");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.common.features.api.Features.Aot}.
     */
    static final TypeName AOT = TypeName.create("io.helidon.common.features.api.Features.Aot");

    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.common.features.api.Features.Preview}.
     */
    static final TypeName PREVIEW = TypeName.create("io.helidon.common.features.api.Features.Preview");

    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.common.features.api.Features.Incubating}.
     */
    static final TypeName INCUBATING = TypeName.create("io.helidon.common.features.api.Features.Incubating");

    private FeatureCodegenTypes() {
    }
}
