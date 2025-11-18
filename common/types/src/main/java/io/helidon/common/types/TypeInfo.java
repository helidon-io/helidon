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
import java.util.function.Function;
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
     * Check if an annotation type has a specific meta annotation.
     *
     * @param annotation     annotation to check meta annotation for
     * @param metaAnnotation meta annotation type
     * @return whether the meta annotation is present on the annotation
     */
    default boolean hasMetaAnnotation(TypeName annotation, TypeName metaAnnotation) {
        return TypeInfoBlueprint.super.hasMetaAnnotation(annotation, metaAnnotation);
    }

    /**
     * Check if an annotation type has a specific meta annotation.
     *
     * @param annotation     annotation to check meta annotation for
     * @param metaAnnotation meta annotation type
     * @param inherited      whether to include meta annotations of meta annotations
     * @return whether the meta annotation is present on the annotation
     */
    default boolean hasMetaAnnotation(TypeName annotation, TypeName metaAnnotation, boolean inherited) {
        return TypeInfoBlueprint.super.hasMetaAnnotation(annotation, metaAnnotation, inherited);
    }

    /**
     * Find a meta annotation.
     *
     * @param annotation     annotation to check meta annotation for
     * @param metaAnnotation meta annotation type
     * @return meta annotation, or empty if not defined
     */
    default Optional<Annotation> metaAnnotation(TypeName annotation, TypeName metaAnnotation) {
        return TypeInfoBlueprint.super.metaAnnotation(annotation, metaAnnotation);
    }

    /**
     * Find a meta annotation.
     *
     * @param annotation     annotation to check meta annotation for
     * @param metaAnnotation meta annotation type
     * @param inherited      whether to include meta annotations of meta annotations
     * @return meta annotation, or empty if not defined
     */
    default Optional<Annotation> metaAnnotation(TypeName annotation, TypeName metaAnnotation, boolean inherited) {
        return TypeInfoBlueprint.super.metaAnnotation(annotation, metaAnnotation, inherited);
    }

    /**
     * The element used to create this instance, or {@link io.helidon.common.types.TypeInfo#typeName()} if none provided.
     * The type of the object depends on the environment we are in - it may be an {@code TypeElement} in annotation processing,
     * or a {@code ClassInfo} when using classpath scanning.
     *
     * @return originating element, or the type of this type info
     */
    default Object originatingElementValue() {
        return TypeInfoBlueprint.super.originatingElementValue();
    }

    /**
     * Checks if the current type implements, or extends the provided type.
     * This method analyzes the whole dependency tree of the current type.
     *
     * @param typeName type of interface to check
     * @return the super type info, or interface type info matching the provided type, with appropriate generic declarations
     */
    default Optional<TypeInfo> findInHierarchy(TypeName typeName) {
        return TypeInfoBlueprint.super.findInHierarchy(typeName);
    }

    /**
     * Uses {@link io.helidon.common.types.TypeInfo#referencedModuleNames()} to determine if the module name is known for the
     * given type.
     *
     * @param typeName the type name to lookup
     * @return the module name if it is known
     */
    default Optional<String> moduleNameOf(TypeName typeName) {
        return TypeInfoBlueprint.super.moduleNameOf(typeName);
    }

    /**
     * The type name.
     * This type name represents the type usage of this type
     * (obtained from {@link TypeInfo#superTypeInfo()} or {@link TypeInfo#interfaceTypeInfo()}).
     * In case this is a type info created from {@link io.helidon.common.types.TypeName}, this will be the type name returned.
     *
     * @return the type name
     */
    @Override
    TypeName typeName();

    /**
     * The raw type name. This is a unique identification of a type, containing ONLY:
     * <ul>
     *  <li>{@link TypeName#packageName()}</li>
     *  <li>{@link io.helidon.common.types.TypeName#className()}</li>
     *  <li>if relevant: {@link io.helidon.common.types.TypeName#enclosingNames()}</li>
     * </ul>
     *
     * @return raw type of this type info
     */
    @Override
    TypeName rawType();

    /**
     * The declared type name, including type parameters.
     *
     * @return type name with declared type parameters
     */
    @Override
    TypeName declaredType();

    /**
     * Description, such as javadoc, if available.
     *
     * @return description of this element
     */
    @Override
    Optional<String> description();

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
     * @return the type element kind.
     * @see io.helidon.common.types.TypeValues#KIND_CLASS and other constants on this class prefixed with {@code TYPE}
     * @deprecated This option is deprecated, use {@link #kind} instead
     */
    @Deprecated(since = "4.1.0", forRemoval = true)
    @Override
    String typeKind();

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
     * @return element kind of this type
     */
    @Override
    ElementKind kind();

    /**
     * The elements that make up the type that are relevant for processing.
     *
     * @return the elements that make up the type that are relevant for processing
     */
    @Override
    List<TypedElementInfo> elementInfo();

    /**
     * The elements that make up this type that are considered "other", or being skipped because they are irrelevant to
     * processing.
     *
     * @return the elements that still make up the type, but are otherwise deemed irrelevant for processing
     */
    @Override
    List<TypedElementInfo> otherElementInfo();

    /**
     * Any Map, List, Set, or method that has {@link io.helidon.common.types.TypeName#typeArguments()} will be analyzed and any
     * type arguments will have
     * its annotations added here. Note that this only applies to non-built-in types.
     *
     * @return all referenced types
     */
    @Override
    Map<TypeName, List<Annotation>> referencedTypeNamesToAnnotations();

    /**
     * Populated if the (external) module name containing the type is known.
     *
     * @return type names to its associated defining module name
     */
    @Override
    Map<TypeName, String> referencedModuleNames();

    /**
     * The parent/super class for this type info.
     *
     * @return the super type
     */
    @Override
    Optional<TypeInfo> superTypeInfo();

    /**
     * The interface classes for this type info.
     *
     * @return the interface type info
     */
    @Override
    List<TypeInfo> interfaceTypeInfo();

    /**
     * Element modifiers.
     *
     * @return element modifiers
     * @see io.helidon.common.types.TypeValues#MODIFIER_PUBLIC and other constants prefixed with {@code MODIFIER}
     * @deprecated This option is deprecated, use {@link #elementModifiers} instead
     */
    @Deprecated(since = "4.1.0", forRemoval = true)
    @Override
    Set<String> modifiers();

    /**
     * Type modifiers.
     *
     * @return set of modifiers that are present on the type (and that we understand)
     * @see io.helidon.common.types.Modifier
     * @see #accessModifier()
     */
    @Override
    Set<Modifier> elementModifiers();

    /**
     * Access modifier.
     *
     * @return access modifier
     */
    @Override
    AccessModifier accessModifier();

    /**
     * Module of this type, if available.
     *
     * @return module name
     */
    @Override
    Optional<String> module();

    /**
     * The element used to create this instance.
     * The type of the object depends on the environment we are in - it may be an {@code TypeElement} in annotation processing,
     * or a {@code ClassInfo} when using classpath scanning.
     *
     * @return originating element
     */
    @Override
    Optional<Object> originatingElement();

    /**
     * Fluent API builder base for {@link TypeInfo}.
     *
     * @param <BUILDER>   type of the builder extending this abstract builder
     * @param <PROTOTYPE> type of the prototype interface that would be built by {@link #buildPrototype()}
     */
    abstract class BuilderBase<BUILDER extends TypeInfo.BuilderBase<BUILDER, PROTOTYPE>, PROTOTYPE extends TypeInfo>
            implements Prototype.Builder<BUILDER, PROTOTYPE> {

        private final List<Annotation> annotations = new ArrayList<>();
        private final List<Annotation> inheritedAnnotations = new ArrayList<>();
        private final List<TypeInfo> interfaceTypeInfo = new ArrayList<>();
        private final List<TypedElementInfo> elementInfo = new ArrayList<>();
        private final List<TypedElementInfo> otherElementInfo = new ArrayList<>();
        private final Set<Modifier> elementModifiers = new LinkedHashSet<>();
        private final Set<String> modifiers = new LinkedHashSet<>();
        private AccessModifier accessModifier;
        private boolean isAnnotationsMutated;
        private boolean isElementInfoMutated;
        private boolean isElementModifiersMutated;
        private boolean isInheritedAnnotationsMutated;
        private boolean isInterfaceTypeInfoMutated;
        private boolean isModifiersMutated;
        private boolean isOtherElementInfoMutated;
        private boolean isReferencedModuleNamesMutated;
        private boolean isReferencedTypeNamesToAnnotationsMutated;
        private ElementKind kind;
        private Map<TypeName, String> referencedModuleNames = new LinkedHashMap<>();
        private Map<TypeName, List<Annotation>> referencedTypeNamesToAnnotations = new LinkedHashMap<>();
        private Object originatingElement;
        private String description;
        private String module;
        private String typeKind;
        private TypeInfo superTypeInfo;
        private TypeName declaredType;
        private TypeName rawType;
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
            rawType(prototype.rawType());
            declaredType(prototype.declaredType());
            description(prototype.description());
            typeKind(prototype.typeKind());
            kind(prototype.kind());
            if (!this.isElementInfoMutated) {
                this.elementInfo.clear();
            }
            addElementInfo(prototype.elementInfo());
            if (!this.isOtherElementInfoMutated) {
                this.otherElementInfo.clear();
            }
            addOtherElementInfo(prototype.otherElementInfo());
            if (!this.isReferencedTypeNamesToAnnotationsMutated) {
                this.referencedTypeNamesToAnnotations.clear();
            }
            addReferencedTypeNamesToAnnotations(prototype.referencedTypeNamesToAnnotations());
            if (!this.isReferencedModuleNamesMutated) {
                this.referencedModuleNames.clear();
            }
            addReferencedModuleNames(prototype.referencedModuleNames());
            superTypeInfo(prototype.superTypeInfo());
            if (!this.isInterfaceTypeInfoMutated) {
                this.interfaceTypeInfo.clear();
            }
            addInterfaceTypeInfo(prototype.interfaceTypeInfo());
            if (!this.isModifiersMutated) {
                this.modifiers.clear();
            }
            addModifiers(prototype.modifiers());
            if (!this.isElementModifiersMutated) {
                this.elementModifiers.clear();
            }
            addElementModifiers(prototype.elementModifiers());
            accessModifier(prototype.accessModifier());
            module(prototype.module());
            originatingElement(prototype.originatingElement());
            if (!this.isAnnotationsMutated) {
                this.annotations.clear();
            }
            addAnnotations(prototype.annotations());
            if (!this.isInheritedAnnotationsMutated) {
                this.inheritedAnnotations.clear();
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
            builder.rawType().ifPresent(this::rawType);
            builder.declaredType().ifPresent(this::declaredType);
            builder.description().ifPresent(this::description);
            builder.typeKind().ifPresent(this::typeKind);
            builder.kind().ifPresent(this::kind);
            if (this.isElementInfoMutated) {
                if (builder.isElementInfoMutated) {
                    addElementInfo(builder.elementInfo());
                }
            } else {
                elementInfo(builder.elementInfo());
            }
            if (this.isOtherElementInfoMutated) {
                if (builder.isOtherElementInfoMutated) {
                    addOtherElementInfo(builder.otherElementInfo());
                }
            } else {
                otherElementInfo(builder.otherElementInfo());
            }
            if (this.isReferencedTypeNamesToAnnotationsMutated) {
                if (builder.isReferencedTypeNamesToAnnotationsMutated) {
                    addReferencedTypeNamesToAnnotations(builder.referencedTypeNamesToAnnotations());
                }
            } else {
                referencedTypeNamesToAnnotations(builder.referencedTypeNamesToAnnotations());
            }
            if (this.isReferencedModuleNamesMutated) {
                if (builder.isReferencedModuleNamesMutated) {
                    addReferencedModuleNames(builder.referencedModuleNames());
                }
            } else {
                referencedModuleNames(builder.referencedModuleNames());
            }
            builder.superTypeInfo().ifPresent(this::superTypeInfo);
            if (this.isInterfaceTypeInfoMutated) {
                if (builder.isInterfaceTypeInfoMutated) {
                    addInterfaceTypeInfo(builder.interfaceTypeInfo());
                }
            } else {
                interfaceTypeInfo(builder.interfaceTypeInfo());
            }
            if (this.isModifiersMutated) {
                if (builder.isModifiersMutated) {
                    addModifiers(builder.modifiers());
                }
            } else {
                modifiers(builder.modifiers());
            }
            if (this.isElementModifiersMutated) {
                if (builder.isElementModifiersMutated) {
                    addElementModifiers(builder.elementModifiers());
                }
            } else {
                elementModifiers(builder.elementModifiers());
            }
            builder.accessModifier().ifPresent(this::accessModifier);
            builder.module().ifPresent(this::module);
            builder.originatingElement().ifPresent(this::originatingElement);
            if (this.isAnnotationsMutated) {
                if (builder.isAnnotationsMutated) {
                    addAnnotations(builder.annotations());
                }
            } else {
                annotations(builder.annotations());
            }
            if (this.isInheritedAnnotationsMutated) {
                if (builder.isInheritedAnnotationsMutated) {
                    addInheritedAnnotations(builder.inheritedAnnotations());
                }
            } else {
                inheritedAnnotations(builder.inheritedAnnotations());
            }
            return self();
        }

        /**
         * The type name.
         * This type name represents the type usage of this type
         * (obtained from {@link TypeInfo#superTypeInfo()} or {@link TypeInfo#interfaceTypeInfo()}).
         * In case this is a type info created from {@link io.helidon.common.types.TypeName}, this will be the type name returned.
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
         * This type name represents the type usage of this type
         * (obtained from {@link TypeInfo#superTypeInfo()} or {@link TypeInfo#interfaceTypeInfo()}).
         * In case this is a type info created from {@link io.helidon.common.types.TypeName}, this will be the type name returned.
         *
         * @param consumer consumer of builder of the type name
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
         * This type name represents the type usage of this type
         * (obtained from {@link TypeInfo#superTypeInfo()} or {@link TypeInfo#interfaceTypeInfo()}).
         * In case this is a type info created from {@link io.helidon.common.types.TypeName}, this will be the type name returned.
         *
         * @param supplier supplier of the type name
         * @return updated builder instance
         * @see #typeName()
         */
        public BUILDER typeName(Supplier<? extends TypeName> supplier) {
            Objects.requireNonNull(supplier);
            this.typeName(supplier.get());
            return self();
        }

        /**
         * The raw type name. This is a unique identification of a type, containing ONLY:
         * <ul>
         *  <li>{@link TypeName#packageName()}</li>
         *  <li>{@link io.helidon.common.types.TypeName#className()}</li>
         *  <li>if relevant: {@link io.helidon.common.types.TypeName#enclosingNames()}</li>
         * </ul>
         *
         * @param rawType raw type of this type info
         * @return updated builder instance
         * @see #rawType()
         */
        public BUILDER rawType(TypeName rawType) {
            Objects.requireNonNull(rawType);
            this.rawType = rawType;
            return self();
        }

        /**
         * The raw type name. This is a unique identification of a type, containing ONLY:
         * <ul>
         *  <li>{@link TypeName#packageName()}</li>
         *  <li>{@link io.helidon.common.types.TypeName#className()}</li>
         *  <li>if relevant: {@link io.helidon.common.types.TypeName#enclosingNames()}</li>
         * </ul>
         *
         * @param consumer consumer of builder of raw type of this type info
         * @return updated builder instance
         * @see #rawType()
         */
        public BUILDER rawType(Consumer<TypeName.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = TypeName.builder();
            consumer.accept(builder);
            this.rawType(builder.build());
            return self();
        }

        /**
         * The raw type name. This is a unique identification of a type, containing ONLY:
         * <ul>
         *  <li>{@link TypeName#packageName()}</li>
         *  <li>{@link io.helidon.common.types.TypeName#className()}</li>
         *  <li>if relevant: {@link io.helidon.common.types.TypeName#enclosingNames()}</li>
         * </ul>
         *
         * @param supplier supplier of raw type of this type info
         * @return updated builder instance
         * @see #rawType()
         */
        public BUILDER rawType(Supplier<? extends TypeName> supplier) {
            Objects.requireNonNull(supplier);
            this.rawType(supplier.get());
            return self();
        }

        /**
         * The declared type name, including type parameters.
         *
         * @param declaredType type name with declared type parameters
         * @return updated builder instance
         * @see #declaredType()
         */
        public BUILDER declaredType(TypeName declaredType) {
            Objects.requireNonNull(declaredType);
            this.declaredType = declaredType;
            return self();
        }

        /**
         * The declared type name, including type parameters.
         *
         * @param consumer consumer of builder of type name with declared type parameters
         * @return updated builder instance
         * @see #declaredType()
         */
        public BUILDER declaredType(Consumer<TypeName.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = TypeName.builder();
            consumer.accept(builder);
            this.declaredType(builder.build());
            return self();
        }

        /**
         * The declared type name, including type parameters.
         *
         * @param supplier supplier of type name with declared type parameters
         * @return updated builder instance
         * @see #declaredType()
         */
        public BUILDER declaredType(Supplier<? extends TypeName> supplier) {
            Objects.requireNonNull(supplier);
            this.declaredType(supplier.get());
            return self();
        }

        /**
         * Clear existing value of description.
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
         * @see io.helidon.common.types.TypeValues#KIND_CLASS and other constants on this class prefixed with {@code TYPE}
         * @see #typeKind()
         * @deprecated This option is deprecated, use {@link #kind} instead
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
         * Clear all elementInfo.
         *
         * @return updated builder instance
         * @see #elementInfo()
         */
        public BUILDER clearElementInfo() {
            this.isElementInfoMutated = true;
            this.elementInfo.clear();
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
            this.isElementInfoMutated = true;
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
            this.isElementInfoMutated = true;
            this.elementInfo.addAll(elementInfo);
            return self();
        }

        /**
         * The elements that make up the type that are relevant for processing.
         *
         * @param elementInfo add single the elements that make up the type that are relevant for processing
         * @return updated builder instance
         * @see #elementInfo()
         */
        public BUILDER addElementInfo(TypedElementInfo elementInfo) {
            Objects.requireNonNull(elementInfo);
            this.elementInfo.add(elementInfo);
            this.isElementInfoMutated = true;
            return self();
        }

        /**
         * The elements that make up the type that are relevant for processing.
         *
         * @param consumer consumer of builder for the elements that make up the type that are relevant for processing
         * @return updated builder instance
         * @see #elementInfo()
         */
        public BUILDER addElementInfo(Consumer<TypedElementInfo.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = TypedElementInfo.builder();
            consumer.accept(builder);
            this.addElementInfo(builder.build());
            return self();
        }

        /**
         * Clear all otherElementInfo.
         *
         * @return updated builder instance
         * @see #otherElementInfo()
         */
        public BUILDER clearOtherElementInfo() {
            this.isOtherElementInfoMutated = true;
            this.otherElementInfo.clear();
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
            this.isOtherElementInfoMutated = true;
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
            this.isOtherElementInfoMutated = true;
            this.otherElementInfo.addAll(otherElementInfo);
            return self();
        }

        /**
         * The elements that make up this type that are considered "other", or being skipped because they are irrelevant to
         * processing.
         *
         * @param otherElementInfo add single the elements that still make up the type, but are otherwise deemed irrelevant for
         *                         processing
         * @return updated builder instance
         * @see #otherElementInfo()
         */
        public BUILDER addOtherElementInfo(TypedElementInfo otherElementInfo) {
            Objects.requireNonNull(otherElementInfo);
            this.otherElementInfo.add(otherElementInfo);
            this.isOtherElementInfoMutated = true;
            return self();
        }

        /**
         * The elements that make up this type that are considered "other", or being skipped because they are irrelevant to
         * processing.
         *
         * @param consumer consumer of builder for the elements that still make up the type, but are otherwise deemed irrelevant
         *                 for processing
         * @return updated builder instance
         * @see #otherElementInfo()
         */
        public BUILDER addOtherElementInfo(Consumer<TypedElementInfo.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = TypedElementInfo.builder();
            consumer.accept(builder);
            this.addOtherElementInfo(builder.build());
            return self();
        }

        /**
         * Any Map, List, Set, or method that has {@link io.helidon.common.types.TypeName#typeArguments()} will be analyzed and
         * any
         * type arguments will have
         * its annotations added here. Note that this only applies to non-built-in types.
         * This method replaces all values with the new ones.
         *
         * @param referencedTypeNamesToAnnotations all referenced types
         * @return updated builder instance
         * @see #referencedTypeNamesToAnnotations()
         */
        public BUILDER referencedTypeNamesToAnnotations(Map<? extends TypeName,
                List<Annotation>> referencedTypeNamesToAnnotations) {
            Objects.requireNonNull(referencedTypeNamesToAnnotations);
            this.referencedTypeNamesToAnnotations.clear();
            this.referencedTypeNamesToAnnotations.putAll(referencedTypeNamesToAnnotations);
            this.isReferencedTypeNamesToAnnotationsMutated = true;
            return self();
        }

        /**
         * Any Map, List, Set, or method that has {@link io.helidon.common.types.TypeName#typeArguments()} will be analyzed and
         * any
         * type arguments will have
         * its annotations added here. Note that this only applies to non-built-in types.
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
            this.isReferencedTypeNamesToAnnotationsMutated = true;
            return self();
        }

        /**
         * Any Map, List, Set, or method that has {@link io.helidon.common.types.TypeName#typeArguments()} will be analyzed and
         * any
         * type arguments will have
         * its annotations added here. Note that this only applies to non-built-in types.
         * This method adds a new value to the map value, or creates a new value.
         *
         * @param key                             key to add value for
         * @param referencedTypeNamesToAnnotation value to add to the map values
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
            this.isReferencedTypeNamesToAnnotationsMutated = true;
            return self();
        }

        /**
         * Any Map, List, Set, or method that has {@link io.helidon.common.types.TypeName#typeArguments()} will be analyzed and
         * any
         * type arguments will have
         * its annotations added here. Note that this only applies to non-built-in types.
         * This method adds new values to the map values, or creates a new mapping.
         *
         * @param key                              key to add value for
         * @param referencedTypeNamesToAnnotations values to add to the map values
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
            this.isReferencedTypeNamesToAnnotationsMutated = true;
            return self();
        }

        /**
         * Any Map, List, Set, or method that has {@link io.helidon.common.types.TypeName#typeArguments()} will be analyzed and
         * any
         * type arguments will have
         * its annotations added here. Note that this only applies to non-built-in types.
         * This method adds a new value to the map, or replaces it if the key already exists.
         *
         * @param key                             key to add or replace
         * @param referencedTypeNamesToAnnotation new value for the key
         * @return updated builder instance
         * @see #referencedTypeNamesToAnnotations()
         */
        public BUILDER putReferencedTypeNamesToAnnotation(TypeName key, List<Annotation> referencedTypeNamesToAnnotation) {
            Objects.requireNonNull(key);
            Objects.requireNonNull(referencedTypeNamesToAnnotation);
            this.referencedTypeNamesToAnnotations.put(key, List.copyOf(referencedTypeNamesToAnnotation));
            this.isReferencedTypeNamesToAnnotationsMutated = true;
            return self();
        }

        /**
         * Populated if the (external) module name containing the type is known.
         * This method replaces all values with the new ones.
         *
         * @param referencedModuleNames type names to its associated defining module name
         * @return updated builder instance
         * @see #referencedModuleNames()
         */
        public BUILDER referencedModuleNames(Map<? extends TypeName, String> referencedModuleNames) {
            Objects.requireNonNull(referencedModuleNames);
            this.referencedModuleNames.clear();
            this.referencedModuleNames.putAll(referencedModuleNames);
            this.isReferencedModuleNamesMutated = true;
            return self();
        }

        /**
         * Populated if the (external) module name containing the type is known.
         * This method keeps existing values, then puts all new values into the map.
         *
         * @param referencedModuleNames type names to its associated defining module name
         * @return updated builder instance
         * @see #referencedModuleNames()
         */
        public BUILDER addReferencedModuleNames(Map<? extends TypeName, String> referencedModuleNames) {
            Objects.requireNonNull(referencedModuleNames);
            this.referencedModuleNames.putAll(referencedModuleNames);
            this.isReferencedModuleNamesMutated = true;
            return self();
        }

        /**
         * Populated if the (external) module name containing the type is known.
         * This method adds a new value to the map, or replaces it if the key already exists.
         *
         * @param key                  key to add or replace
         * @param referencedModuleName new value for the key
         * @return updated builder instance
         * @see #referencedModuleNames()
         */
        public BUILDER putReferencedModuleName(TypeName key, String referencedModuleName) {
            Objects.requireNonNull(key);
            Objects.requireNonNull(referencedModuleName);
            this.referencedModuleNames.put(key, referencedModuleName);
            this.isReferencedModuleNamesMutated = true;
            return self();
        }

        /**
         * Clear existing value of superTypeInfo.
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
         * @param consumer consumer of builder of the super type
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
         * The parent/super class for this type info.
         *
         * @param supplier supplier of the super type
         * @return updated builder instance
         * @see #superTypeInfo()
         */
        public BUILDER superTypeInfo(Supplier<? extends TypeInfo> supplier) {
            Objects.requireNonNull(supplier);
            this.superTypeInfo(supplier.get());
            return self();
        }

        /**
         * Clear all interfaceTypeInfo.
         *
         * @return updated builder instance
         * @see #interfaceTypeInfo()
         */
        public BUILDER clearInterfaceTypeInfo() {
            this.isInterfaceTypeInfoMutated = true;
            this.interfaceTypeInfo.clear();
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
            this.isInterfaceTypeInfoMutated = true;
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
            this.isInterfaceTypeInfoMutated = true;
            this.interfaceTypeInfo.addAll(interfaceTypeInfo);
            return self();
        }

        /**
         * The interface classes for this type info.
         *
         * @param interfaceTypeInfo add single the interface type info
         * @return updated builder instance
         * @see #interfaceTypeInfo()
         */
        public BUILDER addInterfaceTypeInfo(TypeInfo interfaceTypeInfo) {
            Objects.requireNonNull(interfaceTypeInfo);
            this.interfaceTypeInfo.add(interfaceTypeInfo);
            this.isInterfaceTypeInfoMutated = true;
            return self();
        }

        /**
         * The interface classes for this type info.
         *
         * @param consumer consumer of builder for the interface type info
         * @return updated builder instance
         * @see #interfaceTypeInfo()
         */
        public BUILDER addInterfaceTypeInfo(Consumer<TypeInfo.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = TypeInfo.builder();
            consumer.accept(builder);
            this.addInterfaceTypeInfo(builder.build());
            return self();
        }

        /**
         * Clear all modifiers.
         *
         * @return updated builder instance
         * @see io.helidon.common.types.TypeValues#MODIFIER_PUBLIC and other constants prefixed with {@code MODIFIER}
         * @see #modifiers()
         * @deprecated This option is deprecated, use {@link #elementModifiers} instead
         */
        @Deprecated(since = "4.1.0", forRemoval = true)
        public BUILDER clearModifiers() {
            this.isModifiersMutated = true;
            this.modifiers.clear();
            return self();
        }

        /**
         * Element modifiers.
         *
         * @param modifiers element modifiers
         * @return updated builder instance
         * @see io.helidon.common.types.TypeValues#MODIFIER_PUBLIC and other constants prefixed with {@code MODIFIER}
         * @see #modifiers()
         * @deprecated This option is deprecated, use {@link #elementModifiers} instead
         */
        @Deprecated(since = "4.1.0", forRemoval = true)
        public BUILDER modifiers(Set<String> modifiers) {
            Objects.requireNonNull(modifiers);
            this.isModifiersMutated = true;
            this.modifiers.clear();
            this.modifiers.addAll(modifiers);
            return self();
        }

        /**
         * Element modifiers.
         *
         * @param modifiers element modifiers
         * @return updated builder instance
         * @see io.helidon.common.types.TypeValues#MODIFIER_PUBLIC and other constants prefixed with {@code MODIFIER}
         * @see #modifiers()
         * @deprecated This option is deprecated, use {@link #elementModifiers} instead
         */
        @Deprecated(since = "4.1.0", forRemoval = true)
        public BUILDER addModifiers(Set<String> modifiers) {
            Objects.requireNonNull(modifiers);
            this.isModifiersMutated = true;
            this.modifiers.addAll(modifiers);
            return self();
        }

        /**
         * Element modifiers.
         *
         * @param modifier add single element modifiers
         * @return updated builder instance
         * @see io.helidon.common.types.TypeValues#MODIFIER_PUBLIC and other constants prefixed with {@code MODIFIER}
         * @see #modifiers()
         * @deprecated This option is deprecated, use {@link #elementModifiers} instead
         */
        @Deprecated(since = "4.1.0", forRemoval = true)
        public BUILDER addModifier(String modifier) {
            Objects.requireNonNull(modifier);
            this.modifiers.add(modifier);
            this.isModifiersMutated = true;
            return self();
        }

        /**
         * Clear all elementModifiers.
         *
         * @return updated builder instance
         * @see io.helidon.common.types.Modifier
         * @see #accessModifier()
         * @see #elementModifiers()
         */
        public BUILDER clearElementModifiers() {
            this.isElementModifiersMutated = true;
            this.elementModifiers.clear();
            return self();
        }

        /**
         * Type modifiers.
         *
         * @param elementModifiers set of modifiers that are present on the type (and that we understand)
         * @return updated builder instance
         * @see io.helidon.common.types.Modifier
         * @see #accessModifier()
         * @see #elementModifiers()
         */
        public BUILDER elementModifiers(Set<? extends Modifier> elementModifiers) {
            Objects.requireNonNull(elementModifiers);
            this.isElementModifiersMutated = true;
            this.elementModifiers.clear();
            this.elementModifiers.addAll(elementModifiers);
            return self();
        }

        /**
         * Type modifiers.
         *
         * @param elementModifiers set of modifiers that are present on the type (and that we understand)
         * @return updated builder instance
         * @see io.helidon.common.types.Modifier
         * @see #accessModifier()
         * @see #elementModifiers()
         */
        public BUILDER addElementModifiers(Set<? extends Modifier> elementModifiers) {
            Objects.requireNonNull(elementModifiers);
            this.isElementModifiersMutated = true;
            this.elementModifiers.addAll(elementModifiers);
            return self();
        }

        /**
         * Type modifiers.
         *
         * @param elementModifier add single set of modifiers that are present on the type (and that we understand)
         * @return updated builder instance
         * @see io.helidon.common.types.Modifier
         * @see #accessModifier()
         * @see #elementModifiers()
         */
        public BUILDER addElementModifier(Modifier elementModifier) {
            Objects.requireNonNull(elementModifier);
            this.elementModifiers.add(elementModifier);
            this.isElementModifiersMutated = true;
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
         * Clear existing value of module.
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
         * Clear existing value of originatingElement.
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
         * The type of the object depends on the environment we are in - it may be an {@code TypeElement} in annotation
         * processing,
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
         * Clear all annotations.
         *
         * @return updated builder instance
         * @see #annotations()
         */
        public BUILDER clearAnnotations() {
            this.isAnnotationsMutated = true;
            this.annotations.clear();
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
            this.isAnnotationsMutated = true;
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
            this.isAnnotationsMutated = true;
            this.annotations.addAll(annotations);
            return self();
        }

        /**
         * List of declared and known annotations for this element.
         * Note that "known" implies that the annotation is visible, which depends
         * upon the context in which it was build (such as the {@link java.lang.annotation.Retention of the annotation}).
         *
         * @param annotation add single the list of annotations declared on this element
         * @return updated builder instance
         * @see #annotations()
         */
        public BUILDER addAnnotation(Annotation annotation) {
            Objects.requireNonNull(annotation);
            this.annotations.add(annotation);
            this.isAnnotationsMutated = true;
            return self();
        }

        /**
         * List of declared and known annotations for this element.
         * Note that "known" implies that the annotation is visible, which depends
         * upon the context in which it was build (such as the {@link java.lang.annotation.Retention of the annotation}).
         *
         * @param consumer consumer of builder for the list of annotations declared on this element
         * @return updated builder instance
         * @see #annotations()
         */
        public BUILDER addAnnotation(Consumer<Annotation.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = Annotation.builder();
            consumer.accept(builder);
            this.addAnnotation(builder.build());
            return self();
        }

        /**
         * Clear all inheritedAnnotations.
         *
         * @return updated builder instance
         * @see #inheritedAnnotations()
         */
        public BUILDER clearInheritedAnnotations() {
            this.isInheritedAnnotationsMutated = true;
            this.inheritedAnnotations.clear();
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
            this.isInheritedAnnotationsMutated = true;
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
            this.isInheritedAnnotationsMutated = true;
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
         * @param inheritedAnnotation add single list of all meta annotations of this element
         * @return updated builder instance
         * @see #inheritedAnnotations()
         */
        public BUILDER addInheritedAnnotation(Annotation inheritedAnnotation) {
            Objects.requireNonNull(inheritedAnnotation);
            this.inheritedAnnotations.add(inheritedAnnotation);
            this.isInheritedAnnotationsMutated = true;
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
         * @param consumer consumer of builder for list of all meta annotations of this element
         * @return updated builder instance
         * @see #inheritedAnnotations()
         */
        public BUILDER addInheritedAnnotation(Consumer<Annotation.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = Annotation.builder();
            consumer.accept(builder);
            this.addInheritedAnnotation(builder.build());
            return self();
        }

        /**
         * The type name.
         * This type name represents the type usage of this type
         * (obtained from {@link TypeInfo#superTypeInfo()} or {@link TypeInfo#interfaceTypeInfo()}).
         * In case this is a type info created from {@link io.helidon.common.types.TypeName}, this will be the type name returned.
         *
         * @return the type name
         */
        public Optional<TypeName> typeName() {
            return Optional.ofNullable(typeName);
        }

        /**
         * The raw type name. This is a unique identification of a type, containing ONLY:
         * <ul>
         *  <li>{@link TypeName#packageName()}</li>
         *  <li>{@link io.helidon.common.types.TypeName#className()}</li>
         *  <li>if relevant: {@link io.helidon.common.types.TypeName#enclosingNames()}</li>
         * </ul>
         *
         * @return raw type of this type info
         */
        public Optional<TypeName> rawType() {
            return Optional.ofNullable(rawType);
        }

        /**
         * The declared type name, including type parameters.
         *
         * @return type name with declared type parameters
         */
        public Optional<TypeName> declaredType() {
            return Optional.ofNullable(declaredType);
        }

        /**
         * Description, such as javadoc, if available.
         *
         * @return description of this element
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
         * @return the type element kind.
         * @see io.helidon.common.types.TypeValues#KIND_CLASS and other constants on this class prefixed with {@code TYPE}
         * @deprecated This option is deprecated, use {@link #kind} instead
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
         * @return element kind of this type
         */
        public Optional<ElementKind> kind() {
            return Optional.ofNullable(kind);
        }

        /**
         * The elements that make up the type that are relevant for processing.
         *
         * @return the elements that make up the type that are relevant for processing
         */
        public List<TypedElementInfo> elementInfo() {
            return elementInfo;
        }

        /**
         * The elements that make up this type that are considered "other", or being skipped because they are irrelevant to
         * processing.
         *
         * @return the elements that still make up the type, but are otherwise deemed irrelevant for processing
         */
        public List<TypedElementInfo> otherElementInfo() {
            return otherElementInfo;
        }

        /**
         * Any Map, List, Set, or method that has {@link io.helidon.common.types.TypeName#typeArguments()} will be analyzed and
         * any
         * type arguments will have
         * its annotations added here. Note that this only applies to non-built-in types.
         *
         * @return all referenced types
         */
        public Map<TypeName, List<Annotation>> referencedTypeNamesToAnnotations() {
            return referencedTypeNamesToAnnotations;
        }

        /**
         * Populated if the (external) module name containing the type is known.
         *
         * @return type names to its associated defining module name
         */
        public Map<TypeName, String> referencedModuleNames() {
            return referencedModuleNames;
        }

        /**
         * The parent/super class for this type info.
         *
         * @return the super type
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
         * @return element modifiers
         * @see io.helidon.common.types.TypeValues#MODIFIER_PUBLIC and other constants prefixed with {@code MODIFIER}
         * @deprecated This option is deprecated, use {@link #elementModifiers} instead
         */
        @Deprecated(since = "4.1.0", forRemoval = true)
        public Set<String> modifiers() {
            return modifiers;
        }

        /**
         * Type modifiers.
         *
         * @return set of modifiers that are present on the type (and that we understand)
         * @see io.helidon.common.types.Modifier
         * @see #accessModifier()
         */
        public Set<Modifier> elementModifiers() {
            return elementModifiers;
        }

        /**
         * Access modifier.
         *
         * @return access modifier
         */
        public Optional<AccessModifier> accessModifier() {
            return Optional.ofNullable(accessModifier);
        }

        /**
         * Module of this type, if available.
         *
         * @return module name
         */
        public Optional<String> module() {
            return Optional.ofNullable(module);
        }

        /**
         * The element used to create this instance.
         * The type of the object depends on the environment we are in - it may be an {@code TypeElement} in annotation
         * processing,
         * or a {@code ClassInfo} when using classpath scanning.
         *
         * @return originating element
         */
        public Optional<Object> originatingElement() {
            return Optional.ofNullable(originatingElement);
        }

        /**
         * List of declared and known annotations for this element.
         * Note that "known" implies that the annotation is visible, which depends
         * upon the context in which it was build (such as the {@link java.lang.annotation.Retention of the annotation}).
         *
         * @return the list of annotations declared on this element
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
         * @return list of all meta annotations of this element
         */
        public List<Annotation> inheritedAnnotations() {
            return inheritedAnnotations;
        }

        @Override
        public String toString() {
            return "TypeInfoBuilder{"
                    + "typeName=" + typeName + ","
                    + "rawType=" + rawType + ","
                    + "declaredType=" + declaredType + ","
                    + "kind=" + kind + ","
                    + "elementInfo=" + elementInfo + ","
                    + "superTypeInfo=" + superTypeInfo + ","
                    + "elementModifiers=" + elementModifiers + ","
                    + "accessModifier=" + accessModifier + ","
                    + "module=" + module
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
                collector.fatal(getClass(), "Property \"typeName\" must not be null, but not set");
            }
            if (rawType == null) {
                collector.fatal(getClass(), "Property \"rawType\" must not be null, but not set");
            }
            if (declaredType == null) {
                collector.fatal(getClass(), "Property \"declaredType\" must not be null, but not set");
            }
            if (typeKind == null) {
                collector.fatal(getClass(), "Property \"typeKind\" must not be null, but not set");
            }
            if (kind == null) {
                collector.fatal(getClass(), "Property \"kind\" must not be null, but not set");
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
            this.description = description.orElse(this.description);
            return self();
        }

        /**
         * The parent/super class for this type info.
         *
         * @param superTypeInfo the super type
         * @return updated builder instance
         * @see #superTypeInfo()
         */
        @SuppressWarnings("unchecked")
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
            this.module = module.orElse(this.module);
            return self();
        }

        /**
         * The element used to create this instance.
         * The type of the object depends on the environment we are in - it may be an {@code TypeElement} in annotation
         * processing,
         * or a {@code ClassInfo} when using classpath scanning.
         *
         * @param originatingElement originating element
         * @return updated builder instance
         * @see #originatingElement()
         */
        @SuppressWarnings("unchecked")
        BUILDER originatingElement(Optional<?> originatingElement) {
            Objects.requireNonNull(originatingElement);
            this.originatingElement = originatingElement.map(Object.class::cast).orElse(this.originatingElement);
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
            private final TypeName declaredType;
            private final TypeName rawType;
            private final TypeName typeName;

            /**
             * Create an instance providing a builder.
             *
             * @param builder extending builder base of this prototype
             */
            protected TypeInfoImpl(TypeInfo.BuilderBase<?, ?> builder) {
                this.typeName = builder.typeName().get();
                this.rawType = builder.rawType().get();
                this.declaredType = builder.declaredType().get();
                this.description = builder.description().map(Function.identity());
                this.typeKind = builder.typeKind().get();
                this.kind = builder.kind().get();
                this.elementInfo = List.copyOf(builder.elementInfo());
                this.otherElementInfo = List.copyOf(builder.otherElementInfo());
                this.referencedTypeNamesToAnnotations =
                        Collections.unmodifiableMap(new LinkedHashMap<>(builder.referencedTypeNamesToAnnotations()));
                this.referencedModuleNames = Collections.unmodifiableMap(new LinkedHashMap<>(builder.referencedModuleNames()));
                this.superTypeInfo = builder.superTypeInfo().map(Function.identity());
                this.interfaceTypeInfo = List.copyOf(builder.interfaceTypeInfo());
                this.modifiers = Collections.unmodifiableSet(new LinkedHashSet<>(builder.modifiers()));
                this.elementModifiers = Collections.unmodifiableSet(new LinkedHashSet<>(builder.elementModifiers()));
                this.accessModifier = builder.accessModifier().get();
                this.module = builder.module().map(Function.identity());
                this.originatingElement = builder.originatingElement().map(Function.identity());
                this.annotations = List.copyOf(builder.annotations());
                this.inheritedAnnotations = List.copyOf(builder.inheritedAnnotations());
            }

            @Override
            public TypeName typeName() {
                return typeName;
            }

            @Override
            public TypeName rawType() {
                return rawType;
            }

            @Override
            public TypeName declaredType() {
                return declaredType;
            }

            @Override
            public Optional<String> description() {
                return description;
            }

            @Override
            @Deprecated(since = "4.1.0", forRemoval = true)
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
            @Deprecated(since = "4.1.0", forRemoval = true)
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
                        + "rawType=" + rawType + ","
                        + "declaredType=" + declaredType + ","
                        + "kind=" + kind + ","
                        + "elementInfo=" + elementInfo + ","
                        + "superTypeInfo=" + superTypeInfo + ","
                        + "elementModifiers=" + elementModifiers + ","
                        + "accessModifier=" + accessModifier + ","
                        + "module=" + module
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
                        && Objects.equals(rawType, other.rawType())
                        && Objects.equals(declaredType, other.declaredType())
                        && Objects.equals(kind, other.kind())
                        && Objects.equals(elementInfo, other.elementInfo())
                        && Objects.equals(superTypeInfo, other.superTypeInfo())
                        && Objects.equals(elementModifiers, other.elementModifiers())
                        && Objects.equals(accessModifier, other.accessModifier())
                        && Objects.equals(module, other.module());
            }

            @Override
            public int hashCode() {
                return Objects.hash(typeName,
                                    rawType,
                                    declaredType,
                                    kind,
                                    elementInfo,
                                    superTypeInfo,
                                    elementModifiers,
                                    accessModifier,
                                    module);
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
