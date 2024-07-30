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

import java.util.Collection;

import io.helidon.codegen.CodegenContext;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;

/**
 * Maps annotation from a single annotation instance to zero or more annotation instances.
 */
public interface AnnotationMapper {
    /**
     * Check if the annotation is supported.
     *
     * @param annotation annotation to check
     * @return {@code true} if this mapper is interested in the annotation.
     */
    boolean supportsAnnotation(Annotation annotation);

    /**
     * Map an annotation to a set of new annotations.
     * The original annotation is not retained, unless part of the result of this method.
     *
     * @param ctx         code generation context
     * @param original    original annotation that matches {@link #supportsAnnotation(io.helidon.common.types.Annotation)}
     * @param elementKind kind of element the annotation is on
     * @return list of annotations to add instead of the provided annotation (may be empty to remove it),
     *         this result is used to process other mappers (except for this one)
     */
    Collection<Annotation> mapAnnotation(CodegenContext ctx, Annotation original, ElementKind elementKind);
}
