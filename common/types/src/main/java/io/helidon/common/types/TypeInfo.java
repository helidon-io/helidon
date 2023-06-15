/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

import java.util.Objects;
import java.util.Optional;

import io.helidon.common.Errors;

/**
 * Represents the model object for a type.
 *
 * @see #builder()
 */
public interface TypeInfo extends TypeInfoBlueprint, io.helidon.builder.api.Prototype {
    /**
     * Create a new fluent API builder to customize configuration.
     *
     * @return a new builder
     */
    static Builder builder() {
        return new Builder();
    }

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
     * Fluent API builder base for {@link io.helidon.common.types.TypeInfo}.
     *
     * @param <BUILDER> type of the builder extending this abstract builder
     * @param <PROTOTYPE> type of the prototype interface that would be built by {@link #buildPrototype()}
     */
    abstract class BuilderBase<BUILDER extends BuilderBase<BUILDER, PROTOTYPE>, PROTOTYPE extends TypeInfo>
            implements io.helidon.builder.api.Prototype.Builder<BUILDER, PROTOTYPE>, TypeInfo {
        private final java.util.List<TypedElementInfo> elementInfo = new java.util.ArrayList<>();
        private final java.util.List<TypedElementInfo> otherElementInfo = new java.util.ArrayList<>();
        private final java.util.Map<TypeName, java.util.List<Annotation>> referencedTypeNamesToAnnotations =
                new java.util.LinkedHashMap<>();
        private final java.util.Map<TypeName, String> referencedModuleNames = new java.util.LinkedHashMap<>();
        private final java.util.List<TypeInfo> interfaceTypeInfo = new java.util.ArrayList<>();
        private final java.util.Set<String> modifiers = new java.util.LinkedHashSet<>();
        private final java.util.List<Annotation> annotations = new java.util.ArrayList<>();
        private TypeName typeName;
        private String typeKind;
        private TypeInfo superTypeInfo;

        /**
         * Protected to support extensibility.
         *
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
            return me();
        }

        /**
         * Update this builder from an existing prototype builder instance.
         *
         * @param builder existing builder prototype to update this builder from
         * @return updated builder instance
         */
        public BUILDER from(BuilderBase<?, ?> builder) {
            if (builder.typeName() != null) {
                typeName(builder.typeName());
            }
            if (builder.typeKind() != null) {
                typeKind(builder.typeKind());
            }
            addElementInfo(builder.elementInfo());
            addOtherElementInfo(builder.otherElementInfo());
            addReferencedTypeNamesToAnnotations(builder.referencedTypeNamesToAnnotations());
            addReferencedModuleNames(builder.referencedModuleNames());
            superTypeInfo(builder.superTypeInfo());
            addInterfaceTypeInfo(builder.interfaceTypeInfo());
            addModifiers(builder.modifiers());
            addAnnotations(builder.annotations());
            return me();
        }

        /**
         * Handles providers and interceptors.
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
         * The type name.
         *
         * @param typeName the type name
         * @return updated builder instance
         * @see #typeName()
         */
        public BUILDER typeName(TypeName typeName) {
            Objects.requireNonNull(typeName);
            this.typeName = typeName;
            return me();
        }

        /**
         * The type name.
         *
         * @param consumer consumer of builder for
         * the type name
         * @return updated builder instance
         * @see #typeName()
         */
        public BUILDER typeName(java.util.function.Consumer<TypeName.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = TypeName.builder();
            consumer.accept(builder);
            this.typeName(builder.build());
            return me();
        }

        /**
         * The type element kind.
         *
         * @param typeKind the type element kind (e.g., "{@value TypeValues#KIND_INTERFACE}",
         * "{@value TypeValues#KIND_ANNOTATION_TYPE}",
         * etc.)
         * @return updated builder instance
         * @see #typeKind()
         */
        public BUILDER typeKind(String typeKind) {
            Objects.requireNonNull(typeKind);
            this.typeKind = typeKind;
            return me();
        }

        /**
         * The elements that make up the type that are relevant for processing.
         *
         * @param elementInfo the elements that make up the type that are relevant for processing
         * @return updated builder instance
         * @see #elementInfo()
         */
        public BUILDER elementInfo(java.util.List<? extends TypedElementInfo> elementInfo) {
            Objects.requireNonNull(elementInfo);
            this.elementInfo.clear();
            this.elementInfo.addAll(elementInfo);
            return me();
        }

        /**
         * The elements that make up the type that are relevant for processing.
         *
         * @param elementInfo the elements that make up the type that are relevant for processing
         * @return updated builder instance
         * @see #elementInfo()
         */
        public BUILDER addElementInfo(java.util.List<? extends TypedElementInfo> elementInfo) {
            Objects.requireNonNull(elementInfo);
            this.elementInfo.addAll(elementInfo);
            return me();
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
            return me();
        }

        /**
         * The elements that make up the type that are relevant for processing.
         *
         * @param consumer the elements that make up the type that are relevant for processing
         * @return updated builder instance
         * @see #elementInfo()
         */
        public BUILDER addElementInfo(java.util.function.Consumer<TypedElementInfo.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = TypedElementInfo.builder();
            consumer.accept(builder);
            this.elementInfo.add(builder.build());
            return me();
        }

        /**
         * The elements that make up this type that are considered "other", or being skipped because they are irrelevant to
         * processing.
         *
         * @param otherElementInfo the elements that still make up the type, but are otherwise deemed irrelevant for processing
         * @return updated builder instance
         * @see #otherElementInfo()
         */
        public BUILDER otherElementInfo(java.util.List<? extends TypedElementInfo> otherElementInfo) {
            Objects.requireNonNull(otherElementInfo);
            this.otherElementInfo.clear();
            this.otherElementInfo.addAll(otherElementInfo);
            return me();
        }

        /**
         * The elements that make up this type that are considered "other", or being skipped because they are irrelevant to
         * processing.
         *
         * @param otherElementInfo the elements that still make up the type, but are otherwise deemed irrelevant for processing
         * @return updated builder instance
         * @see #otherElementInfo()
         */
        public BUILDER addOtherElementInfo(java.util.List<? extends TypedElementInfo> otherElementInfo) {
            Objects.requireNonNull(otherElementInfo);
            this.otherElementInfo.addAll(otherElementInfo);
            return me();
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
            return me();
        }

        /**
         * The elements that make up this type that are considered "other", or being skipped because they are irrelevant to
         * processing.
         *
         * @param consumer the elements that still make up the type, but are otherwise deemed irrelevant for processing
         * @return updated builder instance
         * @see #otherElementInfo()
         */
        public BUILDER addOtherElementInfo(java.util.function.Consumer<TypedElementInfo.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = TypedElementInfo.builder();
            consumer.accept(builder);
            this.otherElementInfo.add(builder.build());
            return me();
        }

        /**
         * Any Map, List, Set, or method that has {@link TypeName#typeArguments()} will be analyzed and any
         * type arguments will have
         * its annotations added here. Note that this only applies to non-built-in types.
         *
         * This method replaces all values with the new ones.
         * @param referencedTypeNamesToAnnotations all referenced types
         * @return updated builder instance
         * @see #referencedTypeNamesToAnnotations()
         */
        public BUILDER referencedTypeNamesToAnnotations(java.util.Map<? extends TypeName,
                ? extends java.util.List<Annotation>> referencedTypeNamesToAnnotations) {
            Objects.requireNonNull(referencedTypeNamesToAnnotations);
            this.referencedTypeNamesToAnnotations.clear();
            this.referencedTypeNamesToAnnotations.putAll(referencedTypeNamesToAnnotations);
            return me();
        }

        /**
         * Any Map, List, Set, or method that has {@link TypeName#typeArguments()} will be analyzed and any
         * type arguments will have
         * its annotations added here. Note that this only applies to non-built-in types.
         *
         * This method keeps existing values, then puts all new values into the map.
         * @param referencedTypeNamesToAnnotations all referenced types
         * @return updated builder instance
         * @see #referencedTypeNamesToAnnotations()
         */
        public BUILDER addReferencedTypeNamesToAnnotations(java.util.Map<? extends TypeName,
                ? extends java.util.List<Annotation>> referencedTypeNamesToAnnotations) {
            Objects.requireNonNull(referencedTypeNamesToAnnotations);
            this.referencedTypeNamesToAnnotations.putAll(referencedTypeNamesToAnnotations);
            return me();
        }

        /**
         * Any Map, List, Set, or method that has {@link TypeName#typeArguments()} will be analyzed and any
         * type arguments will have
         * its annotations added here. Note that this only applies to non-built-in types.
         *
         * This method adds a new value to the map value, or creates a new value.
         * @param key key to add to
         * @param referencedTypeNamesToAnnotation additional value for the key
         * @return updated builder instance
         * @see #referencedTypeNamesToAnnotations()
         */
        public BUILDER addReferencedTypeNamesToAnnotation(TypeName key, Annotation referencedTypeNamesToAnnotation) {
            Objects.requireNonNull(key);
            Objects.requireNonNull(referencedTypeNamesToAnnotation);
            this.referencedTypeNamesToAnnotations.compute(key, (k, v) -> {
                v = v == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(v);
                v.add(referencedTypeNamesToAnnotation);
                return v;
            });
            return me();
        }

        /**
         * Any Map, List, Set, or method that has {@link TypeName#typeArguments()} will be analyzed and any
         * type arguments will have
         * its annotations added here. Note that this only applies to non-built-in types.
         *
         * This method adds a new value to the map value, or creates a new value.
         * @param key key to add to
         * @param referencedTypeNamesToAnnotations additional values for the key
         * @return updated builder instance
         * @see #referencedTypeNamesToAnnotations()
         */
        public BUILDER addReferencedTypeNamesToAnnotations(TypeName key,
                                                           java.util.List<Annotation> referencedTypeNamesToAnnotations) {
            Objects.requireNonNull(key);
            Objects.requireNonNull(referencedTypeNamesToAnnotations);
            this.referencedTypeNamesToAnnotations.compute(key, (k, v) -> {
                v = v == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(v);
                v.addAll(referencedTypeNamesToAnnotations);
                return v;
            });
            return me();
        }

        /**
         * Any Map, List, Set, or method that has {@link TypeName#typeArguments()} will be analyzed and any
         * type arguments will have
         * its annotations added here. Note that this only applies to non-built-in types.
         *
         * This method adds a new value to the map, or replaces it if the key already exists.
         * @param key key to add or replace
         * @param referencedTypeNamesToAnnotation new value for the key
         * @return updated builder instance
         * @see #referencedTypeNamesToAnnotations()
         */
        public BUILDER putReferencedTypeNamesToAnnotation(TypeName key,
                                                          java.util.List<Annotation> referencedTypeNamesToAnnotation) {
            Objects.requireNonNull(key);
            Objects.requireNonNull(referencedTypeNamesToAnnotation);
            this.referencedTypeNamesToAnnotations.put(key, java.util.List.copyOf(referencedTypeNamesToAnnotation));
            return me();
        }

        /**
         * Populated if the (external) module name containing the type is known.
         *
         * This method replaces all values with the new ones.
         * @param referencedModuleNames type names to its associated defining module name
         * @return updated builder instance
         * @see #referencedModuleNames()
         */
        public BUILDER referencedModuleNames(java.util.Map<? extends TypeName, ? extends String> referencedModuleNames) {
            Objects.requireNonNull(referencedModuleNames);
            this.referencedModuleNames.clear();
            this.referencedModuleNames.putAll(referencedModuleNames);
            return me();
        }

        /**
         * Populated if the (external) module name containing the type is known.
         *
         * This method keeps existing values, then puts all new values into the map.
         * @param referencedModuleNames type names to its associated defining module name
         * @return updated builder instance
         * @see #referencedModuleNames()
         */
        public BUILDER addReferencedModuleNames(java.util.Map<? extends TypeName, ? extends String> referencedModuleNames) {
            Objects.requireNonNull(referencedModuleNames);
            this.referencedModuleNames.putAll(referencedModuleNames);
            return me();
        }

        /**
         * Populated if the (external) module name containing the type is known.
         *
         * This method adds a new value to the map, or replaces it if the key already exists.
         * @param key key to add or replace
         * @param referencedModuleName new value for the key
         * @return updated builder instance
         * @see #referencedModuleNames()
         */
        public BUILDER putReferencedModuleName(TypeName key, String referencedModuleName) {
            Objects.requireNonNull(key);
            Objects.requireNonNull(referencedModuleName);
            this.referencedModuleNames.put(key, referencedModuleName);
            return me();
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
            return me();
        }

        /**
         * Unset existing value of this property.
         * @return updated builder instance
         * @see #superTypeInfo()
         */
        public BUILDER unsetSuperTypeInfo() {
            this.superTypeInfo = null;
            return me();
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
            return me();
        }

        /**
         * The parent/super class for this type info.
         *
         * @param consumer the super type
         * @return updated builder instance
         * @see #superTypeInfo()
         */
        public BUILDER superTypeInfo(java.util.function.Consumer<TypeInfo.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = TypeInfo.builder();
            consumer.accept(builder);
            this.superTypeInfo(builder.build());
            return me();
        }

        /**
         * The interface classes for this type info.
         *
         * @param interfaceTypeInfo the interface type info
         * @return updated builder instance
         * @see #interfaceTypeInfo()
         */
        public BUILDER interfaceTypeInfo(java.util.List<? extends TypeInfo> interfaceTypeInfo) {
            Objects.requireNonNull(interfaceTypeInfo);
            this.interfaceTypeInfo.clear();
            this.interfaceTypeInfo.addAll(interfaceTypeInfo);
            return me();
        }

        /**
         * The interface classes for this type info.
         *
         * @param interfaceTypeInfo the interface type info
         * @return updated builder instance
         * @see #interfaceTypeInfo()
         */
        public BUILDER addInterfaceTypeInfo(java.util.List<? extends TypeInfo> interfaceTypeInfo) {
            Objects.requireNonNull(interfaceTypeInfo);
            this.interfaceTypeInfo.addAll(interfaceTypeInfo);
            return me();
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
            return me();
        }

        /**
         * The interface classes for this type info.
         *
         * @param consumer the interface type info
         * @return updated builder instance
         * @see #interfaceTypeInfo()
         */
        public BUILDER addInterfaceTypeInfo(java.util.function.Consumer<TypeInfo.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = TypeInfo.builder();
            consumer.accept(builder);
            this.interfaceTypeInfo.add(builder.build());
            return me();
        }

        /**
         * Element modifiers.
         *
         * @param modifiers element modifiers
         * @return updated builder instance
         * @see #modifiers()
         */
        public BUILDER modifiers(java.util.Set<? extends String> modifiers) {
            Objects.requireNonNull(modifiers);
            this.modifiers.clear();
            this.modifiers.addAll(modifiers);
            return me();
        }

        /**
         * Element modifiers.
         *
         * @param modifiers element modifiers
         * @return updated builder instance
         * @see #modifiers()
         */
        public BUILDER addModifiers(java.util.Set<? extends String> modifiers) {
            Objects.requireNonNull(modifiers);
            this.modifiers.addAll(modifiers);
            return me();
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
            return me();
        }

        /**
         * The list of known annotations for this element. Note that "known" implies that the annotation is visible, which depends
         * upon the context in which it was build.
         *
         * @param annotations the list of annotations on this element
         * @return updated builder instance
         * @see #annotations()
         */
        public BUILDER annotations(java.util.List<? extends Annotation> annotations) {
            Objects.requireNonNull(annotations);
            this.annotations.clear();
            this.annotations.addAll(annotations);
            return me();
        }

        /**
         * The list of known annotations for this element. Note that "known" implies that the annotation is visible, which depends
         * upon the context in which it was build.
         *
         * @param annotations the list of annotations on this element
         * @return updated builder instance
         * @see #annotations()
         */
        public BUILDER addAnnotations(java.util.List<? extends Annotation> annotations) {
            Objects.requireNonNull(annotations);
            this.annotations.addAll(annotations);
            return me();
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
            return me();
        }

        /**
         * The list of known annotations for this element. Note that "known" implies that the annotation is visible, which depends
         * upon the context in which it was build.
         *
         * @param consumer the list of annotations on this element
         * @return updated builder instance
         * @see #annotations()
         */
        public BUILDER addAnnotation(java.util.function.Consumer<Annotation.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = Annotation.builder();
            consumer.accept(builder);
            this.annotations.add(builder.build());
            return me();
        }

        /**
         * The type name.
         *
         * @return the type name
         */
        @Override
        public TypeName typeName() {
            return typeName;
        }

        /**
         * The type element kind.
         *
         * @return the type kind
         */
        @Override
        public String typeKind() {
            return typeKind;
        }

        /**
         * The elements that make up the type that are relevant for processing.
         *
         * @return the element info
         */
        @Override
        public java.util.List<TypedElementInfo> elementInfo() {
            return elementInfo;
        }

        /**
         * The elements that make up this type that are considered "other", or being skipped because they are irrelevant to
         * processing.
         *
         * @return the other element info
         */
        @Override
        public java.util.List<TypedElementInfo> otherElementInfo() {
            return otherElementInfo;
        }

        /**
         * Any Map, List, Set, or method that has {@link TypeName#typeArguments()} will be analyzed and any
         * type arguments will have
         * its annotations added here. Note that this only applies to non-built-in types.
         *
         * @return the referenced type names to annotations
         */
        @Override
        public java.util.Map<TypeName, java.util.List<Annotation>> referencedTypeNamesToAnnotations() {
            return referencedTypeNamesToAnnotations;
        }

        /**
         * Populated if the (external) module name containing the type is known.
         *
         * @return the referenced module names
         */
        @Override
        public java.util.Map<TypeName, String> referencedModuleNames() {
            return referencedModuleNames;
        }

        /**
         * The parent/super class for this type info.
         *
         * @return the super type info
         */
        @Override
        public Optional<TypeInfo> superTypeInfo() {
            return Optional.ofNullable(superTypeInfo);
        }

        /**
         * The interface classes for this type info.
         *
         * @return the interface type info
         */
        @Override
        public java.util.List<TypeInfo> interfaceTypeInfo() {
            return interfaceTypeInfo;
        }

        /**
         * Element modifiers.
         *
         * @return the modifiers
         */
        @Override
        public java.util.Set<String> modifiers() {
            return modifiers;
        }

        /**
         * The list of known annotations for this element. Note that "known" implies that the annotation is visible, which depends
         * upon the context in which it was build.
         *
         * @return the annotations
         */
        @Override
        public java.util.List<Annotation> annotations() {
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
         * Generated implementation of the prototype, can be extended by descendant prototype implementations.
         */
        protected static class TypeInfoImpl implements TypeInfo {
            private final TypeName typeName;
            private final String typeKind;
            private final java.util.List<TypedElementInfo> elementInfo;
            private final java.util.List<TypedElementInfo> otherElementInfo;
            private final java.util.Map<TypeName, java.util.List<Annotation>> referencedTypeNamesToAnnotations;
            private final java.util.Map<TypeName, String> referencedModuleNames;
            private final Optional<TypeInfo> superTypeInfo;
            private final java.util.List<TypeInfo> interfaceTypeInfo;
            private final java.util.Set<String> modifiers;
            private final java.util.List<Annotation> annotations;

            /**
             * Create an instance providing a builder.
             * @param builder extending builder base of this prototype
             */
            protected TypeInfoImpl(BuilderBase<?, ?> builder) {
                this.typeName = builder.typeName();
                this.typeKind = builder.typeKind();
                this.elementInfo = java.util.List.copyOf(builder.elementInfo());
                this.otherElementInfo = java.util.List.copyOf(builder.otherElementInfo());
                this.referencedTypeNamesToAnnotations = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(
                        builder.referencedTypeNamesToAnnotations()));
                this.referencedModuleNames =
                        java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(builder.referencedModuleNames()));
                this.superTypeInfo = builder.superTypeInfo();
                this.interfaceTypeInfo = java.util.List.copyOf(builder.interfaceTypeInfo());
                this.modifiers = java.util.Collections.unmodifiableSet(new java.util.LinkedHashSet<>(builder.modifiers()));
                this.annotations = java.util.List.copyOf(builder.annotations());
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
            public java.util.List<TypedElementInfo> elementInfo() {
                return elementInfo;
            }

            @Override
            public java.util.List<TypedElementInfo> otherElementInfo() {
                return otherElementInfo;
            }

            @Override
            public java.util.Map<TypeName, java.util.List<Annotation>> referencedTypeNamesToAnnotations() {
                return referencedTypeNamesToAnnotations;
            }

            @Override
            public java.util.Map<TypeName, String> referencedModuleNames() {
                return referencedModuleNames;
            }

            @Override
            public Optional<TypeInfo> superTypeInfo() {
                return superTypeInfo;
            }

            @Override
            public java.util.List<TypeInfo> interfaceTypeInfo() {
                return interfaceTypeInfo;
            }

            @Override
            public java.util.Set<String> modifiers() {
                return modifiers;
            }

            @Override
            public java.util.List<Annotation> annotations() {
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
                return Objects.equals(typeName, other.typeName())
                        && Objects.equals(typeKind, other.typeKind())
                        && Objects.equals(elementInfo, other.elementInfo())
                        && Objects.equals(superTypeInfo, other.superTypeInfo())
                        && Objects.equals(modifiers, other.modifiers())
                        && Objects.equals(annotations, other.annotations());
            }

            @Override
            public int hashCode() {
                return Objects.hash(typeName, typeKind, elementInfo, superTypeInfo, modifiers, annotations);
            }
        }
    }

    /**
     * Fluent API builder for {@link io.helidon.common.types.TypeInfo}.
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
