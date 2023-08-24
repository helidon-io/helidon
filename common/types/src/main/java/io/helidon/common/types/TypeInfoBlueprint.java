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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.config.metadata.ConfiguredOption;

/**
 * Represents the model object for a type.
 */
@Prototype.Blueprint
interface TypeInfoBlueprint extends Annotated {
    /**
     * The type name.
     *
     * @return the type name
     */
    @ConfiguredOption(required = true)
    TypeName typeName();

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
     */
    @ConfiguredOption(required = true)
    String typeKind();

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
     */
    @Option.Singular
    Set<String> modifiers();

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
}
