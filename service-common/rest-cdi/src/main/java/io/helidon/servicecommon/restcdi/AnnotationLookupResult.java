/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.servicecommon.restcdi;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Member;
import java.util.Collection;
import java.util.stream.Collectors;

import javax.enterprise.inject.spi.Annotated;
import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.AnnotatedType;

/**
 * Encapsulation of the result of locating an annotation, indicating the type of site ({@link AnnotationSiteType}) as well as the
 * annotation itself.
 *
 * @param <A> type of annotation
 */
public class AnnotationLookupResult<A extends Annotation> {

    private final AnnotationSiteType type;

    private final A annotation;

    /**
     * Creates a new instance based on the provided annotation class and element bearing that annotation.
     *
     * @param element    element
     * @param annotClass annotation class
     * @param clazz      class
     * @param <E>        element type
     * @param <A>        annotation type
     * @return lookup result
     */
    public static <E extends Member & AnnotatedElement, A extends Annotation>
    AnnotationLookupResult<A> lookupAnnotation(E element, Class<? extends A> annotClass, Class<?> clazz) {
        // First check annotation on element
        A annotation = element.getAnnotation(annotClass);
        if (annotation != null) {
            return new AnnotationLookupResult<>(AnnotationSiteType.METHOD, annotation);
        }
        // Finally check annotations on class
        annotation = element.getDeclaringClass()
                .getAnnotation(annotClass);
        if (annotation == null) {
            annotation = clazz.getAnnotation(annotClass);
        }
        return annotation == null ? null : new AnnotationLookupResult<>(AnnotationSiteType.CLASS, annotation);
    }

    /**
     * Creates a collection of lookup results given an annotation type and an {@code Annotated} site.
     *
     * @param annotated  the annotated site
     * @param annotClass the annotation type class
     * @param <A>        the annotation type
     * @return lookup result
     */
    public static <A extends Annotation> Collection<AnnotationLookupResult<A>> lookupAnnotations(Annotated annotated,
            Class<A> annotClass) {
        // We have to filter by annotation class ourselves, because annotatedMethod.getAnnotations(Class) delegates
        // to the Java method. That would bypass any annotations that had been added dynamically to the configurator.
        return annotated.getAnnotations()
                .stream()
                .filter(annotClass::isInstance)
                .map(annotation -> new AnnotationLookupResult<>(AnnotationSiteType.matchingType(annotated),
                        annotClass.cast(annotation)))
                .collect(Collectors.toList());
    }

    /**
     * Creates a collection of lookup results for an annotation on a given method or, failing that, at the corresponding class
     * level.
     *
     * @param annotatedType   the type on which the annotation might appear
     * @param annotatedMethod the method to check for the annotation
     * @param annotClass      the annotation to check for
     * @param <A>             the type of the annotation
     * @return {@code List} of {@code AnnotationLookupResult}
     */
    public static <A extends Annotation> Collection<AnnotationLookupResult<A>> lookupAnnotations(
            AnnotatedType<?> annotatedType,
            AnnotatedMethod<?> annotatedMethod,
            Class<A> annotClass) {
        Collection<AnnotationLookupResult<A>> result = lookupAnnotations(annotatedMethod, annotClass);
        if (result.isEmpty()) {
            result = lookupAnnotations(annotatedType, annotClass);
        }
        return result;
    }

    private AnnotationLookupResult(AnnotationSiteType type, A annotation) {
        this.type = type;
        this.annotation = annotation;
    }

    /**
     * Returns the site type where the annotation is located.
     *
     * @return the matching type
     */
    public AnnotationSiteType siteType() {
        return type;
    }

    /**
     * Returns the annotation.
     *
     * @return the annotation
     */
    public A annotation() {
        return annotation;
    }
}
