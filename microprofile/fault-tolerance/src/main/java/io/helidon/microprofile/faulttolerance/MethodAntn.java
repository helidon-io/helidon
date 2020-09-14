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

package io.helidon.microprofile.faulttolerance;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import static io.helidon.microprofile.faulttolerance.FaultToleranceParameter.getParameter;

/**
 * Class MethodAntn.
 */
abstract class MethodAntn {
    private static final Logger LOGGER = Logger.getLogger(MethodAntn.class.getName());

    private final Method method;

    private final Class<?> beanClass;

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
     * @param beanClass Bean class.
     * @param method The method.
     */
    MethodAntn(Class<?> beanClass, Method method) {
        this.beanClass = beanClass;
        this.method = method;
    }

    Method method() {
        return method;
    }

    Class<?> beanClass() {
        return beanClass;
    }

    /**
     * Look up an annotation on the method.
     *
     * @param annotClass Annotation class.
     * @param <A> Annotation class type param.
     * @return A lookup result.
     */
    public final <A extends Annotation> LookupResult<A> lookupAnnotation(Class<A> annotClass) {
        return lookupAnnotation(beanClass, method, annotClass);
    }

    /**
     * Returns underlying annotation and info as to how it was found.
     *
     * @param beanClass The bean class.
     * @param method The method.
     * @param annotClass The annotation class.
     * @param <A> Annotation type.
     * @return The lookup result or {@code null}.
     */
    static <A extends Annotation> LookupResult<A> lookupAnnotation(Class<?> beanClass, Method method,
                                                                   Class<A> annotClass) {
        A annotation = method.getAnnotation(annotClass);
        if (annotation != null) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Found annotation '" + annotClass.getName()
                        + "' method '" + method.getName() + "'");
            }
            return new LookupResult<>(MatchingType.METHOD, annotation);
        }
        annotation = beanClass.getAnnotation(annotClass);
        if (annotation != null) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Found annotation '" + annotClass.getName()
                        + "' class '" + method.getDeclaringClass().getName() + "'");
            }
            return new LookupResult<>(MatchingType.CLASS, annotation);
        }
        annotation = method.getDeclaringClass().getAnnotation(annotClass);
        if (annotation != null) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Found annotation '" + method.getDeclaringClass().getName()
                        + "' class '" + method.getDeclaringClass().getName() + "'");
            }
            return new LookupResult<>(MatchingType.CLASS, annotation);
        }
        return null;
    }

    /**
     * Finds if an annotation is present on a method or its class.
     *
     * @param beanClass The bean class.
     * @param method Method to check.
     * @param annotClass Annotation class.
     * @return Outcome of test.
     */
    static boolean isAnnotationPresent(Class<?> beanClass, Method method, Class<? extends Annotation> annotClass) {
        return lookupAnnotation(beanClass, method, annotClass) != null;
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
            value = getParameter(method.getDeclaringClass().getName(), method.getName(),
                    annotationType, parameter);
            if (value != null) {
                return value;
            }
        } else if (type == MatchingType.CLASS) {
            value = getParameter(method.getDeclaringClass().getName(), annotationType, parameter);
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
}
