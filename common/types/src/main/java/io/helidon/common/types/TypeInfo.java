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
import java.util.Optional;
import java.util.Set;

/**
 * Represents the model object for a type.
 */
public interface TypeInfo {
    /**
     * The {@code public} modifier.
     */
    String MODIFIER_PUBLIC = "PUBLIC";
    /**
     * The {@code protected} modifier.
     */
    String MODIFIER_PROTECTED = "PROTECTED";
    /**
     * The {@code private} modifier.
     */
    String MODIFIER_PRIVATE = "PRIVATE";
    /**
     * The {@code abstract} modifier.
     */
    String MODIFIER_ABSTRACT = "ABSTRACT";
    /**
     * The {@code default} modifier.
     */
    String MODIFIER_DEFAULT = "DEFAULT";
    /**
     * The {@code static} modifier.
     */
    String MODIFIER_STATIC = "STATIC";
    /**
     * The {@code sealed} modifier.
     */
    String MODIFIER_SEALED = "SEALED";
    /**
     * The {@code final} modifier.
     */
    String MODIFIER_FINAL = "FINAL";

    /**
     * Enum class.
     */
    String TYPE_ENUM = "ENUM";
    /**
     * Class, unless more specific type is more relevant, such as {@link #TYPE_ENUM}.
     */
    String TYPE_CLASS = "CLASS";
    /**
     * Annotation interface.
     */
    String TYPE_ANNOTATION = "ANNOTATION_TYPE";
    /**
     * Interface.
     */
    String TYPE_INTERFACE = "INTERFACE";
    /**
     * Record type (sing Java 16).
     */
    String TYPE_RECORD = "RECORD";

    /**
     * The type name.
     *
     * @return the type name
     */
    TypeName typeName();

    /**
     * The type element kind.
     *
     * @return the type element kind (e.g., "{@value #TYPE_INTERFACE}", "{@value #TYPE_ANNOTATION}", etc.)
     * @see #TYPE_CLASS and other constants on this class prefixed with {@code TYPE}
     */
    String typeKind();

    /**
     * The annotations on the type.
     *
     * @return the annotations on the type
     */
    List<AnnotationAndValue> annotations();

    /**
     * The elements that make up the type that are relevant for processing.
     *
     * @return the elements that make up the type that are relevant for processing
     */
    List<TypedElementName> elementInfo();

    /**
     * The elements that make up this type that are considered "other", or being skipped because they are irrelevant to
     * processing.
     *
     * @return the elements that still make up the type, but are otherwise deemed irrelevant for processing
     */
    List<TypedElementName> otherElementInfo();

    /**
     * The parent/super class for this type info.
     *
     * @return the super type
     */
    Optional<TypeInfo> superTypeInfo();

    /**
     * Element modifiers.
     *
     * @return element modifiers
     * @see #MODIFIER_PUBLIC and other constants prefixed with {@code MODIFIER}
     */
    Set<String> modifierNames();

}
