/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.service.registry;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import io.helidon.builder.api.Prototype;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;

/*
 Support for QualifierBlueprint
 */
class QualifierSupport {
    static final class CustomMethods {
        /**
         * Represents a wildcard {@link io.helidon.service.registry.Service.Named} qualifier.
         */
        @Prototype.Constant
        static final Qualifier WILDCARD_NAMED = createNamed(Service.Named.WILDCARD_NAME);

        /**
         * Represents an instance named with the default name: {@value Service.Named#DEFAULT_NAME}.
         */
        @Prototype.Constant
        static final Qualifier DEFAULT_NAMED = createNamed(Service.Named.DEFAULT_NAME);

        /**
         * Represents a qualifier used for injecting name of {@link io.helidon.service.registry.Service.PerInstance}
         * instances.
         */
        @Prototype.Constant
        static final Qualifier CREATE_FOR_NAME = create(Service.InstanceName.class);

        private CustomMethods() {
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
            TypeName typeName = TypeName.create(qualifierType);
            TypeName qualifierTypeName = maybeNamed(typeName);
            return Qualifier.builder().typeName(qualifierTypeName).build();
        }

        /**
         * Creates a qualifier with a value from an annotation.
         *
         * @param qualifierType the qualifier type
         * @param value         the value property
         * @return qualifier
         */
        @Prototype.FactoryMethod
        static Qualifier create(Class<? extends java.lang.annotation.Annotation> qualifierType, String value) {
            Objects.requireNonNull(qualifierType);
            TypeName typeName = TypeName.create(qualifierType);
            TypeName qualifierTypeName = maybeNamed(typeName);
            return Qualifier.builder()
                    .typeName(qualifierTypeName)
                    .putValue("value", value)
                    .build();
        }

        /**
         * Creates a qualifier from an annotation.
         *
         * @param qualifierType the qualifier type
         * @return qualifier
         */
        @Prototype.FactoryMethod
        static Qualifier create(TypeName qualifierType) {
            Objects.requireNonNull(qualifierType);
            TypeName qualifierTypeName = maybeNamed(qualifierType);
            return Qualifier.builder().typeName(qualifierTypeName).build();
        }

        /**
         * Creates a qualifier with a value from an annotation.
         *
         * @param qualifierType the qualifier type
         * @param value         the value property
         * @return qualifier
         */
        @Prototype.FactoryMethod
        static Qualifier create(TypeName qualifierType, String value) {
            Objects.requireNonNull(qualifierType);
            TypeName qualifierTypeName = maybeNamed(qualifierType);
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
         * Creates a {@link Service.Named} qualifier.
         *
         * @param name the name
         * @return named qualifier
         */
        @Prototype.FactoryMethod
        static Qualifier createNamed(String name) {
            Objects.requireNonNull(name);
            return Qualifier.builder()
                    .typeName(Service.Named.TYPE)
                    .value(name)
                    .build();
        }

        /**
         * Creates a {@link Service.Named} qualifier.
         *
         * @param name the name
         * @return named qualifier
         */
        @Prototype.FactoryMethod
        static Qualifier createNamed(Service.Named name) {
            Objects.requireNonNull(name);
            Qualifier.Builder builder = Qualifier.builder()
                    .typeName(Service.Named.TYPE);
            if (!name.value().isEmpty()) {
                builder.value(name.value());
            }
            return builder.build();
        }

        /**
         * Creates a {@link Service.Named} qualifier.
         *
         * @param name the name
         * @return named qualifier
         */
        @Prototype.FactoryMethod
        static Qualifier createNamed(Service.NamedByType name) {
            Objects.requireNonNull(name);
            return Qualifier.builder()
                    .typeName(Service.Named.TYPE)
                    .value(name.value().getName())
                    .build();
        }

        /**
         * Creates a {@link Service.Named} qualifier from a class name.
         *
         * @param className class whose name will be used
         * @return named qualifier
         */
        @Prototype.FactoryMethod
        static Qualifier createNamed(Class<?> className) {
            Objects.requireNonNull(className);
            return Qualifier.builder()
                    .typeName(Service.Named.TYPE)
                    .value(className.getName())
                    .build();
        }

        private static TypeName maybeNamed(TypeName qualifierType) {
            if (Service.NamedByType.TYPE.equals(qualifierType)) {
                return Service.Named.TYPE;
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
