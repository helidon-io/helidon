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

package io.helidon.microprofile.faulttolerance;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.faulttolerance.Asynchronous;
import org.eclipse.microprofile.faulttolerance.Bulkhead;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;

/**
 * Class MethodAntn.
 */
public class MethodAntn {
    private static final Logger LOGGER = Logger.getLogger(MethodAntn.class.getName());

    private final Method method;

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
     * @param method The method.
     */
    public MethodAntn(Method method) {
        this.method = method;
        validate();
    }

    public Method getMethod() {
        return method;
    }

    /**
     * Returns underlying annotation and info as to how it was found.
     *
     * @param clazz Annotation class.
     * @param <A> Annotation type.
     * @return The lookup result or {@code null}.
     */
    public final <A extends Annotation> LookupResult<A> lookupAnnotation(Class<A> clazz) {
        A annotation = getMethod().getAnnotation(clazz);
        if (annotation != null) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Found annotation '" + clazz.getName()
                            + "' method '" + getMethod().getName() + "'");
            }
            return new LookupResult<>(MatchingType.METHOD, annotation);
        }
        annotation = getMethod().getDeclaringClass().getAnnotation(clazz);
        if (annotation != null) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Found annotation '" + clazz.getName()
                            + "' class '" + getMethod().getDeclaringClass().getName() + "'");
            }
            return new LookupResult<>(MatchingType.CLASS, annotation);
        }
        return null;
    }

    /**
     * Finds if an annotation is present on a method or its class.
     *
     * @param method Method to check.
     * @param clazz Annotation class.
     * @return Outcome of test.
     */
    static boolean isAnnotationPresent(Method method, Class<? extends Annotation> clazz) {
        return new MethodAntn(method).lookupAnnotation(clazz) != null;
    }

    /**
     * Validate the annotation on this method.
     */
    public void validate() {
    }

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

        // Check if property defined at method level
        if (type == MatchingType.METHOD) {
            String methodLevel = String.format("%s/%s/%s/%s",
                                               method.getDeclaringClass().getName(),
                                               method.getName(),
                                               annotationType,
                                               parameter);
            value = getProperty(methodLevel);
            if (value != null) {
                return value;
            }
        }

        // Check if property defined a class level
        String classLevel = String.format("%s/%s/%s",
                                          method.getDeclaringClass().getName(),
                                          annotationType,
                                          parameter);
        value = getProperty(classLevel);
        if (value != null) {
            return value;
        }

        // Check if propoerty defined at global level
        String globalLevel = String.format("%s/%s",
                                           annotationType,
                                           parameter);
        value = getProperty(globalLevel);
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
     * Returns the value of a property using the MP config API.
     *
     * @param name Property name.
     * @return Property value or {@code null} if it does not exist.
     */
    protected String getProperty(String name) {
        try {
            String value = ConfigProvider.getConfig().getValue(name, String.class);
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Found config property '" + name + "' value '" + value + "'");
            }
            return value;
        } catch (NoSuchElementException e) {
            return null;
        }
    }

    /**
     * Determines if a method or its declaring class has a FT annotation.
     *
     * @param method The method to check.
     * @return Outcome of test.
     */
    static boolean isFaultToleranceMethod(Method method) {
        if (method.isAnnotationPresent(Retry.class)
            || method.isAnnotationPresent(CircuitBreaker.class)
            || method.isAnnotationPresent(Bulkhead.class)
            || method.isAnnotationPresent(Timeout.class)
            || method.isAnnotationPresent(Asynchronous.class)
            || method.isAnnotationPresent(Fallback.class)) {
            return true;
        }
        final Class<?> clazz = method.getDeclaringClass();
        if (clazz.isAnnotationPresent(Retry.class)
            || clazz.isAnnotationPresent(CircuitBreaker.class)
            || clazz.isAnnotationPresent(Bulkhead.class)
            || clazz.isAnnotationPresent(Timeout.class)
            || clazz.isAnnotationPresent(Asynchronous.class)
            || clazz.isAnnotationPresent(Fallback.class)) {
            return true;
        }
        return false;
    }
}
