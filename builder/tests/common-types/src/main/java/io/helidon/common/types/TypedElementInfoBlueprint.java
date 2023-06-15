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

import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.helidon.builder.api.Prototype;
import io.helidon.config.metadata.ConfiguredOption;

/**
 * An annotation with defined values.
 */
@Prototype.Blueprint
@Prototype.CustomMethods(TypedElementInfoSupport.class)
interface TypedElementInfoBlueprint extends Annotated {
    /**
     * Description, such as javadoc, if available.
     *
     * @return description of this element
     */
    @Prototype.Redundant
    Optional<String> description();

    /**
     * The type name for the element (e.g., java.util.List). If the element is a method, then this is the return type of
     * the method.
     *
     * @return the type name of the element
     */
    @ConfiguredOption(required = true)
    TypeName typeName();

    /**
     * The element (e.g., method, field, etc) name.
     *
     * @return the name of the element
     */
    @ConfiguredOption(required = true)
    String elementName();

    /**
     * The kind of element (e.g., method, field, etc).
     *
     * @return the element kind
     * @see io.helidon.common.types.TypeInfo
     */
    @ConfiguredOption(required = true)
    String elementTypeKind();

    /**
     * The default value assigned to the element, represented as a string.
     *
     * @return the default value as a string
     */
    @Prototype.Redundant
    Optional<String> defaultValue();

    /**
     * The list of known annotations on the type name referenced by {@link #typeName()}.
     *
     * @return the list of annotations on this element's (return) type.
     */
    @Prototype.Redundant
    List<Annotation> elementTypeAnnotations();

    /**
     * Returns the component type names describing the element.
     *
     * @return the component type names of the element
     */
    @Prototype.Redundant
    List<TypeName> componentTypes();

    /**
     * Element modifiers.
     *
     * @return element modifiers
     * @see io.helidon.common.types.TypeInfo
     */
    @Prototype.Redundant
    @Prototype.Singular
    Set<String> modifiers();

    /**
     * The enclosing type name for this typed element. Applicable when this instance represents a
     * {@link io.helidon.common.types.TypeValues#KIND_FIELD} or
     * {@link io.helidon.common.types.TypeValues#KIND_METHOD}.
     *
     * @return the enclosing type element
     */
    Optional<TypeName> enclosingType();

    /**
     * Parameter arguments applicable if this type element represents a {@link io.helidon.common.types.TypeValues#KIND_METHOD}.
     * Each instance of this list
     * will be the individual {@link io.helidon.common.types.TypeValues#KIND_PARAMETER}'s for the method.
     *
     * @return the list of parameters belonging to this method if applicable
     */
    @Prototype.Singular
    List<TypedElementInfo> parameterArguments();
}
