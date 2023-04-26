/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.common.types;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static io.helidon.common.types.TypeNameDefault.create;

/**
 * Default implementation for {@link io.helidon.common.types.TypedElementName}.
 */
@SuppressWarnings("unused")
public class TypedElementNameDefault implements TypedElementName {
    private final TypeName typeName;
    private final List<TypeName> componentTypeNames;
    private final String elementName;
    private final String elementKind;
    private final String defaultValue;
    private final List<AnnotationAndValue> annotations;
    private final List<AnnotationAndValue> elementTypeAnnotations;
    private final Set<String> modifierNames;
    private final TypeName enclosingTypeName;
    private final List<TypedElementName> parameters;

    /**
     * Constructor taking the fluent builder.
     *
     * @param b the builder
     * @see #builder()
     */
    protected TypedElementNameDefault(Builder b) {
        this.typeName = b.typeName;
        this.componentTypeNames = List.copyOf(b.componentTypeNames);
        this.elementName = b.elementName;
        this.elementKind = b.elementKind;
        this.defaultValue = b.defaultValue;
        this.annotations = List.copyOf(b.annotations);
        this.elementTypeAnnotations = List.copyOf(b.elementTypeAnnotations);
        this.modifierNames = Set.copyOf(b.modifierNames);
        this.enclosingTypeName = b.enclosingTypeName;
        this.parameters = List.copyOf(b.parameters);
    }

    @Override
    public TypeName typeName() {
        return typeName;
    }

    @Override
    public String elementName() {
        return elementName;
    }

    @Override
    public String elementTypeKind() {
        return elementKind;
    }

    @Override
    public Optional<String> defaultValue() {
        return Optional.ofNullable(defaultValue);
    }

    @Override
    public List<AnnotationAndValue> annotations() {
        return annotations;
    }

    @Override
    public List<AnnotationAndValue> elementTypeAnnotations() {
        return elementTypeAnnotations;
    }

    @Override
    public List<TypeName> componentTypeNames() {
        return componentTypeNames;
    }

    @Override
    public Set<String> modifierNames() {
        return modifierNames;
    }

    @Override
    public Optional<TypeName> enclosingTypeName() {
        return Optional.ofNullable(enclosingTypeName);
    }

    @Override
    public List<TypedElementName> parameterArguments() {
        return parameters;
    }

    @Override
    public int hashCode() {
        return Objects.hash(typeName(), elementName(), elementTypeKind(), annotations(), enclosingTypeName());
    }

    @Override
    public boolean equals(Object another) {
        if (!(another instanceof TypedElementName)) {
            return false;
        }

        TypedElementName other = (TypedElementName) another;
        return Objects.equals(typeName(), other.typeName())
                && Objects.equals(elementName(), other.elementName())
                && Objects.equals(elementTypeKind(), other.elementTypeKind())
                && Objects.equals(annotations(), other.annotations())
                && Objects.equals(enclosingTypeName(), other.enclosingTypeName())
                && Objects.equals(parameterArguments(), other.parameterArguments());
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        if (!TypeInfo.KIND_PARAMETER.equals(elementTypeKind())) {
            TypeName enclosingTypeName = enclosingTypeName().orElse(null);
            if (enclosingTypeName != null) {
                builder.append(enclosingTypeName).append("::");
            }
        }
        builder.append(toDeclaration());
        return builder.toString();
    }

    /**
     * Provides a description for this instance.
     *
     * @return provides the {typeName}{space}{elementName}
     */
    public String toDeclaration() {
        StringBuilder builder = new StringBuilder();
        builder.append(typeName()).append(" ").append(elementName());
        String params = parameterArguments().stream()
                .map(it -> it.typeName() + " " + it.elementName())
                .collect(Collectors.joining(", "));
        if (!params.isBlank()) {
            builder.append("(").append(params).append(")");
        }
        return builder.toString();
    }

    /**
     * Creates a builder for {@link io.helidon.common.types.TypedElementName}.
     *
     * @return a fluent builder
     */
    public static Builder builder() {
        return new Builder();
    }


    /**
     * The fluent builder.
     */
    public static class Builder {
        private final List<TypeName> componentTypeNames = new ArrayList<>();
        private final List<AnnotationAndValue> annotations = new ArrayList<>();
        private final List<AnnotationAndValue> elementTypeAnnotations = new ArrayList<>();
        private final Set<String> modifierNames = new LinkedHashSet<>();
        private final List<TypedElementName> parameters = new ArrayList<>();

        private TypeName typeName;
        private String elementName;
        private String elementKind;
        private String defaultValue;
        private TypeName enclosingTypeName;

        /**
         * Default Constructor.
         */
        protected Builder() {
        }

        /**
         * Set the type name.
         *
         * @param val   the type name value
         * @return this fluent builder
         */
        public Builder typeName(TypeName val) {
            Objects.requireNonNull(val);
            this.typeName = val;
            return this;
        }

        /**
         * Set the type of the element.
         *
         * @param type  the type
         * @return the fluent builder
         */
        public Builder typeName(Class<?> type) {
            return typeName(create(type));
        }

        /**
         * Set the component type names.
         *
         * @param val   the component type values
         * @return this fluent builder
         */
        public Builder componentTypeNames(List<TypeName> val) {
            Objects.requireNonNull(val);
            this.componentTypeNames.clear();
            this.componentTypeNames.addAll(val);
            return this;
        }

        /**
         * Set the element name.
         *
         * @param val   the element name value
         * @return this fluent builder
         */
        public Builder elementName(String val) {
            Objects.requireNonNull(val);
            this.elementName = val;
            return this;
        }

        /**
         * Set the element kind.
         *
         * @param val   the element kind value
         * @return this fluent builder
         */
        public Builder elementKind(String val) {
            Objects.requireNonNull(val);
            this.elementKind = val;
            return this;
        }

        /**
         * Set the default value.
         *
         * @param val   the default value
         * @return this fluent builder
         */
        public Builder defaultValue(String val) {
            Objects.requireNonNull(val);
            this.defaultValue = val;
            return this;
        }

        /**
         * Set the annotations for this element.
         *
         * @param val   the annotation values
         * @return this fluent builder
         */
        public Builder annotations(List<AnnotationAndValue> val) {
            Objects.requireNonNull(val);
            this.annotations.clear();
            this.annotations.addAll(val);
            return this;
        }

        /**
         * Adds a singular annotation.
         *
         * @param annotation the annotation to add
         * @return the fluent builder
         */
        public Builder addAnnotation(AnnotationAndValue annotation) {
            Objects.requireNonNull(annotation);
            this.annotations.add(annotation);
            return this;
        }

        /**
         * Set the annotations for this element type.
         *
         * @param val   the element type annotation values
         * @return this fluent builder
         */
        public Builder elementTypeAnnotations(List<AnnotationAndValue> val) {
            Objects.requireNonNull(val);
            this.elementTypeAnnotations.clear();
            this.elementTypeAnnotations.addAll(val);
            return this;
        }

        /**
         * Sets the modifiers to val.
         *
         * @param val the value
         * @return this fluent builder
         */
        public Builder modifierNames(Collection<String> val) {
            Objects.requireNonNull(val);
            this.modifierNames.clear();
            this.modifierNames.addAll(val);
            return this;
        }

        /**
         * Adds a single modifier val.
         *
         * @param val the value
         * @return this fluent builder
         */
        public Builder addModifierName(String val) {
            Objects.requireNonNull(val);
            modifierNames.add(val);
            return this;
        }

        /**
         * Set the enclosing type name.
         *
         * @param val   the type name value
         * @return this fluent builder
         */
        public Builder enclosingTypeName(TypeName val) {
            Objects.requireNonNull(val);
            this.enclosingTypeName = val;
            return this;
        }

        /**
         * Set the enclosing type of the element.
         *
         * @param val  the type val
         * @return the fluent builder
         */
        public Builder enclosingTypeName(Class<?> val) {
            return enclosingTypeName(create(val));
        }

        /**
         * Set the parameters for this element.
         *
         * @param val the parameter values
         * @return this fluent builder
         */
        public Builder parameterArgumentss(List<TypedElementName> val) {
            Objects.requireNonNull(val);
            this.parameters.clear();
            this.parameters.addAll(val);
            return this;
        }

        /**
         * Adds a singular parameter.
         *
         * @param val the parameter value
         * @return the fluent builder
         */
        public Builder addParameterArgument(TypedElementName val) {
            Objects.requireNonNull(val);
            this.parameters.add(val);
            return this;
        }

        /**
         * Set the enclosing type name.
         *
         * @param val   the type name value
         * @return this fluent builder
         */
        public Builder enclosingTypeName(TypeName val) {
            Objects.requireNonNull(val);
            this.enclosingTypeName = val;
            return this;
        }

        /**
         * Set the enclosing type of the element.
         *
         * @param val  the type
         * @return the fluent builder
         */
        public Builder enclosingTypeName(Class<?> val) {
            return enclosingTypeName(TypeNameDefault.create(val));
        }

        /**
         * Build the instance.
         *
         * @return the built instance
         */
        public TypedElementNameDefault build() {
            Objects.requireNonNull(typeName);
            return new TypedElementNameDefault(this);
        }
    }

}
