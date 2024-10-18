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
public interface TypeInfo extends TypeInfoBlueprint, Prototype.Api {

    /**
     * Create a new fluent API builder to customize configuration.
     *
     * @return a new builder
     */
    static TypeInfo.Builder builder() {
        return new TypeInfo.Builder();
    }

    /**
     * Create a new fluent API builder from an existing instance.
     *
     * @param instance an existing instance used as a base for the builder
     * @return a builder based on an instance
     */
    static TypeInfo.Builder builder(TypeInfo instance) {
        return TypeInfo.builder().from(instance);
    }

    /**
     * Fluent API builder base for {@link TypeInfo}.
     *
     * @param <BUILDER> type of the builder extending this abstract builder
     * @param <PROTOTYPE> type of the prototype interface that would be built by {@link #buildPrototype()}
     */
    abstract class BuilderBase<BUILDER extends TypeInfo.BuilderBase<BUILDER, PROTOTYPE>, PROTOTYPE extends TypeInfo> implements Prototype.Builder<BUILDER, PROTOTYPE> {

        private final List<Annotation> annotations = new ArrayList<>();
        private final List<Annotation> inheritedAnnotations = new ArrayList<>();
        private final List<TypeInfo> interfaceTypeInfo = new ArrayList<>();
        private final List<TypedElementInfo> elementInfo = new ArrayList<>();
        private final List<TypedElementInfo> otherElementInfo = new ArrayList<>();
        private final Map<TypeName, String> referencedModuleNames = new LinkedHashMap<>();
        private final Map<TypeName, List<Annotation>> referencedTypeNamesToAnnotations = new LinkedHashMap<>();
        private final Set<Modifier> elementModifiers = new LinkedHashSet<>();
        private final Set<String> modifiers = new LinkedHashSet<>();
        private AccessModifier accessModifier;
        private boolean isAnnotationsMutated;
        private boolean isElementInfoMutated;
        private boolean isInheritedAnnotationsMutated;
        private boolean isInterfaceTypeInfoMutated;
        private boolean isOtherElementInfoMutated;
        private ElementKind kind;
        private Object originatingElement;
        private String description;
        private String module;
        private String typeKind;
        private TypeInfo superTypeInfo;
        private TypeName typeName;

        /**
         * Protected to support extensibility.
         */
        protected BuilderBase() {
        }

        /**
         * Update this builder from an existing prototype instance. This method disables automatic service discovery.
         *
         * @param prototype existing prototype to update this builder from
         * @return updated builder instance
         */
        public BUILDER from(TypeInfo prototype) {
            typeName(prototype.typeName());
            description(prototype.description());
            typeKind(prototype.typeKind());
            kind(prototype.kind());
            if (!isElementInfoMutated) {
                elementInfo.clear();
            }
            addElementInfo(prototype.elementInfo());
            if (!isOtherElementInfoMutated) {
                otherElementInfo.clear();
            }
            addOtherElementInfo(prototype.otherElementInfo());
            addReferencedTypeNamesToAnnotations(prototype.referencedTypeNamesToAnnotations());
            addReferencedModuleNames(prototype.referencedModuleNames());
            superTypeInfo(prototype.superTypeInfo());
            if (!isInterfaceTypeInfoMutated) {
                interfaceTypeInfo.clear();
            }
            addInterfaceTypeInfo(prototype.interfaceTypeInfo());
            addModifiers(prototype.modifiers());
            addElementModifiers(prototype.elementModifiers());
            accessModifier(prototype.accessModifier());
            module(prototype.module());
            originatingElement(prototype.originatingElement());
            if (!isAnnotationsMutated) {
                annotations.clear();
            }
            addAnnotations(prototype.annotations());
            if (!isInheritedAnnotationsMutated) {
                inheritedAnnotations.clear();
            }
            addInheritedAnnotations(prototype.inheritedAnnotations());
            return self();
        }

        /**
         * Update this builder from an existing prototype builder instance.
         *
         * @param builder existing builder prototype to update this builder from
         * @return updated builder instance
         */
        public BUILDER from(TypeInfo.BuilderBase<?, ?> builder) {
            builder.typeName().ifPresent(this::typeName);
            builder.description().ifPresent(this::description);
            builder.typeKind().ifPresent(this::typeKind);
            builder.kind().ifPresent(this::kind);
            if (isElementInfoMutated) {
                if (builder.isElementInfoMutated) {
                    addElementInfo(builder.elementInfo);
                }
            } else {
                elementInfo.clear();
                addElementInfo(builder.elementInfo);
            }
            if (isOtherElementInfoMutated) {
                if (builder.isOtherElementInfoMutated) {
                    addOtherElementInfo(builder.otherElementInfo);
                }
            } else {
                otherElementInfo.clear();
                addOtherElementInfo(builder.otherElementInfo);
            }
            addReferencedTypeNamesToAnnotations(builder.referencedTypeNamesToAnnotations);
            addReferencedModuleNames(builder.referencedModuleNames);
            builder.superTypeInfo().ifPresent(this::superTypeInfo);
            if (isInterfaceTypeInfoMutated) {
                if (builder.isInterfaceTypeInfoMutated) {
                    addInterfaceTypeInfo(builder.interfaceTypeInfo);
                }
            } else {
                interfaceTypeInfo.clear();
                addInterfaceTypeInfo(builder.interfaceTypeInfo);
            }
            addModifiers(builder.modifiers);
            addElementModifiers(builder.elementModifiers);
            builder.accessModifier().ifPresent(this::accessModifier);
            builder.module().ifPresent(this::module);
            builder.originatingElement().ifPresent(this::originatingElement);
            if (isAnnotationsMutated) {
                if (builder.isAnnotationsMutated) {
                    addAnnotations(builder.annotations);
                }
            } else {
                annotations.clear();
                addAnnotations(builder.annotations);
            }
            if (isInheritedAnnotationsMutated) {
                if (builder.isInheritedAnnotationsMutated) {
                    addInheritedAnnotations(builder.inheritedAnnotations);
                }
            } else {
                inheritedAnnotations.clear();
                addInheritedAnnotations(builder.inheritedAnnotations);
            }
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
         * The type element kind.
         * <p>
         * Such as
         * <ul>
         *     <li>{@value io.helidon.common.types.TypeValues#KIND_INTERFACE}</li>
         *     <li>{@value io.helidon.common.types.TypeValues#KIND_ANNOTATION_TYPE}</li>
         *     <li>and other constants on {@link io.helidon.common.types.TypeValues}</li>
         * </ul>
         *
         * @param typeKind the type element kind.
         * @return updated builder instance
         * @deprecated use {@link #kind()} instead
         * @see io.helidon.common.types.TypeValues#KIND_CLASS and other constants on this class prefixed with {@code TYPE}
         * @see #typeKind()
         */
        @Deprecated(since = "4.1.0", forRemoval = true)
        public BUILDER typeKind(String typeKind) {
            Objects.requireNonNull(typeKind);
            this.typeKind = typeKind;
            return self();
        }

        /**
         * The kind of this type.
         * <p>
         * Such as:
         * <ul>
         *     <li>{@link io.helidon.common.types.ElementKind#CLASS}</li>
         *     <li>{@link io.helidon.common.types.ElementKind#INTERFACE}</li>
         *     <li>{@link io.helidon.common.types.ElementKind#ANNOTATION_TYPE}</li>
         * </ul>
         *
         * @param kind element kind of this type
         * @return updated builder instance
         * @see #kind()
         */
        public BUILDER kind(ElementKind kind) {
            Objects.requireNonNull(kind);
            this.kind = kind;
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
            isElementInfoMutated = true;
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
            isElementInfoMutated = true;
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
            isElementInfoMutated = true;
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
            isOtherElementInfoMutated = true;
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
            isOtherElementInfoMutated = true;
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
            isOtherElementInfoMutated = true;
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
        public BUILDER referencedTypeNamesToAnnotations(
                Map<? extends TypeName, List<Annotation>> referencedTypeNamesToAnnotations) {
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
        public BUILDER addReferencedTypeNamesToAnnotations(Map<? extends TypeName,
                List<Annotation>> referencedTypeNamesToAnnotations) {
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
        public BUILDER superTypeInfo(Consumer<TypeInfo.Builder> consumer) {
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
            isInterfaceTypeInfoMutated = true;
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
            isInterfaceTypeInfoMutated = true;
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
            isInterfaceTypeInfoMutated = true;
            return self();
        }

        /**
         * The interface classes for this type info.
         *
         * @param consumer the interface type info
         * @return updated builder instance
         * @see #interfaceTypeInfo()
         */
        public BUILDER addInterfaceTypeInfo(Consumer<TypeInfo.Builder> consumer) {
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
         * @deprecated use {@link #elementModifiers()} instead
         * @see io.helidon.common.types.TypeValues#MODIFIER_PUBLIC and other constants prefixed with {@code MODIFIER}
         * @see #modifiers()
         */
        @Deprecated(since = "4.1.0", forRemoval = true)
        public BUILDER addModifier(String modifier) {
            Objects.requireNonNull(modifier);
            this.modifiers.add(modifier);
            return self();
        }

        /**
         * Type modifiers.
         *
         * @param elementModifiers set of modifiers that are present on the type (and that we understand)
         * @return updated builder instance
         * @see #elementModifiers()
         */
        public BUILDER elementModifiers(Set<? extends Modifier> elementModifiers) {
            Objects.requireNonNull(elementModifiers);
            this.elementModifiers.clear();
            this.elementModifiers.addAll(elementModifiers);
            return self();
        }

        /**
         * Type modifiers.
         *
         * @param elementModifiers set of modifiers that are present on the type (and that we understand)
         * @return updated builder instance
         * @see #elementModifiers()
         */
        public BUILDER addElementModifiers(Set<? extends Modifier> elementModifiers) {
            Objects.requireNonNull(elementModifiers);
            this.elementModifiers.addAll(elementModifiers);
            return self();
        }

        /**
         * Type modifiers.
         *
         * @param elementModifier set of modifiers that are present on the type (and that we understand)
         * @return updated builder instance
         * @see io.helidon.common.types.Modifier
         * @see #accessModifier()
         * @see #elementModifiers()
         */
        public BUILDER addElementModifier(Modifier elementModifier) {
            Objects.requireNonNull(elementModifier);
            this.elementModifiers.add(elementModifier);
            return self();
        }

        /**
         * Access modifier.
         *
         * @param accessModifier access modifier
         * @return updated builder instance
         * @see #accessModifier()
         */
        public BUILDER accessModifier(AccessModifier accessModifier) {
            Objects.requireNonNull(accessModifier);
            this.accessModifier = accessModifier;
            return self();
        }

        /**
         * Clear existing value of this property.
         *
         * @return updated builder instance
         * @see #module()
         */
        public BUILDER clearModule() {
            this.module = null;
            return self();
        }

        /**
         * Module of this type, if available.
         *
         * @param module module name
         * @return updated builder instance
         * @see #module()
         */
        public BUILDER module(String module) {
            Objects.requireNonNull(module);
            this.module = module;
            return self();
        }

        /**
         * Clear existing value of this property.
         *
         * @return updated builder instance
         * @see #originatingElement()
         */
        public BUILDER clearOriginatingElement() {
            this.originatingElement = null;
            return self();
        }

        /**
         * The element used to create this instance.
         * The type of the object depends on the environment we are in - it may be an {@code TypeElement} in annotation processing,
         * or a {@code ClassInfo} when using classpath scanning.
         *
         * @param originatingElement originating element
         * @return updated builder instance
         * @see #originatingElement()
         */
        public BUILDER originatingElement(Object originatingElement) {
            Objects.requireNonNull(originatingElement);
            this.originatingElement = originatingElement;
            return self();
        }

        /**
         * List of declared and known annotations for this element.
         * Note that "known" implies that the annotation is visible, which depends
         * upon the context in which it was build (such as the {@link java.lang.annotation.Retention of the annotation}).
         *
         * @param annotations the list of annotations declared on this element
         * @return updated builder instance
         * @see #annotations()
         */
        public BUILDER annotations(List<? extends Annotation> annotations) {
            Objects.requireNonNull(annotations);
            isAnnotationsMutated = true;
            this.annotations.clear();
            this.annotations.addAll(annotations);
            return self();
        }

        /**
         * List of declared and known annotations for this element.
         * Note that "known" implies that the annotation is visible, which depends
         * upon the context in which it was build (such as the {@link java.lang.annotation.Retention of the annotation}).
         *
         * @param annotations the list of annotations declared on this element
         * @return updated builder instance
         * @see #annotations()
         */
        public BUILDER addAnnotations(List<? extends Annotation> annotations) {
            Objects.requireNonNull(annotations);
            isAnnotationsMutated = true;
            this.annotations.addAll(annotations);
            return self();
        }

        /**
         * List of declared and known annotations for this element.
         * Note that "known" implies that the annotation is visible, which depends
         * upon the context in which it was build (such as the {@link java.lang.annotation.Retention of the annotation}).
         *
         * @param annotation the list of annotations declared on this element
         * @return updated builder instance
         * @see #annotations()
         */
        public BUILDER addAnnotation(Annotation annotation) {
            Objects.requireNonNull(annotation);
            this.annotations.add(annotation);
            isAnnotationsMutated = true;
            return self();
        }

        /**
         * List of declared and known annotations for this element.
         * Note that "known" implies that the annotation is visible, which depends
         * upon the context in which it was build (such as the {@link java.lang.annotation.Retention of the annotation}).
         *
         * @param consumer the list of annotations declared on this element
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
         * List of all inherited annotations for this element. Inherited annotations are annotations declared
         * on annotations of this element that are also marked as {@link java.lang.annotation.Inherited}.
         * <p>
         * The returned list does not contain {@link #annotations()}. If a meta-annotation is present on multiple
         * annotations, it will be returned once for each such declaration.
         * <p>
         * This method does not return annotations on super types or interfaces!
         *
         * @param inheritedAnnotations list of all meta annotations of this element
         * @return updated builder instance
         * @see #inheritedAnnotations()
         */
        public BUILDER inheritedAnnotations(List<? extends Annotation> inheritedAnnotations) {
            Objects.requireNonNull(inheritedAnnotations);
            isInheritedAnnotationsMutated = true;
            this.inheritedAnnotations.clear();
            this.inheritedAnnotations.addAll(inheritedAnnotations);
            return self();
        }

        /**
         * List of all inherited annotations for this element. Inherited annotations are annotations declared
         * on annotations of this element that are also marked as {@link java.lang.annotation.Inherited}.
         * <p>
         * The returned list does not contain {@link #annotations()}. If a meta-annotation is present on multiple
         * annotations, it will be returned once for each such declaration.
         * <p>
         * This method does not return annotations on super types or interfaces!
         *
         * @param inheritedAnnotations list of all meta annotations of this element
         * @return updated builder instance
         * @see #inheritedAnnotations()
         */
        public BUILDER addInheritedAnnotations(List<? extends Annotation> inheritedAnnotations) {
            Objects.requireNonNull(inheritedAnnotations);
            isInheritedAnnotationsMutated = true;
            this.inheritedAnnotations.addAll(inheritedAnnotations);
            return self();
        }

        /**
         * List of all inherited annotations for this element. Inherited annotations are annotations declared
         * on annotations of this element that are also marked as {@link java.lang.annotation.Inherited}.
         * <p>
         * The returned list does not contain {@link #annotations()}. If a meta-annotation is present on multiple
         * annotations, it will be returned once for each such declaration.
         * <p>
         * This method does not return annotations on super types or interfaces!
         *
         * @param inheritedAnnotation list of all meta annotations of this element
         * @return updated builder instance
         * @see #inheritedAnnotations()
         */
        public BUILDER addInheritedAnnotation(Annotation inheritedAnnotation) {
            Objects.requireNonNull(inheritedAnnotation);
            this.inheritedAnnotations.add(inheritedAnnotation);
            isInheritedAnnotationsMutated = true;
            return self();
        }

        /**
         * List of all inherited annotations for this element. Inherited annotations are annotations declared
         * on annotations of this element that are also marked as {@link java.lang.annotation.Inherited}.
         * <p>
         * The returned list does not contain {@link #annotations()}. If a meta-annotation is present on multiple
         * annotations, it will be returned once for each such declaration.
         * <p>
         * This method does not return annotations on super types or interfaces!
         *
         * @param consumer list of all meta annotations of this element
         * @return updated builder instance
         * @see #inheritedAnnotations()
         */
        public BUILDER addInheritedAnnotation(Consumer<Annotation.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = Annotation.builder();
            consumer.accept(builder);
            this.inheritedAnnotations.add(builder.build());
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
         * Description, such as javadoc, if available.
         *
         * @return the description
         */
        public Optional<String> description() {
            return Optional.ofNullable(description);
        }

        /**
         * The type element kind.
         * <p>
         * Such as
         * <ul>
         *     <li>{@value io.helidon.common.types.TypeValues#KIND_INTERFACE}</li>
         *     <li>{@value io.helidon.common.types.TypeValues#KIND_ANNOTATION_TYPE}</li>
         *     <li>and other constants on {@link io.helidon.common.types.TypeValues}</li>
         * </ul>
         *
         * @return the type kind
         * @deprecated use {@link #kind()} instead
         * @see io.helidon.common.types.TypeValues#KIND_CLASS and other constants on this class prefixed with {@code TYPE}
         * @see #typeKind()
         */
        @Deprecated(since = "4.1.0", forRemoval = true)
        public Optional<String> typeKind() {
            return Optional.ofNullable(typeKind);
        }

        /**
         * The kind of this type.
         * <p>
         * Such as:
         * <ul>
         *     <li>{@link io.helidon.common.types.ElementKind#CLASS}</li>
         *     <li>{@link io.helidon.common.types.ElementKind#INTERFACE}</li>
         *     <li>{@link io.helidon.common.types.ElementKind#ANNOTATION_TYPE}</li>
         * </ul>
         *
         * @return the kind
         */
        public Optional<ElementKind> kind() {
            return Optional.ofNullable(kind);
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
         * Any Map, List, Set, or method that has {@link io.helidon.common.types.TypeName#typeArguments()} will be analyzed and any
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
         * @deprecated use {@link #elementModifiers()} instead
         * @see io.helidon.common.types.TypeValues#MODIFIER_PUBLIC and other constants prefixed with {@code MODIFIER}
         * @see #modifiers()
         */
        @Deprecated(since = "4.1.0", forRemoval = true)
        public Set<String> modifiers() {
            return modifiers;
        }

        /**
         * Type modifiers.
         *
         * @return the element modifiers
         * @see io.helidon.common.types.Modifier
         * @see #accessModifier()
         * @see #elementModifiers()
         */
        public Set<Modifier> elementModifiers() {
            return elementModifiers;
        }

        /**
         * Access modifier.
         *
         * @return the access modifier
         */
        public Optional<AccessModifier> accessModifier() {
            return Optional.ofNullable(accessModifier);
        }

        /**
         * Module of this type, if available.
         *
         * @return the module
         */
        public Optional<String> module() {
            return Optional.ofNullable(module);
        }

        /**
         * The element used to create this instance.
         * The type of the object depends on the environment we are in - it may be an {@code TypeElement} in annotation processing,
         * or a {@code ClassInfo} when using classpath scanning.
         *
         * @return the originating element
         */
        public Optional<Object> originatingElement() {
            return Optional.ofNullable(originatingElement);
        }

        /**
         * List of declared and known annotations for this element.
         * Note that "known" implies that the annotation is visible, which depends
         * upon the context in which it was build (such as the {@link java.lang.annotation.Retention of the annotation}).
         *
         * @return the annotations
         */
        public List<Annotation> annotations() {
            return annotations;
        }

        /**
         * List of all inherited annotations for this element. Inherited annotations are annotations declared
         * on annotations of this element that are also marked as {@link java.lang.annotation.Inherited}.
         * <p>
         * The returned list does not contain {@link #annotations()}. If a meta-annotation is present on multiple
         * annotations, it will be returned once for each such declaration.
         * <p>
         * This method does not return annotations on super types or interfaces!
         *
         * @return the inherited annotations
         */
        public List<Annotation> inheritedAnnotations() {
            return inheritedAnnotations;
        }

        @Override
        public String toString() {
            return "TypeInfoBuilder{"
                    + "typeName=" + typeName + ","
                    + "kind=" + kind + ","
                    + "elementInfo=" + elementInfo + ","
                    + "superTypeInfo=" + superTypeInfo + ","
                    + "elementModifiers=" + elementModifiers + ","
                    + "accessModifier=" + accessModifier + ","
                    + "module=" + module + ","
                    + "annotations=" + annotations + ","
                    + "inheritedAnnotations=" + inheritedAnnotations
                    + "}";
        }

        /**
         * Handles providers and decorators.
         */
        protected void preBuildPrototype() {
            new TypeInfoSupport.TypeInfoDecorator().decorate(this);
        }

        /**
         * Validates required properties.
         */
        protected void validatePrototype() {
            Errors.Collector collector = Errors.collector();
            if (typeName == null) {
                collector.fatal(getClass(), "Property \"typeName\" is required, but not set");
            }
            if (typeKind == null) {
                collector.fatal(getClass(), "Property \"typeKind\" is required, but not set");
            }
            if (kind == null) {
                collector.fatal(getClass(), "Property \"kind\" is required, but not set");
            }
            if (accessModifier == null) {
                collector.fatal(getClass(), "Property \"accessModifier\" must not be null, but not set");
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
        BUILDER description(Optional<String> description) {
            Objects.requireNonNull(description);
            this.description = description.map(java.lang.String.class::cast).orElse(this.description);
            return self();
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
            this.superTypeInfo = superTypeInfo.map(TypeInfo.class::cast).orElse(this.superTypeInfo);
            return self();
        }

        /**
         * Module of this type, if available.
         *
         * @param module module name
         * @return updated builder instance
         * @see #module()
         */
        BUILDER module(Optional<String> module) {
            Objects.requireNonNull(module);
            this.module = module.map(java.lang.String.class::cast).orElse(this.module);
            return self();
        }

        /**
         * The element used to create this instance.
         * The type of the object depends on the environment we are in - it may be an {@code TypeElement} in annotation processing,
         * or a {@code ClassInfo} when using classpath scanning.
         *
         * @param originatingElement originating element
         * @return updated builder instance
         * @see #originatingElement()
         */
        BUILDER originatingElement(Optional<?> originatingElement) {
            Objects.requireNonNull(originatingElement);
            this.originatingElement = originatingElement.map(java.lang.Object.class::cast).orElse(this.originatingElement);
            return self();
        }

        /**
         * Generated implementation of the prototype, can be extended by descendant prototype implementations.
         */
        protected static class TypeInfoImpl implements TypeInfo {

            private final AccessModifier accessModifier;
            private final ElementKind kind;
            private final List<Annotation> annotations;
            private final List<Annotation> inheritedAnnotations;
            private final List<TypeInfo> interfaceTypeInfo;
            private final List<TypedElementInfo> elementInfo;
            private final List<TypedElementInfo> otherElementInfo;
            private final Map<TypeName, String> referencedModuleNames;
            private final Map<TypeName, List<Annotation>> referencedTypeNamesToAnnotations;
            private final Optional<TypeInfo> superTypeInfo;
            private final Optional<Object> originatingElement;
            private final Optional<String> description;
            private final Optional<String> module;
            private final Set<Modifier> elementModifiers;
            private final Set<String> modifiers;
            private final String typeKind;
            private final TypeName typeName;

            /**
             * Create an instance providing a builder.
             *
             * @param builder extending builder base of this prototype
             */
            protected TypeInfoImpl(TypeInfo.BuilderBase<?, ?> builder) {
                this.typeName = builder.typeName().get();
                this.description = builder.description();
                this.typeKind = builder.typeKind().get();
                this.kind = builder.kind().get();
                this.elementInfo = List.copyOf(builder.elementInfo());
                this.otherElementInfo = List.copyOf(builder.otherElementInfo());
                this.referencedTypeNamesToAnnotations =
                        Collections.unmodifiableMap(new LinkedHashMap<>(builder.referencedTypeNamesToAnnotations()));
                this.referencedModuleNames = Collections.unmodifiableMap(new LinkedHashMap<>(builder.referencedModuleNames()));
                this.superTypeInfo = builder.superTypeInfo();
                this.interfaceTypeInfo = List.copyOf(builder.interfaceTypeInfo());
                this.modifiers = Collections.unmodifiableSet(new LinkedHashSet<>(builder.modifiers()));
                this.elementModifiers = Collections.unmodifiableSet(new LinkedHashSet<>(builder.elementModifiers()));
                this.accessModifier = builder.accessModifier().get();
                this.module = builder.module();
                this.originatingElement = builder.originatingElement();
                this.annotations = List.copyOf(builder.annotations());
                this.inheritedAnnotations = List.copyOf(builder.inheritedAnnotations());
            }

            @Override
            public TypeName typeName() {
                return typeName;
            }

            @Override
            public Optional<String> description() {
                return description;
            }

            @Override
            public String typeKind() {
                return typeKind;
            }

            @Override
            public ElementKind kind() {
                return kind;
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
            public Set<Modifier> elementModifiers() {
                return elementModifiers;
            }

            @Override
            public AccessModifier accessModifier() {
                return accessModifier;
            }

            @Override
            public Optional<String> module() {
                return module;
            }

            @Override
            public Optional<Object> originatingElement() {
                return originatingElement;
            }

            @Override
            public List<Annotation> annotations() {
                return annotations;
            }

            @Override
            public List<Annotation> inheritedAnnotations() {
                return inheritedAnnotations;
            }

            @Override
            public String toString() {
                return "TypeInfo{"
                        + "typeName=" + typeName + ","
                        + "kind=" + kind + ","
                        + "elementInfo=" + elementInfo + ","
                        + "superTypeInfo=" + superTypeInfo + ","
                        + "elementModifiers=" + elementModifiers + ","
                        + "accessModifier=" + accessModifier + ","
                        + "module=" + module + ","
                        + "annotations=" + annotations + ","
                        + "inheritedAnnotations=" + inheritedAnnotations
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
                        && Objects.equals(kind, other.kind())
                        && Objects.equals(elementInfo, other.elementInfo())
                        && Objects.equals(superTypeInfo, other.superTypeInfo())
                        && Objects.equals(elementModifiers, other.elementModifiers())
                        && Objects.equals(accessModifier, other.accessModifier())
                        && Objects.equals(module, other.module())
                        && Objects.equals(annotations, other.annotations())
                        && Objects.equals(inheritedAnnotations, other.inheritedAnnotations());
            }

            @Override
            public int hashCode() {
                return Objects.hash(typeName,
                                    kind,
                                    elementInfo,
                                    superTypeInfo,
                                    elementModifiers,
                                    accessModifier,
                                    module,
                                    annotations,
                                    inheritedAnnotations);
            }

        }

    }

    /**
     * Fluent API builder for {@link TypeInfo}.
     */
    class Builder extends TypeInfo.BuilderBase<TypeInfo.Builder, TypeInfo>
            implements io.helidon.common.Builder<TypeInfo.Builder, TypeInfo> {

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
