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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Represents the model object for a type.
 */
@Prototype.Blueprint(decorator = TypeInfoSupport.TypeInfoDecorator.class)
interface TypeInfoBlueprint extends Annotated {
    /**
     * The type name.
     *
     * @return the type name
     */
    @Option.Required
    TypeName typeName();

    /**
     * Description, such as javadoc, if available.
     *
     * @return description of this element
     */
    @Option.Redundant
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
     * @deprecated use {@link #kind()} instead
     */
    @Option.Required
    @Option.Deprecated("kind")
    @Option.Redundant
    @Deprecated(forRemoval = true, since = "4.1.0")
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
    @Option.Required
    ElementKind kind();

    /**
     * The elements that make up the type that are relevant for processing.
     *
     * @return the elements that make up the type that are relevant for processing
     */
    @Option.Singular
    List<TypedElementInfo> elementInfo();

    /**
     * The elements that make up this type that are considered "other", or being skipped because they are irrelevant to
     * processing.
     *
     * @return the elements that still make up the type, but are otherwise deemed irrelevant for processing
     */
    @Option.Singular
    @Option.Redundant
    List<TypedElementInfo> otherElementInfo();

    /**
     * Any Map, List, Set, or method that has {@link io.helidon.common.types.TypeName#typeArguments()} will be analyzed and any
     * type arguments will have
     * its annotations added here. Note that this only applies to non-built-in types.
     *
     * @return all referenced types
     */
    @Option.Singular
    @Option.Redundant
    Map<TypeName, List<Annotation>> referencedTypeNamesToAnnotations();

    /**
     * Check if an annotation type has a specific meta annotation.
     *
     * @param annotation     annotation to check meta annotation for
     * @param metaAnnotation meta annotation type
     * @return whether the meta annotation is present on the annotation
     */
    default boolean hasMetaAnnotation(TypeName annotation, TypeName metaAnnotation) {
        return hasMetaAnnotation(annotation, metaAnnotation, false);
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
        return metaAnnotation(annotation, metaAnnotation, inherited).isPresent();
    }

    /**
     * Find a meta annotation.
     *
     * @param annotation     annotation to check meta annotation for
     * @param metaAnnotation meta annotation type
     * @return meta annotation, or empty if not defined
     */
    default Optional<Annotation> metaAnnotation(TypeName annotation, TypeName metaAnnotation) {
        return metaAnnotation(annotation, metaAnnotation, false);
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
        return metaAnnotation(annotation, metaAnnotation, inherited, new LinkedHashSet<>());
    }

    /**
     * Populated if the (external) module name containing the type is known.
     *
     * @return type names to its associated defining module name
     */
    @Option.Singular
    @Option.Redundant
    Map<TypeName, String> referencedModuleNames();

    /**
     * The parent/super class for this type info.
     *
     * @return the super type
     */
    Optional<TypeInfo> superTypeInfo();

    /**
     * The interface classes for this type info.
     *
     * @return the interface type info
     */
    @Option.Singular
    @Option.Redundant
    List<TypeInfo> interfaceTypeInfo();

    /**
     * Element modifiers.
     *
     * @return element modifiers
     * @see io.helidon.common.types.TypeValues#MODIFIER_PUBLIC and other constants prefixed with {@code MODIFIER}
     * @deprecated use {@link #elementModifiers()} instead
     */
    @Option.Singular
    @Option.Redundant
    @Option.Deprecated("elementModifiers")
    @Deprecated(forRemoval = true, since = "4.1.0")
    Set<String> modifiers();

    /**
     * Type modifiers.
     *
     * @return set of modifiers that are present on the type (and that we understand)
     * @see io.helidon.common.types.Modifier
     * @see #accessModifier()
     */
    @Option.Singular
    Set<Modifier> elementModifiers();

    /**
     * Access modifier.
     *
     * @return access modifier
     */
    AccessModifier accessModifier();

    /**
     * Module of this type, if available.
     *
     * @return module name
     */
    Optional<String> module();

    /**
     * The element used to create this instance.
     * The type of the object depends on the environment we are in - it may be an {@code TypeElement} in annotation processing,
     * or a {@code ClassInfo} when using classpath scanning.
     *
     * @return originating element
     */
    @Option.Redundant
    Optional<Object> originatingElement();

    /**
     * Uses {@link #referencedModuleNames()} to determine if the module name is known for the given type.
     *
     * @param typeName the type name to lookup
     * @return the module name if it is known
     */
    default Optional<String> moduleNameOf(TypeName typeName) {
        String moduleName = referencedModuleNames().get(typeName);
        moduleName = (moduleName != null && moduleName.isBlank()) ? null : moduleName;
        return Optional.ofNullable(moduleName);
    }

    private Optional<Annotation> metaAnnotation(TypeName annotation,
                                                TypeName metaAnnotation,
                                                boolean inherited,
                                                Set<TypeName> processed) {
        List<Annotation> annotations = referencedTypeNamesToAnnotations().get(annotation);
        if (annotations == null) {
            return Optional.empty();
        }
        Optional<Annotation> found = Annotations.findFirst(metaAnnotation, annotations);
        if (found.isPresent()) {
            return found;
        }

        if (inherited) {
            for (Annotation referencedAnnotation : annotations) {
                // maybe meta annotation has meta annotation
                if (processed.add(referencedAnnotation.typeName())) {
                    found = metaAnnotation(referencedAnnotation.typeName(), metaAnnotation, inherited, processed);
                    if (found.isPresent()) {
                        return found;
                    }
                }
            }
        }
        return Optional.empty();
    }
}
