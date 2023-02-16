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

/**
 * Provides a way to describe method, field, or annotation attribute.
 */
public interface TypedElementName {
    String KIND_FIELD = "FIELD";
    String KIND_PARAMETER = "PARAMETER";

    /**
     * The type name for the element (e.g., java.util.List). If the element is a method, then this is the return type of
     * the method.
     *
     * @return the type name of the element
     */
    TypeName typeName();

    /**
     * The element (e.g., method, field, etc) name.
     *
     * @return the name of the element
     */
    String elementName();

    /**
     * The kind of element (e.g., method, field, etc).
     *
     * @return the element kind
     */
    String elementKind();

    /**
     * The default value assigned to the element, represented as a string.
     *
     * @return the default value as a string
     */
    Optional<String> defaultValue();

    /**
     * The list of known annotations for this element. Note that "known" implies that the annotation is visible, which depends
     * upon the context in which it was build.
     *
     * @return the list of annotations on this element
     */
    List<AnnotationAndValue> annotations();

    /**
     * The list of known annotations on the type name referenced by {@link #typeName()}.
     *
     * @return the list of annotations on this element's (return) type.
     */
    List<AnnotationAndValue> elementTypeAnnotations();

    /**
     * Returns the component type names describing the element.
     *
     * @return the component type names of the element
     */
    List<TypeName> componentTypeNames();

    /**
     * Element modifiers.
     *
     * @return element modifiers
     */
    Set<String> modifierNames();

}
