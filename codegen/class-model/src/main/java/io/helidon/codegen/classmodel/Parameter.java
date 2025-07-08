/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import io.helidon.common.types.TypeName;

/**
 * Method parameter model.
 */
public final class Parameter extends AnnotatedComponent {

    private final boolean vararg;
    private final List<String> description;

    private Parameter(Builder builder) {
        super(builder);
        this.vararg = builder.vararg;
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
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Parameter parameter = (Parameter) o;
        return vararg == parameter.vararg
                && type().equals(parameter.type());
    }

    @Override
    public int hashCode() {
        return Objects.hash(vararg);
    }

    @Override
    public String toString() {
        return "Parameter{type=" + type().fqTypeName() + ", simpleType=" + type().simpleTypeName() + ", name=" + name() + "}";
    }

    /**
     * Description (javadoc lines) of this parameter.
     *
     * @return parameter description
     */
    public List<String> description() {
        return description;
    }

    @Override
    void writeComponent(ModelWriter writer, Set<String> declaredTokens, ImportOrganizer imports, ClassType classType)
            throws IOException {
        for (Annotation annotation : annotations()) {
            annotation.writeComponent(writer, declaredTokens, imports, classType);
            writer.write(" ");
        }
        type().writeComponent(writer, declaredTokens, imports, classType);
        if (vararg) {
            writer.write("...");
        }
        writer.write(" " + name());
    }

    /**
     * Fluent API builder for {@link Parameter}.
     */
    public static final class Builder extends AnnotatedComponent.Builder<Builder, Parameter> {

        private final List<String> description = new ArrayList<>();
        private boolean vararg = false;

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
         * Whether this parameter is a vararg (zero to n repetitions, declared as {@code Object... objects}).
         * Note that vararg parameter can be only one per method, and it MUST be the last argument defined.
         *
         * @param vararg whether this is a vararg parameter
         * @return updated builder instance
         */
        public Builder vararg(boolean vararg) {
            this.vararg = vararg;
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
            if (type.vararg()) {
                vararg(true);
                return super.type(TypeName.builder(type)
                                          .array(false)
                                          .vararg(false)
                                          .build());
            } else {
                return super.type(type);
            }
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
