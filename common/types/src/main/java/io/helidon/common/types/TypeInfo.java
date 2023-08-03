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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.helidon.builder.api.Prototype;
import io.helidon.common.Errors;

/**
 * Represents the model object for a type.
 *
 * @see #builder()
 */
public interface TypeInfo extends Prototype.Api, TypeInfoBlueprint {

    /**
     * Create a new fluent API builder from an existing instance.
     *
     * @param instance an existing instance used as a base for the builder
     * @return a builder based on an instance
     */
    static Builder builder(TypeInfo instance) {
        return TypeInfo.builder().from(instance);
    }

    /**
     * Create a new fluent API builder to customize configuration.
     *
     * @return a new builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent API builder base for {@link TypeInfo}.
     *
     * @param <BUILDER> type of the builder extending this abstract builder
     * @param <PROTOTYPE> type of the prototype interface that would be built by {@link #buildPrototype()}
     */
    abstract class BuilderBase<BUILDER extends BuilderBase<BUILDER, PROTOTYPE>, PROTOTYPE extends TypeInfo> implements Prototype.Builder<BUILDER, PROTOTYPE> {

        private final List<Annotation> annotations = new ArrayList<>();
        private final List<TypedElementInfo> elementInfo = new ArrayList<>();
        private final List<TypeInfo> interfaceTypeInfo = new ArrayList<>();
        private final List<TypedElementInfo> otherElementInfo = new ArrayList<>();
        private final Map<TypeName, String> referencedModuleNames = new LinkedHashMap<>();
        private final Map<TypeName, List<Annotation>> referencedTypeNamesToAnnotations = new LinkedHashMap<>();
        private final Set<String> modifiers = new LinkedHashSet<>();
        private String typeKind;
        private TypeInfo superTypeInfo;
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
        public BUILDER from(TypeInfo prototype) {
            typeName(prototype.typeName());
            typeKind(prototype.typeKind());
            addElementInfo(prototype.elementInfo());
            addOtherElementInfo(prototype.otherElementInfo());
            addReferencedTypeNamesToAnnotations(prototype.referencedTypeNamesToAnnotations());
            addReferencedModuleNames(prototype.referencedModuleNames());
            superTypeInfo(prototype.superTypeInfo());
            addInterfaceTypeInfo(prototype.interfaceTypeInfo());
            addModifiers(prototype.modifiers());
            addAnnotations(prototype.annotations());
            return self();
        }

        /**
         * Update this builder from an existing prototype builder instance.
         *
         * @param builder existing builder prototype to update this builder from
         * @return updated builder instance
         */
        public BUILDER from(BuilderBase<?, ?> builder) {
            builder.typeName().ifPresent(this::typeName);
            builder.typeKind().ifPresent(this::typeKind);
            addElementInfo(builder.elementInfo());
            addOtherElementInfo(builder.otherElementInfo());
            addReferencedTypeNamesToAnnotations(builder.referencedTypeNamesToAnnotations());
            addReferencedModuleNames(builder.referencedModuleNames());
            builder.superTypeInfo().ifPresent(this::superTypeInfo);
            addInterfaceTypeInfo(builder.interfaceTypeInfo());
            addModifiers(builder.modifiers());
            addAnnotations(builder.annotations());
            return self();
        }

        /**
         * The type name.
         *
         * @param typeName the type name
         * @return updated builder instance
         * @see #typeName()
         */
        public BUILDER typeName(TypeName typeName) {
            Objects.requireNonNull(typeName);
            this.typeName = typeName;
            return self();
        }

        /**
         * The type name.
         *
         * @param consumer consumer of builder for
         *                 the type name
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
         * The type name.
         *
         * @param supplier supplier of
         *                 the type name
         * @return updated builder instance
         * @see #typeName()
         */
        public BUILDER typeName(Supplier<? extends TypeName> supplier) {
            Objects.requireNonNull(supplier);
            this.typeName(supplier.get());
            return self();
        }

        /**
         * The type element kind.
         * <p>
         * Such as
         * <ul>
         *     <li>{@value TypeValues#KIND_INTERFACE}</li>
         *     <li>{@value TypeValues#KIND_ANNOTATION_TYPE}</li>
         *     <li>and other constants on {@link TypeValues}</li>
         * </ul>
         *
         * @param typeKind the type element kind.
         * @return updated builder instance
         * @see #typeKind()
         */
        public BUILDER typeKind(String typeKind) {
            Objects.requireNonNull(typeKind);
            this.typeKind = typeKind;
            return self();
        }

        /**
         * The elements that make up the type that are relevant for processing.
         *
         * @param elementInfo the elements that make up the type that are relevant for processing
         * @return updated builder instance
         * @see #elementInfo()
         */
        public BUILDER elementInfo(List<? extends TypedElementInfo> elementInfo) {
            Objects.requireNonNull(elementInfo);
            this.elementInfo.clear();
            this.elementInfo.addAll(elementInfo);
            return self();
        }

        /**
         * The elements that make up the type that are relevant for processing.
         *
         * @param elementInfo the elements that make up the type that are relevant for processing
         * @return updated builder instance
         * @see #elementInfo()
         */
        public BUILDER addElementInfo(List<? extends TypedElementInfo> elementInfo) {
            Objects.requireNonNull(elementInfo);
            this.elementInfo.addAll(elementInfo);
            return self();
        }

        /**
         * The elements that make up the type that are relevant for processing.
         *
         * @param elementInfo the elements that make up the type that are relevant for processing
         * @return updated builder instance
         * @see #elementInfo()
         */
        public BUILDER addElementInfo(TypedElementInfo elementInfo) {
            Objects.requireNonNull(elementInfo);
            this.elementInfo.add(elementInfo);
            return self();
        }

        /**
         * The elements that make up the type that are relevant for processing.
         *
         * @param consumer the elements that make up the type that are relevant for processing
         * @return updated builder instance
         * @see #elementInfo()
         */
        public BUILDER addElementInfo(Consumer<TypedElementInfo.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = TypedElementInfo.builder();
            consumer.accept(builder);
            this.elementInfo.add(builder.build());
            return self();
        }

        /**
         * The elements that make up this type that are considered "other", or being skipped because they are irrelevant to
         * processing.
         *
         * @param otherElementInfo the elements that still make up the type, but are otherwise deemed irrelevant for processing
         * @return updated builder instance
         * @see #otherElementInfo()
         */
        public BUILDER otherElementInfo(List<? extends TypedElementInfo> otherElementInfo) {
            Objects.requireNonNull(otherElementInfo);
            this.otherElementInfo.clear();
            this.otherElementInfo.addAll(otherElementInfo);
            return self();
        }

        /**
         * The elements that make up this type that are considered "other", or being skipped because they are irrelevant to
         * processing.
         *
         * @param otherElementInfo the elements that still make up the type, but are otherwise deemed irrelevant for processing
         * @return updated builder instance
         * @see #otherElementInfo()
         */
        public BUILDER addOtherElementInfo(List<? extends TypedElementInfo> otherElementInfo) {
            Objects.requireNonNull(otherElementInfo);
            this.otherElementInfo.addAll(otherElementInfo);
            return self();
        }

        /**
         * The elements that make up this type that are considered "other", or being skipped because they are irrelevant to
         * processing.
         *
         * @param otherElementInfo the elements that still make up the type, but are otherwise deemed irrelevant for processing
         * @return updated builder instance
         * @see #otherElementInfo()
         */
        public BUILDER addOtherElementInfo(TypedElementInfo otherElementInfo) {
            Objects.requireNonNull(otherElementInfo);
            this.otherElementInfo.add(otherElementInfo);
            return self();
        }

        /**
         * The elements that make up this type that are considered "other", or being skipped because they are irrelevant to
         * processing.
         *
         * @param consumer the elements that still make up the type, but are otherwise deemed irrelevant for processing
         * @return updated builder instance
         * @see #otherElementInfo()
         */
        public BUILDER addOtherElementInfo(Consumer<TypedElementInfo.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = TypedElementInfo.builder();
            consumer.accept(builder);
            this.otherElementInfo.add(builder.build());
            return self();
        }

        /**
         * This method replaces all values with the new ones.
         *
         * @param referencedTypeNamesToAnnotations all referenced types
         * @return updated builder instance
         * @see #referencedTypeNamesToAnnotations()
         */
        public BUILDER referencedTypeNamesToAnnotations(Map<? extends TypeName, List<Annotation>> referencedTypeNamesToAnnotations) {
            Objects.requireNonNull(referencedTypeNamesToAnnotations);
            this.referencedTypeNamesToAnnotations.clear();
            this.referencedTypeNamesToAnnotations.putAll(referencedTypeNamesToAnnotations);
            return self();
        }

        /**
         * This method keeps existing values, then puts all new values into the map.
         *
         * @param referencedTypeNamesToAnnotations all referenced types
         * @return updated builder instance
         * @see #referencedTypeNamesToAnnotations()
         */
        public BUILDER addReferencedTypeNamesToAnnotations(Map<? extends TypeName, List<Annotation>> referencedTypeNamesToAnnotations) {
            Objects.requireNonNull(referencedTypeNamesToAnnotations);
            this.referencedTypeNamesToAnnotations.putAll(referencedTypeNamesToAnnotations);
            return self();
        }

        /**
         * This method adds a new value to the map value, or creates a new value.
         *
         * @param key key to add to
         * @param referencedTypeNamesToAnnotation additional value for the key
         * @return updated builder instance
         * @see #referencedTypeNamesToAnnotations()
         */
        public BUILDER addReferencedTypeNamesToAnnotation(TypeName key, Annotation referencedTypeNamesToAnnotation) {
            Objects.requireNonNull(key);
            Objects.requireNonNull(referencedTypeNamesToAnnotation);
            this.referencedTypeNamesToAnnotations.compute(key, (k, v) -> {
                v = v == null ? new ArrayList<>() : new ArrayList<>(v);
                v.add(referencedTypeNamesToAnnotation);
                return v;
            });
            return self();
        }

        /**
         * This method adds a new value to the map value, or creates a new value.
         *
         * @param key key to add to
         * @param referencedTypeNamesToAnnotations additional values for the key
         * @return updated builder instance
         * @see #referencedTypeNamesToAnnotations()
         */
        public BUILDER addReferencedTypeNamesToAnnotations(TypeName key, List<Annotation> referencedTypeNamesToAnnotations) {
            Objects.requireNonNull(key);
            Objects.requireNonNull(referencedTypeNamesToAnnotations);
            this.referencedTypeNamesToAnnotations.compute(key, (k, v) -> {
                v = v == null ? new ArrayList<>() : new ArrayList<>(v);
                v.addAll(referencedTypeNamesToAnnotations);
                return v;
            });
            return self();
        }

        /**
         * This method adds a new value to the map, or replaces it if the key already exists.
         *
         * @param key key to add or replace
         * @param referencedTypeNamesToAnnotation new value for the key
         * @return updated builder instance
         * @see #referencedTypeNamesToAnnotations()
         */
        public BUILDER putReferencedTypeNamesToAnnotation(TypeName key, List<Annotation> referencedTypeNamesToAnnotation) {
            Objects.requireNonNull(key);
            Objects.requireNonNull(referencedTypeNamesToAnnotation);
            this.referencedTypeNamesToAnnotations.put(key, List.copyOf(referencedTypeNamesToAnnotation));
            return self();
        }

        /**
         * This method replaces all values with the new ones.
         *
         * @param referencedModuleNames type names to its associated defining module name
         * @return updated builder instance
         * @see #referencedModuleNames()
         */
        public BUILDER referencedModuleNames(Map<? extends TypeName, ? extends String> referencedModuleNames) {
            Objects.requireNonNull(referencedModuleNames);
            this.referencedModuleNames.clear();
            this.referencedModuleNames.putAll(referencedModuleNames);
            return self();
        }

        /**
         * This method keeps existing values, then puts all new values into the map.
         *
         * @param referencedModuleNames type names to its associated defining module name
         * @return updated builder instance
         * @see #referencedModuleNames()
         */
        public BUILDER addReferencedModuleNames(Map<? extends TypeName, ? extends String> referencedModuleNames) {
            Objects.requireNonNull(referencedModuleNames);
            this.referencedModuleNames.putAll(referencedModuleNames);
            return self();
        }

        /**
         * This method adds a new value to the map, or replaces it if the key already exists.
         *
         * @param key key to add or replace
         * @param referencedModuleName new value for the key
         * @return updated builder instance
         * @see #referencedModuleNames()
         */
        public BUILDER putReferencedModuleName(TypeName key, String referencedModuleName) {
            Objects.requireNonNull(key);
            Objects.requireNonNull(referencedModuleName);
            this.referencedModuleNames.put(key, referencedModuleName);
            return self();
        }

        /**
         * Clear existing value of this property.
         *
         * @return updated builder instance
         * @see #superTypeInfo()
         */
        public BUILDER clearSuperTypeInfo() {
            this.superTypeInfo = null;
            return self();
        }

        /**
         * The parent/super class for this type info.
         *
         * @param superTypeInfo the super type
         * @return updated builder instance
         * @see #superTypeInfo()
         */
        public BUILDER superTypeInfo(TypeInfo superTypeInfo) {
            Objects.requireNonNull(superTypeInfo);
            this.superTypeInfo = superTypeInfo;
            return self();
        }

        /**
         * The parent/super class for this type info.
         *
         * @param consumer the super type
         * @return updated builder instance
         * @see #superTypeInfo()
         */
        public BUILDER superTypeInfo(Consumer<Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = TypeInfo.builder();
            consumer.accept(builder);
            this.superTypeInfo(builder.build());
            return self();
        }

        /**
         * The interface classes for this type info.
         *
         * @param interfaceTypeInfo the interface type info
         * @return updated builder instance
         * @see #interfaceTypeInfo()
         */
        public BUILDER interfaceTypeInfo(List<? extends TypeInfo> interfaceTypeInfo) {
            Objects.requireNonNull(interfaceTypeInfo);
            this.interfaceTypeInfo.clear();
            this.interfaceTypeInfo.addAll(interfaceTypeInfo);
            return self();
        }

        /**
         * The interface classes for this type info.
         *
         * @param interfaceTypeInfo the interface type info
         * @return updated builder instance
         * @see #interfaceTypeInfo()
         */
        public BUILDER addInterfaceTypeInfo(List<? extends TypeInfo> interfaceTypeInfo) {
            Objects.requireNonNull(interfaceTypeInfo);
            this.interfaceTypeInfo.addAll(interfaceTypeInfo);
            return self();
        }

        /**
         * The interface classes for this type info.
         *
         * @param interfaceTypeInfo the interface type info
         * @return updated builder instance
         * @see #interfaceTypeInfo()
         */
        public BUILDER addInterfaceTypeInfo(TypeInfo interfaceTypeInfo) {
            Objects.requireNonNull(interfaceTypeInfo);
            this.interfaceTypeInfo.add(interfaceTypeInfo);
            return self();
        }

        /**
         * The interface classes for this type info.
         *
         * @param consumer the interface type info
         * @return updated builder instance
         * @see #interfaceTypeInfo()
         */
        public BUILDER addInterfaceTypeInfo(Consumer<Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = TypeInfo.builder();
            consumer.accept(builder);
            this.interfaceTypeInfo.add(builder.build());
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
         * The type name.
         *
         * @return the type name
         */
        public Optional<TypeName> typeName() {
            return Optional.ofNullable(typeName);
        }

        /**
         * The type element kind.
         * <p>
         * Such as
         * <ul>
         *     <li>{@value TypeValues#KIND_INTERFACE}</li>
         *     <li>{@value TypeValues#KIND_ANNOTATION_TYPE}</li>
         *     <li>and other constants on {@link TypeValues}</li>
         * </ul>
         *
         * @return the type kind
         */
        public Optional<String> typeKind() {
            return Optional.ofNullable(typeKind);
        }

        /**
         * The elements that make up the type that are relevant for processing.
         *
         * @return the element info
         */
        public List<TypedElementInfo> elementInfo() {
            return elementInfo;
        }

        /**
         * The elements that make up this type that are considered "other", or being skipped because they are irrelevant to
         * processing.
         *
         * @return the other element info
         */
        public List<TypedElementInfo> otherElementInfo() {
            return otherElementInfo;
        }

        /**
         * Any Map, List, Set, or method that has {@link TypeName#typeArguments()} will be analyzed and any
         * type arguments will have
         * its annotations added here. Note that this only applies to non-built-in types.
         *
         * @return the referenced type names to annotations
         */
        public Map<TypeName, List<Annotation>> referencedTypeNamesToAnnotations() {
            return referencedTypeNamesToAnnotations;
        }

        /**
         * Populated if the (external) module name containing the type is known.
         *
         * @return the referenced module names
         */
        public Map<TypeName, String> referencedModuleNames() {
            return referencedModuleNames;
        }

        /**
         * The parent/super class for this type info.
         *
         * @return the super type info
         */
        public Optional<TypeInfo> superTypeInfo() {
            return Optional.ofNullable(superTypeInfo);
        }

        /**
         * The interface classes for this type info.
         *
         * @return the interface type info
         */
        public List<TypeInfo> interfaceTypeInfo() {
            return interfaceTypeInfo;
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
         * The list of known annotations for this element. Note that "known" implies that the annotation is visible, which depends
         * upon the context in which it was build.
         *
         * @return the annotations
         */
        public List<Annotation> annotations() {
            return annotations;
        }

        @Override
        public String toString() {
            return "TypeInfoBuilder{"
                    + "typeName=" + typeName + ","
                    + "typeKind=" + typeKind + ","
                    + "elementInfo=" + elementInfo + ","
                    + "superTypeInfo=" + superTypeInfo + ","
                    + "modifiers=" + modifiers + ","
                    + "annotations=" + annotations
                    + "}";
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
            if (typeKind == null) {
                collector.fatal(getClass(), "Property \"type-kind\" is required, but not set");
            }
            collector.collect().checkValid();
        }

        /**
         * The parent/super class for this type info.
         *
         * @param superTypeInfo the super type
         * @return updated builder instance
         * @see #superTypeInfo()
         */
        BUILDER superTypeInfo(Optional<? extends TypeInfo> superTypeInfo) {
            Objects.requireNonNull(superTypeInfo);
            this.superTypeInfo = superTypeInfo.orElse(null);
            return self();
        }

        /**
         * Generated implementation of the prototype, can be extended by descendant prototype implementations.
         */
        protected static class TypeInfoImpl implements TypeInfo {

            private final List<Annotation> annotations;
            private final List<TypedElementInfo> elementInfo;
            private final List<TypeInfo> interfaceTypeInfo;
            private final List<TypedElementInfo> otherElementInfo;
            private final Map<TypeName, String> referencedModuleNames;
            private final Map<TypeName, List<Annotation>> referencedTypeNamesToAnnotations;
            private final Optional<TypeInfo> superTypeInfo;
            private final Set<String> modifiers;
            private final String typeKind;
            private final TypeName typeName;

            /**
             * Create an instance providing a builder.
             *
             * @param builder extending builder base of this prototype
             */
            protected TypeInfoImpl(BuilderBase<?, ?> builder) {
                this.typeName = builder.typeName().get();
                this.typeKind = builder.typeKind().get();
                this.elementInfo = List.copyOf(builder.elementInfo());
                this.otherElementInfo = List.copyOf(builder.otherElementInfo());
                this.referencedTypeNamesToAnnotations = Collections.unmodifiableMap(new LinkedHashMap<>(builder.referencedTypeNamesToAnnotations()));
                this.referencedModuleNames = Collections.unmodifiableMap(new LinkedHashMap<>(builder.referencedModuleNames()));
                this.superTypeInfo = builder.superTypeInfo();
                this.interfaceTypeInfo = List.copyOf(builder.interfaceTypeInfo());
                this.modifiers = Collections.unmodifiableSet(new LinkedHashSet<>(builder.modifiers()));
                this.annotations = List.copyOf(builder.annotations());
            }

            @Override
            public TypeName typeName() {
                return typeName;
            }

            @Override
            public String typeKind() {
                return typeKind;
            }

            @Override
            public List<TypedElementInfo> elementInfo() {
                return elementInfo;
            }

            @Override
            public List<TypedElementInfo> otherElementInfo() {
                return otherElementInfo;
            }

            @Override
            public Map<TypeName, List<Annotation>> referencedTypeNamesToAnnotations() {
                return referencedTypeNamesToAnnotations;
            }

            @Override
            public Map<TypeName, String> referencedModuleNames() {
                return referencedModuleNames;
            }

            @Override
            public Optional<TypeInfo> superTypeInfo() {
                return superTypeInfo;
            }

            @Override
            public List<TypeInfo> interfaceTypeInfo() {
                return interfaceTypeInfo;
            }

            @Override
            public Set<String> modifiers() {
                return modifiers;
            }

            @Override
            public List<Annotation> annotations() {
                return annotations;
            }

            @Override
            public String toString() {
                return "TypeInfo{"
                        + "typeName=" + typeName + ","
                        + "typeKind=" + typeKind + ","
                        + "elementInfo=" + elementInfo + ","
                        + "superTypeInfo=" + superTypeInfo + ","
                        + "modifiers=" + modifiers + ","
                        + "annotations=" + annotations
                        + "}";
            }

            @Override
            public boolean equals(Object o) {
                if (o == this) {
                    return true;
                }
                if (!(o instanceof TypeInfo other)) {
                    return false;
                }
                return Objects.equals(typeName, other.typeName()) && Objects.equals(typeKind, other.typeKind()) && Objects.equals(elementInfo, other.elementInfo()) && Objects.equals(superTypeInfo, other.superTypeInfo()) && Objects.equals(modifiers, other.modifiers()) && Objects.equals(annotations, other.annotations());
            }

            @Override
            public int hashCode() {
                return Objects.hash(typeName, typeKind, elementInfo, superTypeInfo, modifiers, annotations);
            }

        }

    }

    /**
     * Fluent API builder for {@link TypeInfo}.
     */
    class Builder extends BuilderBase<Builder, TypeInfo> implements io.helidon.common.Builder<Builder, TypeInfo> {

        private Builder() {
        }

        @Override
        public TypeInfo buildPrototype() {
            preBuildPrototype();
            validatePrototype();
            return new TypeInfoImpl(this);
        }

        @Override
        public TypeInfo build() {
            return buildPrototype();
        }

    }

}
