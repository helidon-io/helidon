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
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.helidon.builder.api.Prototype;
import io.helidon.common.Errors;

/**
 * An annotation with defined values.
 *
 * @see #builder()
 */
public interface TypedElementInfo extends TypedElementInfoBlueprint, Prototype.Api {

    /**
     * Create a new fluent API builder to customize configuration.
     *
     * @return a new builder
     */
    static TypedElementInfo.Builder builder() {
        return new TypedElementInfo.Builder();
    }

    /**
     * Create a new fluent API builder from an existing instance.
     *
     * @param instance an existing instance used as a base for the builder
     * @return a builder based on an instance
     */
    static TypedElementInfo.Builder builder(TypedElementInfo instance) {
        return TypedElementInfo.builder().from(instance);
    }

    /**
     * Provides a description for this instance.
     *
     * @return provides the {typeName}{space}{elementName}
     */
    String toDeclaration();

    /**
     * Fluent API builder base for {@link TypedElementInfo}.
     *
     * @param <BUILDER> type of the builder extending this abstract builder
     * @param <PROTOTYPE> type of the prototype interface that would be built by {@link #buildPrototype()}
     */
    abstract class BuilderBase<BUILDER extends TypedElementInfo.BuilderBase<BUILDER, PROTOTYPE>, PROTOTYPE extends TypedElementInfo> implements Prototype.Builder<BUILDER, PROTOTYPE> {

        private final List<Annotation> annotations = new ArrayList<>();
        private final List<Annotation> elementTypeAnnotations = new ArrayList<>();
        private final List<TypeName> componentTypes = new ArrayList<>();
        private final List<TypedElementInfo> parameterArguments = new ArrayList<>();
        private final Set<String> modifiers = new LinkedHashSet<>();
        private String defaultValue;
        private String description;
        private String elementName;
        private String elementTypeKind;
        private TypeName enclosingType;
        private TypeName typeName;

        /**
         * Protected to support extensibility.
         */
        protected BuilderBase() {
        }

        /**
         * Update this builder from an existing prototype instance.
         *
         * @param prototype existing prototype to update this builder from
         * @return updated builder instance
         */
        public BUILDER from(TypedElementInfo prototype) {
            description(prototype.description());
            typeName(prototype.typeName());
            elementName(prototype.elementName());
            elementTypeKind(prototype.elementTypeKind());
            defaultValue(prototype.defaultValue());
            addElementTypeAnnotations(prototype.elementTypeAnnotations());
            addComponentTypes(prototype.componentTypes());
            addModifiers(prototype.modifiers());
            enclosingType(prototype.enclosingType());
            addParameterArguments(prototype.parameterArguments());
            addAnnotations(prototype.annotations());
            return self();
        }

        /**
         * Update this builder from an existing prototype builder instance.
         *
         * @param builder existing builder prototype to update this builder from
         * @return updated builder instance
         */
        public BUILDER from(TypedElementInfo.BuilderBase<?, ?> builder) {
            builder.description().ifPresent(this::description);
            builder.typeName().ifPresent(this::typeName);
            builder.elementName().ifPresent(this::elementName);
            builder.elementTypeKind().ifPresent(this::elementTypeKind);
            builder.defaultValue().ifPresent(this::defaultValue);
            addElementTypeAnnotations(builder.elementTypeAnnotations());
            addComponentTypes(builder.componentTypes());
            addModifiers(builder.modifiers());
            builder.enclosingType().ifPresent(this::enclosingType);
            addParameterArguments(builder.parameterArguments());
            addAnnotations(builder.annotations());
            return self();
        }

        /**
         * Clear existing value of this property.
         *
         * @return updated builder instance
         * @see #description()
         */
        public BUILDER clearDescription() {
            this.description = null;
            return self();
        }

        /**
         * Description, such as javadoc, if available.
         *
         * @param description description of this element
         * @return updated builder instance
         * @see #description()
         */
        public BUILDER description(String description) {
            Objects.requireNonNull(description);
            this.description = description;
            return self();
        }

        /**
         * The type name for the element (e.g., java.util.List). If the element is a method, then this is the return type of
         * the method.
         *
         * @param typeName the type name of the element
         * @return updated builder instance
         * @see #typeName()
         */
        public BUILDER typeName(TypeName typeName) {
            Objects.requireNonNull(typeName);
            this.typeName = typeName;
            return self();
        }

        /**
         * The type name for the element (e.g., java.util.List). If the element is a method, then this is the return type of
         * the method.
         *
         * @param consumer consumer of builder for
         *                 the type name of the element
         * @return updated builder instance
         * @see #typeName()
         */
        public BUILDER typeName(Consumer<TypeName.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = TypeName.builder();
            consumer.accept(builder);
            this.typeName(builder.build());
            return self();
        }

        /**
         * The type name for the element (e.g., java.util.List). If the element is a method, then this is the return type of
         * the method.
         *
         * @param supplier supplier of
         *                 the type name of the element
         * @return updated builder instance
         * @see #typeName()
         */
        public BUILDER typeName(Supplier<? extends TypeName> supplier) {
            Objects.requireNonNull(supplier);
            this.typeName(supplier.get());
            return self();
        }

        /**
         * The element (e.g., method, field, etc) name.
         *
         * @param elementName the name of the element
         * @return updated builder instance
         * @see #elementName()
         */
        public BUILDER elementName(String elementName) {
            Objects.requireNonNull(elementName);
            this.elementName = elementName;
            return self();
        }

        /**
         * The kind of element (e.g., method, field, etc).
         *
         * @param elementTypeKind the element kind
         * @return updated builder instance
         * @see #elementTypeKind()
         */
        public BUILDER elementTypeKind(String elementTypeKind) {
            Objects.requireNonNull(elementTypeKind);
            this.elementTypeKind = elementTypeKind;
            return self();
        }

        /**
         * Clear existing value of this property.
         *
         * @return updated builder instance
         * @see #defaultValue()
         */
        public BUILDER clearDefaultValue() {
            this.defaultValue = null;
            return self();
        }

        /**
         * The default value assigned to the element, represented as a string.
         *
         * @param defaultValue the default value as a string
         * @return updated builder instance
         * @see #defaultValue()
         */
        public BUILDER defaultValue(String defaultValue) {
            Objects.requireNonNull(defaultValue);
            this.defaultValue = defaultValue;
            return self();
        }

        /**
         * The list of known annotations on the type name referenced by {@link #typeName()}.
         *
         * @param elementTypeAnnotations the list of annotations on this element's (return) type.
         * @return updated builder instance
         * @see #elementTypeAnnotations()
         */
        public BUILDER elementTypeAnnotations(List<? extends Annotation> elementTypeAnnotations) {
            Objects.requireNonNull(elementTypeAnnotations);
            this.elementTypeAnnotations.clear();
            this.elementTypeAnnotations.addAll(elementTypeAnnotations);
            return self();
        }

        /**
         * The list of known annotations on the type name referenced by {@link #typeName()}.
         *
         * @param elementTypeAnnotations the list of annotations on this element's (return) type.
         * @return updated builder instance
         * @see #elementTypeAnnotations()
         */
        public BUILDER addElementTypeAnnotations(List<? extends Annotation> elementTypeAnnotations) {
            Objects.requireNonNull(elementTypeAnnotations);
            this.elementTypeAnnotations.addAll(elementTypeAnnotations);
            return self();
        }

        /**
         * Returns the component type names describing the element.
         *
         * @param componentTypes the component type names of the element
         * @return updated builder instance
         * @see #componentTypes()
         */
        public BUILDER componentTypes(List<? extends TypeName> componentTypes) {
            Objects.requireNonNull(componentTypes);
            this.componentTypes.clear();
            this.componentTypes.addAll(componentTypes);
            return self();
        }

        /**
         * Returns the component type names describing the element.
         *
         * @param componentTypes the component type names of the element
         * @return updated builder instance
         * @see #componentTypes()
         */
        public BUILDER addComponentTypes(List<? extends TypeName> componentTypes) {
            Objects.requireNonNull(componentTypes);
            this.componentTypes.addAll(componentTypes);
            return self();
        }

        /**
         * Element modifiers.
         *
         * @param modifiers element modifiers
         * @return updated builder instance
         * @see #modifiers()
         */
        public BUILDER modifiers(Set<? extends String> modifiers) {
            Objects.requireNonNull(modifiers);
            this.modifiers.clear();
            this.modifiers.addAll(modifiers);
            return self();
        }

        /**
         * Element modifiers.
         *
         * @param modifiers element modifiers
         * @return updated builder instance
         * @see #modifiers()
         */
        public BUILDER addModifiers(Set<? extends String> modifiers) {
            Objects.requireNonNull(modifiers);
            this.modifiers.addAll(modifiers);
            return self();
        }

        /**
         * Element modifiers.
         *
         * @param modifier element modifiers
         * @return updated builder instance
         * @see #modifiers()
         */
        public BUILDER addModifier(String modifier) {
            Objects.requireNonNull(modifier);
            this.modifiers.add(modifier);
            return self();
        }

        /**
         * Clear existing value of this property.
         *
         * @return updated builder instance
         * @see #enclosingType()
         */
        public BUILDER clearEnclosingType() {
            this.enclosingType = null;
            return self();
        }

        /**
         * The enclosing type name for this typed element. Applicable when this instance represents a
         * {@link io.helidon.common.types.TypeValues#KIND_FIELD}, or
         * {@link io.helidon.common.types.TypeValues#KIND_METHOD}, or
         * {@link io.helidon.common.types.TypeValues#KIND_PARAMETER}
         *
         * @param enclosingType the enclosing type element
         * @return updated builder instance
         * @see #enclosingType()
         */
        public BUILDER enclosingType(TypeName enclosingType) {
            Objects.requireNonNull(enclosingType);
            this.enclosingType = enclosingType;
            return self();
        }

        /**
         * The enclosing type name for this typed element. Applicable when this instance represents a
         * {@link io.helidon.common.types.TypeValues#KIND_FIELD}, or
         * {@link io.helidon.common.types.TypeValues#KIND_METHOD}, or
         * {@link io.helidon.common.types.TypeValues#KIND_PARAMETER}
         *
         * @param consumer the enclosing type element
         * @return updated builder instance
         * @see #enclosingType()
         */
        public BUILDER enclosingType(Consumer<TypeName.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = TypeName.builder();
            consumer.accept(builder);
            this.enclosingType(builder.build());
            return self();
        }

        /**
         * Parameter arguments applicable if this type element represents a {@link io.helidon.common.types.TypeValues#KIND_METHOD}.
         * Each instance of this list
         * will be the individual {@link io.helidon.common.types.TypeValues#KIND_PARAMETER}'s for the method.
         *
         * @param parameterArguments the list of parameters belonging to this method if applicable
         * @return updated builder instance
         * @see #parameterArguments()
         */
        public BUILDER parameterArguments(List<? extends TypedElementInfo> parameterArguments) {
            Objects.requireNonNull(parameterArguments);
            this.parameterArguments.clear();
            this.parameterArguments.addAll(parameterArguments);
            return self();
        }

        /**
         * Parameter arguments applicable if this type element represents a {@link io.helidon.common.types.TypeValues#KIND_METHOD}.
         * Each instance of this list
         * will be the individual {@link io.helidon.common.types.TypeValues#KIND_PARAMETER}'s for the method.
         *
         * @param parameterArguments the list of parameters belonging to this method if applicable
         * @return updated builder instance
         * @see #parameterArguments()
         */
        public BUILDER addParameterArguments(List<? extends TypedElementInfo> parameterArguments) {
            Objects.requireNonNull(parameterArguments);
            this.parameterArguments.addAll(parameterArguments);
            return self();
        }

        /**
         * Parameter arguments applicable if this type element represents a {@link io.helidon.common.types.TypeValues#KIND_METHOD}.
         * Each instance of this list
         * will be the individual {@link io.helidon.common.types.TypeValues#KIND_PARAMETER}'s for the method.
         *
         * @param parameterArgument the list of parameters belonging to this method if applicable
         * @return updated builder instance
         * @see #parameterArguments()
         */
        public BUILDER addParameterArgument(TypedElementInfo parameterArgument) {
            Objects.requireNonNull(parameterArgument);
            this.parameterArguments.add(parameterArgument);
            return self();
        }

        /**
         * Parameter arguments applicable if this type element represents a {@link io.helidon.common.types.TypeValues#KIND_METHOD}.
         * Each instance of this list
         * will be the individual {@link io.helidon.common.types.TypeValues#KIND_PARAMETER}'s for the method.
         *
         * @param consumer the list of parameters belonging to this method if applicable
         * @return updated builder instance
         * @see #parameterArguments()
         */
        public BUILDER addParameterArgument(Consumer<TypedElementInfo.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = TypedElementInfo.builder();
            consumer.accept(builder);
            this.parameterArguments.add(builder.build());
            return self();
        }

        /**
         * The list of known annotations for this element. Note that "known" implies that the annotation is visible, which depends
         * upon the context in which it was build.
         *
         * @param annotations the list of annotations on this element
         * @return updated builder instance
         * @see #annotations()
         */
        public BUILDER annotations(List<? extends Annotation> annotations) {
            Objects.requireNonNull(annotations);
            this.annotations.clear();
            this.annotations.addAll(annotations);
            return self();
        }

        /**
         * The list of known annotations for this element. Note that "known" implies that the annotation is visible, which depends
         * upon the context in which it was build.
         *
         * @param annotations the list of annotations on this element
         * @return updated builder instance
         * @see #annotations()
         */
        public BUILDER addAnnotations(List<? extends Annotation> annotations) {
            Objects.requireNonNull(annotations);
            this.annotations.addAll(annotations);
            return self();
        }

        /**
         * The list of known annotations for this element. Note that "known" implies that the annotation is visible, which depends
         * upon the context in which it was build.
         *
         * @param annotation the list of annotations on this element
         * @return updated builder instance
         * @see #annotations()
         */
        public BUILDER addAnnotation(Annotation annotation) {
            Objects.requireNonNull(annotation);
            this.annotations.add(annotation);
            return self();
        }

        /**
         * The list of known annotations for this element. Note that "known" implies that the annotation is visible, which depends
         * upon the context in which it was build.
         *
         * @param consumer the list of annotations on this element
         * @return updated builder instance
         * @see #annotations()
         */
        public BUILDER addAnnotation(Consumer<Annotation.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = Annotation.builder();
            consumer.accept(builder);
            this.annotations.add(builder.build());
            return self();
        }

        /**
         * Description, such as javadoc, if available.
         *
         * @return the description
         */
        public Optional<String> description() {
            return Optional.ofNullable(description);
        }

        /**
         * The type name for the element (e.g., java.util.List). If the element is a method, then this is the return type of
         * the method.
         *
         * @return the type name
         */
        public Optional<TypeName> typeName() {
            return Optional.ofNullable(typeName);
        }

        /**
         * The element (e.g., method, field, etc) name.
         *
         * @return the element name
         */
        public Optional<String> elementName() {
            return Optional.ofNullable(elementName);
        }

        /**
         * The kind of element (e.g., method, field, etc).
         *
         * @return the element type kind
         */
        public Optional<String> elementTypeKind() {
            return Optional.ofNullable(elementTypeKind);
        }

        /**
         * The default value assigned to the element, represented as a string.
         *
         * @return the default value
         */
        public Optional<String> defaultValue() {
            return Optional.ofNullable(defaultValue);
        }

        /**
         * The list of known annotations on the type name referenced by {@link #typeName()}.
         *
         * @return the element type annotations
         */
        public List<Annotation> elementTypeAnnotations() {
            return elementTypeAnnotations;
        }

        /**
         * Returns the component type names describing the element.
         *
         * @return the component types
         */
        public List<TypeName> componentTypes() {
            return componentTypes;
        }

        /**
         * Element modifiers.
         *
         * @return the modifiers
         */
        public Set<String> modifiers() {
            return modifiers;
        }

        /**
         * The enclosing type name for this typed element. Applicable when this instance represents a
         * {@link io.helidon.common.types.TypeValues#KIND_FIELD}, or
         * {@link io.helidon.common.types.TypeValues#KIND_METHOD}, or
         * {@link io.helidon.common.types.TypeValues#KIND_PARAMETER}
         *
         * @return the enclosing type
         */
        public Optional<TypeName> enclosingType() {
            return Optional.ofNullable(enclosingType);
        }

        /**
         * Parameter arguments applicable if this type element represents a {@link io.helidon.common.types.TypeValues#KIND_METHOD}.
         * Each instance of this list
         * will be the individual {@link io.helidon.common.types.TypeValues#KIND_PARAMETER}'s for the method.
         *
         * @return the parameter arguments
         */
        public List<TypedElementInfo> parameterArguments() {
            return parameterArguments;
        }

        /**
         * The list of known annotations for this element. Note that "known" implies that the annotation is visible, which depends
         * upon the context in which it was build.
         *
         * @return the annotations
         */
        public List<Annotation> annotations() {
            return annotations;
        }

        /**
         * Handles providers and decorators.
         */
        protected void preBuildPrototype() {
        }

        /**
         * Validates required properties.
         */
        protected void validatePrototype() {
            Errors.Collector collector = Errors.collector();
            if (typeName == null) {
                collector.fatal(getClass(), "Property \"type-name\" is required, but not set");
            }
            if (elementName == null) {
                collector.fatal(getClass(), "Property \"element-name\" is required, but not set");
            }
            if (elementTypeKind == null) {
                collector.fatal(getClass(), "Property \"element-type-kind\" is required, but not set");
            }
            collector.collect().checkValid();
        }

        /**
         * Description, such as javadoc, if available.
         *
         * @param description description of this element
         * @return updated builder instance
         * @see #description()
         */
        BUILDER description(Optional<? extends String> description) {
            Objects.requireNonNull(description);
            this.description = description.orElse(null);
            return self();
        }

        /**
         * The default value assigned to the element, represented as a string.
         *
         * @param defaultValue the default value as a string
         * @return updated builder instance
         * @see #defaultValue()
         */
        BUILDER defaultValue(Optional<? extends String> defaultValue) {
            Objects.requireNonNull(defaultValue);
            this.defaultValue = defaultValue.orElse(null);
            return self();
        }

        /**
         * The enclosing type name for this typed element. Applicable when this instance represents a
         * {@link io.helidon.common.types.TypeValues#KIND_FIELD}, or
         * {@link io.helidon.common.types.TypeValues#KIND_METHOD}, or
         * {@link io.helidon.common.types.TypeValues#KIND_PARAMETER}
         *
         * @param enclosingType the enclosing type element
         * @return updated builder instance
         * @see #enclosingType()
         */
        BUILDER enclosingType(Optional<? extends TypeName> enclosingType) {
            Objects.requireNonNull(enclosingType);
            this.enclosingType = enclosingType.orElse(null);
            return self();
        }

        /**
         * Generated implementation of the prototype, can be extended by descendant prototype implementations.
         */
        protected static class TypedElementInfoImpl implements TypedElementInfo {

            private final List<Annotation> annotations;
            private final List<Annotation> elementTypeAnnotations;
            private final List<TypeName> componentTypes;
            private final List<TypedElementInfo> parameterArguments;
            private final Optional<TypeName> enclosingType;
            private final Optional<String> defaultValue;
            private final Optional<String> description;
            private final Set<String> modifiers;
            private final String elementName;
            private final String elementTypeKind;
            private final TypeName typeName;

            /**
             * Create an instance providing a builder.
             *
             * @param builder extending builder base of this prototype
             */
            protected TypedElementInfoImpl(TypedElementInfo.BuilderBase<?, ?> builder) {
                this.description = builder.description();
                this.typeName = builder.typeName().get();
                this.elementName = builder.elementName().get();
                this.elementTypeKind = builder.elementTypeKind().get();
                this.defaultValue = builder.defaultValue();
                this.elementTypeAnnotations = List.copyOf(builder.elementTypeAnnotations());
                this.componentTypes = List.copyOf(builder.componentTypes());
                this.modifiers = Collections.unmodifiableSet(new LinkedHashSet<>(builder.modifiers()));
                this.enclosingType = builder.enclosingType();
                this.parameterArguments = List.copyOf(builder.parameterArguments());
                this.annotations = List.copyOf(builder.annotations());
            }

            @Override
            public String toString() {
                return TypedElementInfoSupport.toString(this);
            }

            @Override
            public String toDeclaration() {
                return TypedElementInfoSupport.toDeclaration(this);
            }

            @Override
            public Optional<String> description() {
                return description;
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
                return elementTypeKind;
            }

            @Override
            public Optional<String> defaultValue() {
                return defaultValue;
            }

            @Override
            public List<Annotation> elementTypeAnnotations() {
                return elementTypeAnnotations;
            }

            @Override
            public List<TypeName> componentTypes() {
                return componentTypes;
            }

            @Override
            public Set<String> modifiers() {
                return modifiers;
            }

            @Override
            public Optional<TypeName> enclosingType() {
                return enclosingType;
            }

            @Override
            public List<TypedElementInfo> parameterArguments() {
                return parameterArguments;
            }

            @Override
            public List<Annotation> annotations() {
                return annotations;
            }

            @Override
            public boolean equals(Object o) {
                if (o == this) {
                    return true;
                }
                if (!(o instanceof TypedElementInfo other)) {
                    return false;
                }
                return Objects.equals(typeName, other.typeName())
                        && Objects.equals(elementName, other.elementName())
                        && Objects.equals(elementTypeKind, other.elementTypeKind())
                        && Objects.equals(enclosingType, other.enclosingType())
                        && Objects.equals(parameterArguments, other.parameterArguments())
                        && Objects.equals(annotations, other.annotations());
            }

            @Override
            public int hashCode() {
                return Objects.hash(typeName, elementName, elementTypeKind, enclosingType, parameterArguments, annotations);
            }

        }

    }

    /**
     * Fluent API builder for {@link TypedElementInfo}.
     */
    class Builder extends TypedElementInfo.BuilderBase<TypedElementInfo.Builder, TypedElementInfo> implements io.helidon.common.Builder<TypedElementInfo.Builder, TypedElementInfo> {

        private Builder() {
        }

        @Override
        public TypedElementInfo buildPrototype() {
            preBuildPrototype();
            validatePrototype();
            return new TypedElementInfoImpl(this);
        }

        @Override
        public TypedElementInfo build() {
            return buildPrototype();
        }

    }

}
