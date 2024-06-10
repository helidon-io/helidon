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
package io.helidon.common.processor.classmodel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import io.helidon.common.types.TypeName;

/**
 * Method parameter model.
 *
 * @deprecated use {@code helidon-codegen-class-model} instead.
 */
@Deprecated(forRemoval = true, since = "4.1.0")
public final class Parameter extends AnnotatedComponent {

    private final boolean optional;
    private final List<String> description;

    private Parameter(Builder builder) {
        super(builder);
        this.optional = builder.optional;
        this.description = List.copyOf(builder.description);
    }

    /**
     * Create new {@link Builder} instance.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    void writeComponent(ModelWriter writer, Set<String> declaredTokens, ImportOrganizer imports, ClassType classType)
            throws IOException {
        for (Annotation annotation : annotations()) {
            annotation.writeComponent(writer, declaredTokens, imports, classType);
            writer.write(" ");
        }
        type().writeComponent(writer, declaredTokens, imports, classType);
        if (optional) {
            writer.write("...");
        }
        writer.write(" " + name());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Parameter parameter = (Parameter) o;
        return optional == parameter.optional
                && type().equals(parameter.type());
    }

    @Override
    public int hashCode() {
        return Objects.hash(optional);
    }

    @Override
    public String toString() {
        return "Parameter{type=" + type().fqTypeName() + ", simpleType=" + type().simpleTypeName() + ", name=" + name() + "}";
    }

    List<String> description() {
        return description;
    }

    /**
     * Fluent API builder for {@link Parameter}.
     */
    public static final class Builder extends AnnotatedComponent.Builder<Builder, Parameter> {

        private boolean optional = false;
        private final List<String> description = new ArrayList<>();

        private Builder() {
        }

        @Override
        public Parameter build() {
            if (type() == null || name() == null) {
                throw new ClassModelException("Annotation parameter must have name and type set");
            }
            return new Parameter(this);
        }

        /**
         * Whether this parameter is optional.
         *
         * @param optional optional parameter
         * @return updated builder instance
         */
        public Builder optional(boolean optional) {
            this.optional = optional;
            return this;
        }

        @Override
        public Builder description(List<String> description) {
            this.description.clear();
            this.description.addAll(description);
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
