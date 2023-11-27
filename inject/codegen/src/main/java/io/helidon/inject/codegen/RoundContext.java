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

package io.helidon.inject.codegen;

import java.util.Collection;

import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

/**
 * Context of a single round of code generation.
 * For example the first round may generate types, that require additional code generation.
 */
public interface RoundContext {
    /**
     * Available annotations for this provider.
     *
     * @return annotation types
     */
    Collection<TypeName> availableAnnotations();

    /**
     * All types for processing in this round.
     *
     * @return all type infos
     */

    Collection<TypeInfo> types();

    /**
     * All types annotated with a specific annotation.
     *
     * @param annotationType annotation type
     * @return type infos annotated with the provided annotation
     */

    Collection<TypeInfo> annotatedTypes(TypeName annotationType);

    /**
     * All elements annotated with a specific annotation.
     *
     * @param annotationType annotation type
     * @return elements annotated with the provided annotation
     */
    Collection<TypedElementInfo> annotatedElements(TypeName annotationType);
}
