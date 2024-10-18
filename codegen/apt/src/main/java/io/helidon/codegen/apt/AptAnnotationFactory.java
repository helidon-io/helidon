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

package io.helidon.codegen.apt;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.util.Elements;

import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;

/**
 * Factory for annotations.
 */
@SuppressWarnings("removal")
final class AptAnnotationFactory {
    private AptAnnotationFactory() {
    }

    /**
     * Creates an instance from an annotation mirror during annotation processing.
     *
     * @param am       the annotation mirror
     * @param elements the elements
     * @return the new instance or empty if the annotation mirror passed is invalid
     */
    public static Annotation createAnnotation(AnnotationMirror am,
                                              Elements elements) {
        TypeName val = AptTypeFactory.createTypeName(am.getAnnotationType())
                .orElseThrow(() -> new IllegalArgumentException("Cannot create annotation for non-existent type: "
                                                                        + am.getAnnotationType()));

        // ignore these annotations, unless one of them was explicitly requested
        var set = new HashSet<TypeName>();
        set.add(TypeNames.INHERITED);
        set.add(TypeNames.TARGET);
        set.add(TypeNames.RETENTION);
        set.add(TypeNames.DOCUMENTED);
        set.remove(val);

        return createAnnotation(elements, am, set)
                .orElseThrow();
    }

    private static Optional<Annotation> createAnnotation(Elements elements, AnnotationMirror am, Set<TypeName> processedTypes) {
        TypeName val = AptTypeFactory.createTypeName(am.getAnnotationType())
                .orElseThrow(() -> new IllegalArgumentException("Cannot create annotation for non-existent type: "
                                                                        + am.getAnnotationType()));

        if (processedTypes.contains(val)) {
            return Optional.empty();
        }

        Annotation.Builder builder = Annotation.builder();

        elements.getAllAnnotationMirrors(am.getAnnotationType().asElement())
                .stream()
                .map(it -> {
                    var newProcessed = new HashSet<>(processedTypes);
                    newProcessed.add(val);
                    return createAnnotation(elements, it, newProcessed);
                })
                .flatMap(Optional::stream)
                .forEach(builder::addMetaAnnotation);

        return Optional.of(builder
                                   .typeName(val)
                                   .values(extractAnnotationValues(am, elements))
                                   .build());
    }

    /**
     * Extracts values from the annotation mirror value.
     *
     * @param am       the annotation mirror
     * @param elements the elements
     * @return the extracted values
     */
    private static Map<String, Object> extractAnnotationValues(AnnotationMirror am,
                                                               Elements elements) {
        return extractAnnotationValues(elements, elements.getElementValuesWithDefaults(am));
    }

    /**
     * Extracts values from the annotation element values.
     *
     * @param values the element values
     * @return the extracted values
     */
    private static Map<String, Object>
    extractAnnotationValues(Elements elements, Map<? extends ExecutableElement, ? extends AnnotationValue> values) {
        Map<String, Object> result = new LinkedHashMap<>();
        values.forEach((el, val) -> {
            String name = el.getSimpleName().toString();
            Object value = val.accept(new ToAnnotationValueVisitor(elements), null);
            if (value != null) {
                result.put(name, value);
            }
        });
        return result;
    }
}
