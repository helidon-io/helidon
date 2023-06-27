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

package io.helidon.common.processor;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.util.Elements;

import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;

/**
 * Factory for annotations.
 */
public final class AnnotationFactory {
    private AnnotationFactory() {
    }

    /**
     * Creates a set of annotations using annotation processor.
     *
     * @param annoMirrors the annotation type mirrors
     * @param elements annotation processing element utils
     * @return the annotation value set
     */
    public static Set<Annotation> createAnnotations(List<? extends AnnotationMirror> annoMirrors, Elements elements) {
        return annoMirrors.stream()
                .map(it -> createAnnotation(it, elements))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Creates a set of annotations based using annotation processor.
     *
     * @param type the enclosing/owing type element
     * @param elements annotation processing element utils
     * @return the annotation value set
     */
    public static Set<Annotation> createAnnotations(Element type, Elements elements) {
        return createAnnotations(type.getAnnotationMirrors(), elements);
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
        TypeName val = TypeFactory.createTypeName(am.getAnnotationType())
                .orElseThrow(() -> new IllegalArgumentException("Cannot create annotation for non-existent type: "
                                                                        + am.getAnnotationType()));

        return Annotation.create(val, extractAnnotationValues(am, elements));
    }

    /**
     * Extracts values from the annotation mirror value.
     *
     * @param am       the annotation mirror
     * @param elements the elements
     * @return the extracted values
     */
    private static Map<String, String> extractAnnotationValues(AnnotationMirror am,
                                                               Elements elements) {
        return extractAnnotationValues(elements.getElementValuesWithDefaults(am));
    }

    /**
     * Extracts values from the annotation element values.
     *
     * @param values the element values
     * @return the extracted values
     */
    private static Map<String, String>
    extractAnnotationValues(Map<? extends ExecutableElement, ? extends AnnotationValue> values) {
        Map<String, String> result = new LinkedHashMap<>();
        values.forEach((el, val) -> {
            String name = el.getSimpleName().toString();
            String value = val.accept(new ToStringAnnotationValueVisitor(), null);
            if (value != null) {
                result.put(name, value);
            }
        });
        return result;
    }
}
