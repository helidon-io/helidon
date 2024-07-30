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

package io.helidon.codegen.spi;

import java.util.Optional;

import io.helidon.codegen.CodegenContext;
import io.helidon.common.types.TypeInfo;

/**
 * Maps {@link io.helidon.common.types.TypeInfo} to another {@link io.helidon.common.types.TypeInfo}.
 * This mapper can be used to handle complex changes to a definition of a type, such as combining
 * multiple annotations into a single one.
 */
public interface TypeMapper {
    /**
     * Check if the type is supported.
     *
     * @param type type to check
     * @return {@code true} if this mapper is interested in the element
     */
    boolean supportsType(TypeInfo type);

    /**
     * Map the original type to a different type, or remove it from processing.
     *
     * @param ctx      code generation context
     * @param typeInfo type info to map
     * @return mapped type info, or empty optional to remove the type info
     */
    Optional<TypeInfo> map(CodegenContext ctx, TypeInfo typeInfo);
}
