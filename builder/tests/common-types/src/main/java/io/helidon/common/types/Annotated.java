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

package io.helidon.common.types;

import java.util.List;
import java.util.Optional;

import io.helidon.builder.api.Option;

/**
 * Annotated elements provide annotations and their values.
 */
public interface Annotated {
    /**
     * The list of known annotations for this element. Note that "known" implies that the annotation is visible, which depends
     * upon the context in which it was build.
     *
     * @return the list of annotations on this element
     */
    @Option.Singular
    List<Annotation> annotations();

    /**
     * Find an annotation on this annotated type.
     *
     * @param annotationType annotation type
     * @return annotation with value (if found)
     */
    default Optional<Annotation> findAnnotation(TypeName annotationType) {
        for (Annotation annotation : annotations()) {
            if (annotation.typeName().equals(annotationType)) {
                return Optional.of(annotation);
            }
        }
        return Optional.empty();
    }

    /**
     * Check if the annotation exists on this annotated.
     *
     * @param annotationType type of annotation to find
     * @return {@code true} if the annotation exists on this annotated component
     */
    default boolean hasAnnotation(TypeName annotationType) {
        return annotations()
                .stream()
                .anyMatch(it -> annotationType.equals(it.typeName()));
    }
}
