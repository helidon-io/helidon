/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

package io.helidon.inject.api;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import io.helidon.builder.api.Prototype;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;

import jakarta.inject.Named;

/**
 * Represents a qualifier annotation (a specific case of annotations, annotated with {@link jakarta.inject.Qualifier}.
 *
 * @see jakarta.inject.Qualifier
 * @see CommonQualifiers
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
@Prototype.Blueprint
@Prototype.CustomMethods(QualifierBlueprint.QualifierMethods.class)
interface QualifierBlueprint extends Annotation {
    /**
     * The type name for {@link ClassNamed}.
     */
    TypeName CLASS_NAMED = TypeName.create(ClassNamed.class);

    /**
     * The qualifier annotation type name.
     *
     * @return the qualifier/annotation type name
     */
    default String qualifierTypeName() {
        return typeName().name();
    }

    final class QualifierMethods {
        private QualifierMethods() {
        }

        /**
         * Creates a qualifier from an annotation.
         *
         * @param qualifierType the qualifier type
         * @return qualifier
         */
        @Prototype.FactoryMethod
        static Qualifier create(Class<? extends java.lang.annotation.Annotation> qualifierType) {
            Objects.requireNonNull(qualifierType);
            TypeName qualifierTypeName = maybeNamed(qualifierType.getName());
            return Qualifier.builder().typeName(qualifierTypeName).build();
        }

        /**
         * Creates a qualifier with a value from an annotation.
         *
         * @param qualifierType the qualifier type
         * @param value the value property
         * @return qualifier
         */
        @Prototype.FactoryMethod
        static Qualifier create(Class<? extends java.lang.annotation.Annotation> qualifierType, String value) {
            Objects.requireNonNull(qualifierType);
            TypeName qualifierTypeName = maybeNamed(qualifierType.getName());
            return Qualifier.builder()
                    .typeName(qualifierTypeName)
                    .putValue("value", value)
                    .build();
        }

        /**
         * Creates a qualifier from an annotation.
         *
         * @param annotation the qualifier annotation
         * @return qualifier
         */
        @Prototype.FactoryMethod
        static Qualifier create(Annotation annotation) {
            Objects.requireNonNull(annotation);
            if (annotation instanceof Qualifier qualifier) {
                return qualifier;
            }
            return Qualifier.builder()
                    .typeName(maybeNamed(annotation.typeName()))
                    .values(removeEmptyProperties(annotation.values()))
                    .build();
        }

        /**
         * Creates a {@link jakarta.inject.Named} qualifier.
         *
         * @param name the name
         * @return named qualifier
         */
        @Prototype.FactoryMethod
        static Qualifier createNamed(String name) {
            Objects.requireNonNull(name);
            return Qualifier.builder()
                    .typeName(CommonQualifiers.NAMED)
                    .value(name)
                    .build();
        }

        /**
         * Creates a {@link jakarta.inject.Named} qualifier.
         *
         * @param name the name
         * @return named qualifier
         */
        @Prototype.FactoryMethod
        static Qualifier createNamed(Named name) {
            Objects.requireNonNull(name);
            Qualifier.Builder builder = Qualifier.builder()
                    .typeName(CommonQualifiers.NAMED);
            if (!name.value().isEmpty()) {
                builder.value(name.value());
            }
            return builder.build();
        }

        /**
         * Creates a {@link jakarta.inject.Named} qualifier.
         *
         * @param name the name
         * @return named qualifier
         */
        @Prototype.FactoryMethod
        static Qualifier createNamed(ClassNamed name) {
            Objects.requireNonNull(name);
            return Qualifier.builder()
                    .typeName(CommonQualifiers.NAMED)
                    .value(name.value().getName())
                    .build();
        }

        /**
         * Creates a {@link jakarta.inject.Named} qualifier from a class name.
         *
         * @param className class whose name will be used
         * @return named qualifier
         */
        @Prototype.FactoryMethod
        static Qualifier createNamed(Class<?> className) {
            Objects.requireNonNull(className);
            return Qualifier.builder()
                    .typeName(CommonQualifiers.NAMED)
                    .value(className.getName())
                    .build();
        }

        private static TypeName maybeNamed(String qualifierTypeName) {
            if (qualifierTypeName.equals(ClassNamed.class.getName())) {
                return CommonQualifiers.NAMED;
            }
            return TypeName.create(qualifierTypeName);
        }

        private static TypeName maybeNamed(TypeName qualifierType) {
            if (CLASS_NAMED.equals(qualifierType)) {
                return CommonQualifiers.NAMED;
            }
            return qualifierType;
        }

        private static Map<String, Object> removeEmptyProperties(Map<String, Object> values) {
            HashMap<String, Object> result = new HashMap<>(values);
            result.entrySet().removeIf(entry -> {
                Object value = entry.getValue();
                return value instanceof String str && str.isBlank();
            });
            return result;
        }
    }
}
