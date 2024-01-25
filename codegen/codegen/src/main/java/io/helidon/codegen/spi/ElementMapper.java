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
import io.helidon.common.types.TypedElementInfo;

/**
 * Maps (or removes) elements.
 */
public interface ElementMapper {
    /**
     * Check if the element is supported.
     *
     * @param element element to check
     * @return {@code true} if this mapper is interested in the element
     */
    boolean supportsElement(TypedElementInfo element);

    /**
     * Map an element to a different element (changing any of its properties), or remove the element.
     *
     * @param ctx     code generation context
     * @param element element to map
     * @return mapped element, or empty optional to remove the element
     */
    Optional<TypedElementInfo> mapElement(CodegenContext ctx, TypedElementInfo element);
}
