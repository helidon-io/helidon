/*
 * Copyright (c) 2018, 2024 Oracle and/or its affiliates.
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

package io.helidon.microprofile.faulttolerance;

import java.lang.System.Logger.Level;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import jakarta.enterprise.inject.spi.AnnotatedMethod;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.BeanManager;
import org.eclipse.microprofile.faulttolerance.Retry;

import static io.helidon.microprofile.faulttolerance.FaultToleranceParameter.getParameter;

/**
 * Base class for all method annotations.
 */
abstract class MethodAntn {
    private static final System.Logger LOGGER = System.getLogger(MethodAntn.class.getName());

    private static final AnnotationFinder ANNOTATION_FINDER = AnnotationFinder.create(Retry.class.getPackage());

    private final AnnotatedMethod<?> annotatedMethod;

    enum MatchingType {
        METHOD, CLASS
    }

    static class LookupResult<A extends Annotation> {

        private final MatchingType type;

        private final A annotation;

        /**
         * Constructor.
         *
         * @param type The type of matching.
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

    /**
     * Constructor.
     *
     * @param annotatedMethod Annotated method.
     */
    MethodAntn(AnnotatedMethod<?> annotatedMethod) {
        this.annotatedMethod = annotatedMethod;
    }

    Method method() {
        return annotatedMethod.getJavaMember();
    }

    /**
     * Look up an annotation on the method using instance variables.
     *
     * @param annotClass Annotation class.
     * @param <A> Annotation class type param.
     * @return A lookup result.
     */
    public final <A extends Annotation> LookupResult<A> lookupAnnotation(Class<A> annotClass) {
        return lookupAnnotation(annotatedMethod, annotClass, null);
    }

    /**
     * Finds if an annotation is present on a method or its class.
     *
     * @param annotatedMethod Method to check.
     * @param annotClass Annotation class.
     * @param beanManager CDI's bean manager or {@code null} if not available.
     * @return Outcome of test.
     */
    static boolean isAnnotationPresent(AnnotatedMethod<?> annotatedMethod,
                                       Class<? extends Annotation> annotClass,
                                       BeanManager beanManager) {
        return lookupAnnotation(annotatedMethod, annotClass, beanManager) != null;
    }

    /**
     * Validate the annotation on this method.
     */
    public abstract void validate();

    /**
     * Get annotation type.
     *
     * @return Annotation type.
     */
    @SuppressWarnings("unchecked")
    public Class<? extends Annotation> annotationType() {
        return (Class<? extends Annotation>) getClass().getInterfaces()[0];
    }

    /**
     * Returns override for parameter.
     *
     * @param parameter Parameter name.
     * @param type Matching type for annotation.
     * @return Override value or {@code null} if none defined.
     */
    protected String getParamOverride(String parameter, MatchingType type) {
        String value;

        // Annotation type
        final String annotationType = getClass().getInterfaces()[0].getSimpleName();

        // Check property depending on matching type
        if (type == MatchingType.METHOD) {
            value = getParameter(method().getDeclaringClass().getName(), method().getName(),
                    annotationType, parameter);
            if (value != null) {
                return value;
            }
        } else if (type == MatchingType.CLASS) {
            value = getParameter(method().getDeclaringClass().getName(), annotationType, parameter);
            if (value != null) {
                return value;
            }
        }

        // Check if property defined at global level
        value = getParameter(annotationType, parameter);
        if (value != null) {
            return value;
        }

        // Not overridden
        return null;
    }

    /**
     * Parses an array of {@code Throwable} with or without ".class" suffixes.
     * E.g., "{ Foo.class, Bar.class, Baz.class }".
     *
     * @param array Array represented as a string.
     * @return Array of throwables.
     */
    @SuppressWarnings("unchecked")
    static Class<? extends Throwable>[] parseThrowableArray(String array) {
        List<Class<? extends Throwable>> result = new ArrayList<>();
        StringTokenizer tokenizer = new StringTokenizer(array, "{}, \t\n\r", false);
        while (tokenizer.hasMoreTokens()) {
            try {
                String className = tokenizer.nextToken();
                if (className.endsWith(".class")) {
                    className = className.substring(0, className.length() - ".class".length());
                }
                result.add((Class<? extends Throwable>) Class.forName(className));
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
        return (Class<? extends Throwable>[]) result.toArray(new Class[0]);
    }

    /**
     * Returns underlying annotation and info as to how it was found. If more than one
     * instance of this annotation exist (after computing the transitive closure),
     * one will be returned in an undefined manner.
     *
     * @param method The annotated method.
     * @param annotClass The annotation class.
     * @param <A> Annotation type.
     * @return The lookup result or {@code null}.
     */
    @SuppressWarnings("unchecked")
    static <A extends Annotation> LookupResult<A> lookupAnnotation(AnnotatedMethod<?> method,
                                                                   Class<A> annotClass,
                                                                   BeanManager beanManager) {
        A annotation = (A) getMethodAnnotation(method, annotClass, beanManager);
        if (annotation != null) {
            if (LOGGER.isLoggable(Level.DEBUG)) {
                LOGGER.log(Level.DEBUG, "Found annotation '" + annotClass.getName()
                        + "' method '" + method.getJavaMember().getName() + "'");
            }
            return new LookupResult<>(MatchingType.METHOD, annotation);
        }
        AnnotatedType<?> type = method.getDeclaringType();
        annotation = (A) getClassAnnotation(type, annotClass, beanManager);
        if (annotation != null) {
            if (LOGGER.isLoggable(Level.DEBUG)) {
                LOGGER.log(Level.DEBUG, "Found annotation '" + annotClass.getName()
                        + "' class '" + method.getJavaMember().getDeclaringClass().getName() + "'");
            }
            return new LookupResult<>(MatchingType.CLASS, annotation);
        }
        annotation = (A) getClassAnnotation(method.getJavaMember().getDeclaringClass(), annotClass, beanManager);
        if (annotation != null) {
            if (LOGGER.isLoggable(Level.DEBUG)) {
                LOGGER.log(Level.DEBUG, "Found annotation '" + annotClass.getName()
                        + "' class '" + method.getJavaMember().getDeclaringClass().getName() + "'");
            }
            return new LookupResult<>(MatchingType.CLASS, annotation);
        }
        return null;
    }

    private static Annotation getMethodAnnotation(AnnotatedMethod<?> m,
                                                  Class<? extends Annotation> annotClass,
                                                  BeanManager beanManager) {
        Set<? extends Annotation> set = ANNOTATION_FINDER.findAnnotations(m.getAnnotations(), beanManager);
        return set.stream()
                .filter(a -> a.annotationType().equals(annotClass))
                .findFirst()
                .orElse(null);
    }

    private static Annotation getClassAnnotation(Class<?> c,
                                                 Class<? extends Annotation> annotClass,
                                                 BeanManager beanManager) {
        Set<? extends Annotation> set = ANNOTATION_FINDER.findAnnotations(Set.of(c.getAnnotations()), beanManager);
        return set.stream()
                .filter(a -> a.annotationType().equals(annotClass))
                .findFirst()
                .orElse(null);
    }

    private static Annotation getClassAnnotation(AnnotatedType<?> type,
                                                 Class<? extends Annotation> annotClass,
                                                 BeanManager beanManager) {
        Set<? extends Annotation> set = ANNOTATION_FINDER.findAnnotations(type.getAnnotations(), beanManager);
        return set.stream()
                .filter(a -> a.annotationType().equals(annotClass))
                .findFirst()
                .orElse(null);
    }
}
