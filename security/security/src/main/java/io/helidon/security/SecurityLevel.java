/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

package io.helidon.security;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.helidon.common.Builder;

/**
 * Security level stores annotations bound to the specific class and method.
 *
 * The first level represents {@link EndpointConfig.AnnotationScope#APPLICATION} level annotations.
 * Other levels are representations of resource, sub-resource and method used on path to get to the target method.
 */
public class SecurityLevel {

    private final String className;
    private final String methodName;
    private final Map<Class<? extends Annotation>, List<Annotation>> classLevelAnnotations;
    private final Map<Class<? extends Annotation>, List<Annotation>> methodLevelAnnotations;

    /**
     * Creates builder for security levels based on class name.
     *
     * @param className class name
     * @return new builder
     */
    public static SecurityLevelBuilder create(String className) {
        Objects.requireNonNull(className);
        return new SecurityLevelBuilder(className);
    }

    /**
     * Creates builder for security levels based on previously created security level.
     *
     * @param copyFrom existing security level
     * @return new builder
     */
    public static SecurityLevelBuilder create(SecurityLevel copyFrom) {
        Objects.requireNonNull(copyFrom);
        return new SecurityLevelBuilder(copyFrom);
    }

    private SecurityLevel(SecurityLevelBuilder builder) {
        this.className = builder.className;
        this.methodName = builder.methodName;

        Map<Class<? extends Annotation>, List<Annotation>> m = new HashMap<>();
        builder.classAnnotations.forEach((key, value) -> m.put(key, Collections.unmodifiableList(value)));
        this.classLevelAnnotations = Collections.unmodifiableMap(m);

        Map<Class<? extends Annotation>, List<Annotation>> meth = new HashMap<>();
        builder.methodAnnotations.forEach((key, value) -> meth.put(key, Collections.unmodifiableList(value)));
        this.methodLevelAnnotations = Collections.unmodifiableMap(meth);
    }

    /**
     * Filters out all annotations of the specific type in the specific scope.
     *
     * @param annotationType type of the annotation
     * @param scope          desired scope
     * @param <T>            annotation type
     * @return list of annotations
     */
    @SuppressWarnings("unchecked")
    public <T extends Annotation> List<T> filterAnnotations(Class<T> annotationType, EndpointConfig.AnnotationScope scope) {
        switch (scope) {
        case CLASS:
            return (List<T>) classLevelAnnotations.getOrDefault(annotationType, List.of());
        case METHOD:
            return (List<T>) methodLevelAnnotations.getOrDefault(annotationType, List.of());
        default:
            return List.of();
        }
    }

    /**
     * Combines all the annotations of the specific type across all the requested scopes.
     *
     * @param annotationType type of the annotation
     * @param scopes         desired scopes
     * @param <T>            annotation type
     * @return list of annotations
     */
    public <T extends Annotation> List<T> combineAnnotations(Class<T> annotationType, EndpointConfig.AnnotationScope... scopes) {
        List<T> result = new LinkedList<>();
        for (EndpointConfig.AnnotationScope scope : scopes) {
            result.addAll(filterAnnotations(annotationType, scope));
        }
        return result;
    }

    /**
     * Returns class level and method level annotations together in one {@link Map}.
     *
     * @return map with class and method level annotations
     */
    public Map<Class<? extends Annotation>, List<Annotation>> allAnnotations() {
        Map<Class<? extends Annotation>, List<Annotation>> result = new HashMap<>(classLevelAnnotations);
        methodLevelAnnotations.forEach((key, value) -> {
            List<Annotation> anno = new LinkedList<>();
            if (result.containsKey(key)) {
                anno.addAll(result.get(key));
            }
            anno.addAll(value);
            result.put(key, anno);
        });
        return result;
    }

    /**
     * Returns the name of the class which this level represents.
     *
     * @return class name
     */
    public String getClassName() {
        return className;
    }

    /**
     * Returns the name of the method which this level represents.
     *
     * @return method name
     */
    public String getMethodName() {
        return methodName;
    }

    /**
     * Returns class level annotations.
     *
     * @return map of annotations
     */
    public Map<Class<? extends Annotation>, List<Annotation>> getClassLevelAnnotations() {
        return classLevelAnnotations;
    }

    /**
     * Returns method level annotations.
     *
     * @return map of annotations
     */
    public Map<Class<? extends Annotation>, List<Annotation>> getMethodLevelAnnotations() {
        return methodLevelAnnotations;
    }

    @Override
    public String toString() {
        return className + (methodName.isEmpty() ? methodName : "." + methodName);
    }

    /**
     *  Builder for {@link SecurityLevel} class.
     */
    public static class SecurityLevelBuilder implements Builder<SecurityLevel> {

        private String className;
        private String methodName;
        private Map<Class<? extends Annotation>, List<Annotation>> classAnnotations;
        private Map<Class<? extends Annotation>, List<Annotation>> methodAnnotations;
        private SecurityLevel copyFrom;

        private SecurityLevelBuilder(String className) {
            this.className = className;
        }

        private SecurityLevelBuilder(SecurityLevel copyFrom) {
            this.copyFrom = copyFrom;
        }

        /**
         * Sets new method name.
         *
         * @param methodName new method name
         * @return updated builder instance
         */
        public SecurityLevelBuilder withMethodName(String methodName) {
            this.methodName = methodName;
            return this;
        }

        /**
         * Sets new class level annotations.
         *
         * @param classAnnotations new class level annotations
         * @return updated builder instance
         */
        public SecurityLevelBuilder withClassAnnotations(Map<Class<? extends Annotation>, List<Annotation>> classAnnotations) {
            this.classAnnotations = classAnnotations;
            return this;
        }

        /**
         * Sets new method level annotations.
         *
         * @param methodAnnotations new method level annotations
         * @return updated builder instance
         */
        public SecurityLevelBuilder withMethodAnnotations(Map<Class<? extends Annotation>, List<Annotation>> methodAnnotations) {
            this.methodAnnotations = methodAnnotations;
            return this;
        }

        @Override
        public SecurityLevel build() {
            this.className = this.className == null ? copyFrom.getClassName() : className;
            this.methodName = this.methodName == null
                    ? (copyFrom == null
                               ? "Unknown"
                               : copyFrom.getMethodName())
                    : methodName;
            this.classAnnotations = this.classAnnotations == null
                    ? (copyFrom == null
                               ? new HashMap<>()
                               : copyFrom.getClassLevelAnnotations())
                    : classAnnotations;
            this.methodAnnotations = this.methodAnnotations == null
                    ? (copyFrom == null
                               ? new HashMap<>()
                               : copyFrom.getMethodLevelAnnotations())
                    : methodAnnotations;
            return new SecurityLevel(this);
        }
    }

}
