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

package io.helidon.common.types;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import io.helidon.builder.api.Option;

/**
 * Annotated elements provide annotations and their values.
 */
public interface Annotated {
    /**
     * List of declared and known annotations for this element.
     * Note that "known" implies that the annotation is visible, which depends
     * upon the context in which it was build (such as the {@link java.lang.annotation.Retention of the annotation}).
     *
     * @return the list of annotations declared on this element
     */
    @Option.Singular
    List<Annotation> annotations();

    /**
     * List of all inherited annotations for this element. Inherited annotations are annotations declared
     * on annotations of this element that are also marked as {@link java.lang.annotation.Inherited}.
     * <p>
     * The returned list does not contain {@link #annotations()}. If a meta-annotation is present on multiple
     * annotations, it will be returned once for each such declaration.
     *
     * @return list of all meta annotations of this element
     */
    @Option.Singular
    List<Annotation> inheritedAnnotations();

    /**
     * List of all annotations - both {@link #annotations()} and {@link #inheritedAnnotations()}.
     *
     * @return list of all annotations valid for this element
     */
    default List<Annotation> allAnnotations() {
        List<Annotation> result = new ArrayList<>(annotations());
        result.addAll(inheritedAnnotations());
        return List.copyOf(result);
    }

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
     * Get an annotation on this annotated type.
     *
     * @param annotationType annotation type
     * @return annotation with value
     * @throws java.util.NoSuchElementException if the annotation is not present on this element
     * @see #hasAnnotation(TypeName)
     * @see #findAnnotation(TypeName)
     */
    default Annotation annotation(TypeName annotationType) {
        return findAnnotation(annotationType).orElseThrow(() -> new NoSuchElementException("Annotation " + annotationType + " "
                                                                                                   + "is not present. Guard "
                                                                                                   + "with hasAnnotation(), or "
                                                                                                   + "use findAnnotation() "
                                                                                                   + "instead"));
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
