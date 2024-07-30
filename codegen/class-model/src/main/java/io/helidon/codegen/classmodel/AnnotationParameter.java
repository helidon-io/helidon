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
package io.helidon.codegen.classmodel;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;

import io.helidon.common.types.TypeName;

/**
 * Annotation parameter model.
 */
public final class AnnotationParameter extends CommonComponent {

    private final String value;

    private AnnotationParameter(Builder builder) {
        super(builder);
        this.value = resolveValueToString(builder.type(), builder.value);
    }

    /**
     * Create new {@link Builder}.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    void writeComponent(ModelWriter writer, Set<String> declaredTokens, ImportOrganizer imports, ClassType classType)
            throws IOException {
        writer.write(name() + " = " + value);
    }

    private static String resolveValueToString(Type type, Object value) {
        Class<?> valueClass = value.getClass();
        if (valueClass.isEnum()) {
            return valueClass.getSimpleName() + "." + ((Enum<?>) value).name();
        } else if (type.fqTypeName().equals(String.class.getName())) {
            String stringValue = value.toString();
            if (!stringValue.startsWith("\"") && !stringValue.endsWith("\"")) {
                return "\"" + stringValue + "\"";
            }
        } else if (value instanceof TypeName typeName) {
            return typeName.fqName() + ".class";
        }
        return value.toString();
    }

    String value() {
        return value;
    }

    /**
     * Fluent API builder for {@link AnnotationParameter}.
     */
    public static final class Builder extends CommonComponent.Builder<Builder, AnnotationParameter> {

        private Object value;

        private Builder() {
        }

        @Override
        public AnnotationParameter build() {
            if (value == null || name() == null) {
                throw new ClassModelException("Annotation parameter needs to have value and type set");
            }
            return new AnnotationParameter(this);
        }

        @Override
        public Builder name(String name) {
            return super.name(name);
        }

        /**
         * Set annotation parameter value.
         *
         * @param value annotation parameter value
         * @return updated builder instance
         */
        public Builder value(Object value) {
            this.value = Objects.requireNonNull(value);
            return this;
        }

        @Override
        public Builder type(TypeName type) {
            return super.type(type);
        }

        @Override
        public Builder type(String type) {
            return super.type(type);
        }

        @Override
        public Builder type(Class<?> type) {
            return super.type(type);
        }

    }
}
