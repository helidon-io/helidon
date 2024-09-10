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

import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * An annotation with defined values.
 */
@Prototype.Blueprint(decorator = TypedElementInfoSupport.BuilderDecorator.class)
@Prototype.CustomMethods(TypedElementInfoSupport.class)
interface TypedElementInfoBlueprint extends Annotated {
    /**
     * Description, such as javadoc, if available.
     *
     * @return description of this element
     */
    @Option.Redundant
    Optional<String> description();

    /**
     * The type name for the element (e.g., java.util.List). If the element is a method, then this is the return type of
     * the method.
     *
     * @return the type name of the element
     */
    @Option.Required
    TypeName typeName();

    /**
     * The element (e.g., method, field, etc) name.
     *
     * @return the name of the element
     */
    @Option.Required
    String elementName();

    /**
     * The kind of element (e.g., method, field, etc).
     *
     * @return the element kind
     * @see io.helidon.common.types.TypeInfo
     * @deprecated use {@link io.helidon.common.types.TypedElementInfo#kind()} instead
     */
    @Option.Required
    @Option.Deprecated("kind")
    @Option.Redundant
    @Deprecated(forRemoval = true, since = "4.1.0")
    String elementTypeKind();

    /**
     * The kind of element (e.g., method, field, etc).
     *
     * @return the element kind
     * @see io.helidon.common.types.ElementKind
     */
    ElementKind kind();

    /**
     * The default value assigned to the element, represented as a string.
     *
     * @return the default value as a string
     */
    @Option.Redundant
    Optional<String> defaultValue();

    /**
     * The list of known annotations on the type name referenced by {@link io.helidon.common.types.TypedElementInfo#typeName()}.
     *
     * @return the list of annotations on this element's (return) type.
     */
    @Option.Redundant
    List<Annotation> elementTypeAnnotations();

    /**
     * Returns the component type names describing the element.
     *
     * @return the component type names of the element
     */
    @Option.Redundant
    List<TypeName> componentTypes();

    /**
     * Element modifiers.
     *
     * @return element modifiers
     * @see io.helidon.common.types.TypeInfo
     * @deprecated use {@link io.helidon.common.types.TypedElementInfo#elementModifiers()} instead
     */
    @Option.Singular
    @Option.Redundant
    @Option.Deprecated("elementModifiers")
    @Deprecated(forRemoval = true, since = "4.1.0")
    Set<String> modifiers();

    /**
     * Element modifiers.
     *
     * @return element modifiers
     * @see io.helidon.common.types.Modifier
     * @see #accessModifier()
     */
    @Option.Redundant
    @Option.Singular
    Set<Modifier> elementModifiers();

    /**
     * Access modifier of the element.
     *
     * @return access modifier
     */
    @Option.Redundant
    AccessModifier accessModifier();

    /**
     * The enclosing type name for this typed element. Applicable when this instance represents a
     * {@link io.helidon.common.types.ElementKind#FIELD}, or
     * {@link io.helidon.common.types.ElementKind#METHOD}, or
     * {@link io.helidon.common.types.ElementKind#PARAMETER}
     *
     * @return the enclosing type element
     */
    Optional<TypeName> enclosingType();

    /**
     * Parameter arguments applicable if this type element represents a {@link io.helidon.common.types.ElementKind#METHOD}.
     * Each instance of this list
     * will be the individual {@link io.helidon.common.types.ElementKind#PARAMETER}'s for the method.
     *
     * @return the list of parameters belonging to this method if applicable
     */
    @Option.Singular
    List<TypedElementInfo> parameterArguments();

    /**
     * List of all thrown types that are checked ({@link java.lang.Exception} and {@link java.lang.Error}).
     *
     * @return set of thrown checked types
     */
    Set<TypeName> throwsChecked();

    /**
     * The element used to create this instance.
     * The type of the object depends on the environment we are in - it may be an {@code Element} in annotation processing,
     * or a {@code MethodInfo} (and such) when using classpath scanning.
     *
     * @return originating element
     */
    @Option.Redundant
    Optional<Object> originatingElement();

    /**
     * The element used to create this instance, or {@link #signature()} if none provided.
     * The type of the object depends on the environment we are in - it may be an {@code TypeElement} in annotation processing,
     * or a {@code MethodInfo} (and such) when using classpath scanning.
     *
     * @return originating element, or the signature of this element
     */
    default Object originatingElementValue() {
        return originatingElement().orElseGet(this::signature);
    }

    /**
     * Signature of this element.
     *
     * @return signature of this element
     * @see io.helidon.common.types.ElementSignature
     */
    @Option.Access("")
    ElementSignature signature();
}
