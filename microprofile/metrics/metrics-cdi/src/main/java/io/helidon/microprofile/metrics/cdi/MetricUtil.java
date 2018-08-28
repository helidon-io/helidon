/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.metrics.cdi;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

/**
 * Class MetricUtil.
 */
final class MetricUtil {

    private MetricUtil() {
    }

    @SuppressWarnings("unchecked")
    static <E extends Member & AnnotatedElement, A extends Annotation>
    LookupResult<A> lookupAnnotation(E element, Class<? extends Annotation> annotClass, Class<?> clazz) {
        // First check annotation on element
        A annotation = (A) element.getAnnotation(annotClass);
        if (annotation != null) {
            return new LookupResult<>(MatchingType.METHOD, annotation);
        }
        // Finally check annotations on class
        annotation = (A) element.getDeclaringClass().getAnnotation(annotClass);
        if (annotation == null) {
            annotation = (A) clazz.getAnnotation(annotClass);
        }
        return annotation == null ? null : new LookupResult<>(MatchingType.CLASS, annotation);
    }

    static <E extends Member & AnnotatedElement>
    String getMetricName(E element, Class<?> clazz, MatchingType matchingType, String explicitName, boolean absolute) {
        String result;
        if (matchingType == MatchingType.METHOD) {
            result = explicitName == null || explicitName.isEmpty()
                    ? getElementName(element, clazz) : explicitName;
            if (!absolute) {
                Class<?> declaringClass = clazz;
                if (element instanceof Method) {
                    // We need to find the declaring class if a method
                    List<Method> methods = Arrays.asList(declaringClass.getDeclaredMethods());
                    while (!methods.contains(element)) {
                        declaringClass = declaringClass.getSuperclass();
                        methods = Arrays.asList(declaringClass.getDeclaredMethods());
                    }
                }
                result = declaringClass.getName() + "." + result;
            }
        } else if (matchingType == MatchingType.CLASS) {
            if (explicitName == null || explicitName.isEmpty()) {
                result = getElementName(element, clazz);
                if (!absolute) {
                    result = clazz.getName() + "." + result;
                }
            } else {
                // absolute?
                result = clazz.getPackage().getName() + "." + explicitName
                        + "." + getElementName(element, clazz);
            }
        } else {
            throw new InternalError("Unknown matching type");
        }
        return result;
    }

    static <E extends Member & AnnotatedElement>
    String getElementName(E element, Class<?> clazz) {
        return element instanceof Constructor ? clazz.getSimpleName() : element.getName();
    }

    enum MatchingType {
        METHOD, CLASS
    }

    static class LookupResult<A extends Annotation> {

        private final MatchingType type;

        private final A annotation;

        /**
         * Constructor.
         *
         * @param type       The type of matching.
         * @param annotation The annotation.
         */
        LookupResult(MatchingType type, A annotation) {
            this.type = type;
            this.annotation = annotation;
        }

        public MatchingType getType() {
            return type;
        }

        public A getAnnotation() {
            return annotation;
        }
    }
}
