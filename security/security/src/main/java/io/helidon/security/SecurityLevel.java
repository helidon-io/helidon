/*
 * Copyright (c) 2019, 2026 Oracle and/or its affiliates.
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.helidon.common.Builder;
import io.helidon.common.Errors;
import io.helidon.common.types.TypeName;
import io.helidon.metadata.reflection.AnnotationFactory;

/**
 * Security level stores annotations bound to the specific class and method.
 * <p>
 * The first level represents {@link io.helidon.security.EndpointConfig.AnnotationScope#APPLICATION} level annotations.
 * Other levels are representations of resource, sub-resource and method used on path to get to the target method.
 */
public class SecurityLevel {

    private final TypeName typeName;
    private final String methodName;
    private final Map<TypeName, List<io.helidon.common.types.Annotation>> classLevelAnnotations;
    private final Map<TypeName, List<io.helidon.common.types.Annotation>> methodLevelAnnotations;

    private SecurityLevel(SecurityLevelBuilder builder) {
        this.typeName = builder.typeName;
        this.methodName = builder.methodName;

        Map<TypeName, List<io.helidon.common.types.Annotation>> clazz = new HashMap<>();
        builder.classAnnots.forEach((key, value) -> clazz.put(key, Collections.unmodifiableList(value)));
        this.classLevelAnnotations = Map.copyOf(clazz);

        Map<TypeName, List<io.helidon.common.types.Annotation>> method = new HashMap<>();
        builder.methodAnnots.forEach((key, value) -> method.put(key, Collections.unmodifiableList(value)));
        this.methodLevelAnnotations = Map.copyOf(method);
    }

    /**
     * Create a new fluent API builder for this type.
     *
     * @return a new builder
     */
    public static SecurityLevelBuilder builder() {
        return new SecurityLevelBuilder();
    }

    /**
     * Filters out all annotations of the specific type in the specific scope.
     *
     * @param annotationType type of the annotation
     * @param scope          desired scope
     * @return list of annotations
     */
    public List<io.helidon.common.types.Annotation> filterAnnotations(TypeName annotationType,
                                                                      EndpointConfig.AnnotationScope scope) {
        return switch (scope) {
            case CLASS -> classLevelAnnotations.getOrDefault(annotationType, List.of());
            case METHOD -> methodLevelAnnotations.getOrDefault(annotationType, List.of());
            default -> List.of();
        };
    }

    /**
     * Combines all the annotations of the specific type across all the requested scopes.
     *
     * @param annotationType type of the annotation
     * @param scopes         desired scopes
     * @return list of annotations
     */
    public List<io.helidon.common.types.Annotation> combineAnnotations(TypeName annotationType,
                                                                       EndpointConfig.AnnotationScope... scopes) {
        List<io.helidon.common.types.Annotation> result = new LinkedList<>();
        for (EndpointConfig.AnnotationScope scope : scopes) {
            result.addAll(filterAnnotations(annotationType, scope));
        }
        return result;
    }

    /**
     * Returns all class level and method level annotations.
     *
     * @return list with class and method level annotations
     */
    public List<io.helidon.common.types.Annotation> annotations() {
        List<io.helidon.common.types.Annotation> result = new ArrayList<>();
        classLevelAnnotations.values()
                .forEach(result::addAll);
        methodLevelAnnotations.values()
                .forEach(result::addAll);
        return result;
    }

    /**
     * Type of the class this level represents (such as an endpoint class).
     *
     * @return the type name
     */
    public TypeName typeName() {
        return typeName;
    }

    /**
     * Name of the method this level represents, or {@code Unknown} if this level does not represent a method.
     *
     * @return method name
     */
    public String methodName() {
        return methodName;
    }

    /**
     * Annotations on the class.
     *
     * @return list of class annotations
     */
    public List<io.helidon.common.types.Annotation> classAnnotations() {
        List<io.helidon.common.types.Annotation> result = new ArrayList<>();
        classLevelAnnotations.values().forEach(result::addAll);
        return result;
    }

    /**
     * Annotations on the method.
     *
     * @return list of method annotations
     */
    public List<io.helidon.common.types.Annotation> methodAnnotations() {
        List<io.helidon.common.types.Annotation> result = new ArrayList<>();
        methodLevelAnnotations.values().forEach(result::addAll);
        return result;
    }

    @Override
    public String toString() {
        return typeName + (methodName.isEmpty() ? methodName : "." + methodName);
    }

    /**
     * Builder for {@link io.helidon.security.SecurityLevel} class.
     */
    public static class SecurityLevelBuilder implements Builder<SecurityLevelBuilder, SecurityLevel> {
        private TypeName typeName;
        private String methodName;
        private SecurityLevel copyFrom;
        private Map<TypeName, List<io.helidon.common.types.Annotation>> classAnnots;
        private Map<TypeName, List<io.helidon.common.types.Annotation>> methodAnnots;

        private SecurityLevelBuilder() {
        }

        @Override
        public SecurityLevel build() {
            // make sure we copy stuff that was not configured here
            if (copyFrom != null) {
                if (typeName == null) {
                    type(copyFrom.typeName());
                }
                if (methodName == null) {
                    methodName(copyFrom.methodName());
                }
                if (classAnnots == null) {
                    classAnnotations(copyFrom.classAnnotations());
                }
                if (methodAnnots == null) {
                    methodAnnotations(copyFrom.methodAnnotations());
                }
            }

            this.methodName = methodName == null ? "Unknown" : methodName;
            this.classAnnots = classAnnots == null ? Map.of() : classAnnots;
            this.methodAnnots = methodAnnots == null ? Map.of() : methodAnnots;

            Errors.Collector collector = Errors.collector();
            if (this.typeName == null) {
                collector.fatal(getClass(), "Property \"className\" is required, but not set");
            }
            collector.collect().checkValid();

            return new SecurityLevel(this);
        }

        /**
         * The analyzed type.
         *
         * @param type class of the type
         * @return updated builder instance
         */
        public SecurityLevelBuilder type(Class<?> type) {
            Objects.requireNonNull(type);

            return type(TypeName.create(type));
        }

        /**
         * The analyzed type.
         *
         * @param typeName the type
         * @return updated builder instance
         */
        public SecurityLevelBuilder type(TypeName typeName) {
            Objects.requireNonNull(typeName);

            this.typeName = typeName;
            return this;
        }

        /**
         * Use the provided level as defaults for components not explicitly set on this builder.
         *
         * @param level to read information from
         * @return updated builder instance
         */
        public SecurityLevelBuilder from(SecurityLevel level) {
            Objects.requireNonNull(level);

            this.copyFrom = level;
            return this;
        }

        /**
         * Method name of the method being secured.
         *
         * @param methodName new method name
         * @return updated builder instance
         */
        public SecurityLevelBuilder methodName(String methodName) {
            Objects.requireNonNull(methodName);

            this.methodName = methodName;
            return this;
        }

        /**
         * Add a class annotation to the list of annotations already configured.
         *
         * @param annotation to add
         * @return updated builder instance
         */
        public SecurityLevelBuilder addClassAnnotation(Annotation annotation) {
            Objects.requireNonNull(annotation);

            return addClassAnnotation(AnnotationFactory.create(annotation));
        }

        /**
         * Add a class annotation to the list of annotations already configured.
         *
         * @param annotation to add
         * @return updated builder instance
         */
        public SecurityLevelBuilder addClassAnnotation(io.helidon.common.types.Annotation annotation) {
            Objects.requireNonNull(annotation);

            if (this.classAnnots == null) {
                this.classAnnots = new HashMap<>();
            }
            this.classAnnots.computeIfAbsent(annotation.typeName(),
                                             it -> new ArrayList<>())
                    .add(annotation);
            return this;
        }

        /**
         * Add a method annotation to the list of annotations already configured.
         *
         * @param annotation to add
         * @return updated builder instance
         */
        public SecurityLevelBuilder addMethodAnnotation(Annotation annotation) {
            Objects.requireNonNull(annotation);

            return addMethodAnnotation(AnnotationFactory.create(annotation));
        }

        /**
         * Add a method annotation to the list of annotations already configured.
         *
         * @param annotation to add
         * @return updated builder instance
         */
        public SecurityLevelBuilder addMethodAnnotation(io.helidon.common.types.Annotation annotation) {
            Objects.requireNonNull(annotation);

            if (this.methodAnnots == null) {
                this.methodAnnots = new HashMap<>();
            }
            this.methodAnnots.computeIfAbsent(annotation.typeName(),
                                             it -> new ArrayList<>())
                    .add(annotation);
            return this;
        }

        /**
         * Class annotation of the analyzed type.
         *
         * @param annotations to set
         * @return updated builder instance
         */
        public SecurityLevelBuilder classAnnotations(List<io.helidon.common.types.Annotation> annotations) {
            Objects.requireNonNull(annotations);

            this.classAnnots = new HashMap<>();

            for (io.helidon.common.types.Annotation annotation : annotations) {
                classAnnots.computeIfAbsent(annotation.typeName(), it -> new ArrayList<>())
                        .add(annotation);
            }
            return this;
        }

        /**
         * Set list of method annotations.
         *
         * @param annotations annotations of the analyzed method
         * @return updated builder instance
         */
        public SecurityLevelBuilder methodAnnotations(List<io.helidon.common.types.Annotation> annotations) {
            Objects.requireNonNull(annotations);

            this.methodAnnots = new HashMap<>();

            for (io.helidon.common.types.Annotation annotation : annotations) {
                methodAnnots.computeIfAbsent(annotation.typeName(), it -> new ArrayList<>())
                        .add(annotation);
            }
            return this;
        }
    }

}
