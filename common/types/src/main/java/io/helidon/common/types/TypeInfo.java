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
     * Field element type kind.
     * See javax.lang.model.element.ElementKind#FIELD
     */
    String KIND_FIELD = "FIELD";

    /**
     * Method element type kind.
     * See javax.lang.model.element.ElementKind#METHOD
     */
    String KIND_METHOD = "METHOD";

    /**
     * Constructor element type kind.
     * See javax.lang.model.element.ElementKind#CONSTRUCTOR
     */
    String KIND_CONSTRUCTOR = "CONSTRUCTOR";

    /**
     * Parameter element type kind.
     * See javax.lang.model.element.ElementKind#PARAMETER
     */
    String KIND_PARAMETER = "PARAMETER";

    /**
     * Interface element type kind.
     * See javax.lang.model.element.ElementKind#INTERFACE
     */
    String KIND_INTERFACE = "INTERFACE";

    /**
     * Interface element type kind.
     * See javax.lang.model.element.ElementKind#CLASS
     */
    String KIND_CLASS = "CLASS";

    /**
     * Enum element type kind.
     * See javax.lang.model.element.ElementKind#ENUM
     */
    String KIND_ENUM = "ENUM";

    /**
     * Annotation element type kind.
     * See javax.lang.model.element.ElementKind#ANNOTATION_TYPE
     */
    String KIND_ANNOTATION = "ANNOTATION_TYPE";

    /**
     * Package element type kind.
     * See javax.lang.model.element.ElementKind#PACKAGE
     */
    String KIND_PACKAGE = "PACKAGE";

    /**
     * Record element type kind (since Java 16).
     * See javax.lang.model.element.ElementKind#RECORD
     */
    String KIND_RECORD = "RECORD";

    /**
     * The type name.
     *
     * @return the type name
     */
    TypeName typeName();

    /**
     * The type element kind.
     *
     * @return the type element kind (e.g., "{@value #KIND_INTERFACE}", "{@value #KIND_ANNOTATION}", etc.)
     * @see #KIND_CLASS and other constants on this class prefixed with {@code TYPE}
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
