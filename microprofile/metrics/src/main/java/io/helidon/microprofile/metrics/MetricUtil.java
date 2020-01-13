/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.metrics;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.Tag;

/**
 * Class MetricUtil.
 */
public final class MetricUtil {
    private static final Logger LOGGER = Logger.getLogger(MetricUtil.class.getName());

    private MetricUtil() {
    }

    /**
     * DO NOT USE THIS METHOD please.
     *
     * @param element element
     * @param annotClass annotation class
     * @param clazz class
     * @param <E> element type
     * @param <A> annotation type
     * @return lookup result
     * @deprecated This method is made public to migrate from metrics1 to metrics2 for gRPC, this should be refactored
     */
    @SuppressWarnings("unchecked")
    @Deprecated
    public static <E extends Member & AnnotatedElement, A extends Annotation>
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
    MetricID getMetricID(E element, Class<?> clazz, MatchingType matchingType, String explicitName, String[] tags,
                         boolean absolute) {
        return new MetricID(getMetricName(element, clazz, matchingType, explicitName, absolute), tags(tags));
    }

    /**
     * This method is intended only for other Helidon components.
     *
     * @param element such as method
     * @param clazz class
     * @param matchingType type to match
     * @param explicitName name
     * @param absolute if absolute
     * @param <E> type of element
     *
     * @return name of the metric
     */
    public static <E extends Member & AnnotatedElement>
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
                // Absolute must be false at class level, issue warning here
                if (absolute) {
                    LOGGER.warning(() -> "Attribute 'absolute=true' in metric annotation ignored at class level");
                }
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

    static Tag[] tags(String[] tagStrings) {
        final List<Tag> result = new ArrayList<>();
        for (int i = 0; i < tagStrings.length; i++) {
            final int eq = tagStrings[i].indexOf("=");
            if (eq > 0) {
                final String tagName = tagStrings[i].substring(0, eq);
                final String tagValue = tagStrings[i].substring(eq + 1);
                result.add(new Tag(tagName, tagValue));
            }
        }
        return result.toArray(new Tag[result.size()]);
    }

    /**
     * DO NOT USE THIS CLASS please.
     *
     * Types of possible matching.
     * @deprecated This class is made public to migrate from metrics1 to metrics2 for gRPC, this should be refactored
     */
    @Deprecated
    public enum MatchingType {
        /**
         * Method.
         */
        METHOD,
        /**
         * Class.
         */
        CLASS
    }

    /**
     * DO NOT USE THIS CLASS please.
     * @param <A> type of annotation
     * @deprecated This class is made public to migrate from metrics1 to metrics2 for gRPC, this should be refactored
     */
    @Deprecated
    public static class LookupResult<A extends Annotation> {

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
