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

/**
 * Constants to be used with {@link io.helidon.common.types.TypeInfo} and {@link io.helidon.common.types.TypedElementInfo}.
 *
 * @deprecated use {@link io.helidon.common.types.ElementKind}, {@link io.helidon.common.types.Modifier} and
 *              {@link io.helidon.common.types.AccessModifier} instead, and appropriate methods that return these types
 */
@Deprecated(forRemoval = true)
public final class TypeValues {
    /**
     * The {@code public} modifier.
     */
    public static final String MODIFIER_PUBLIC = "public";
    /**
     * The {@code protected} modifier.
     */
    public static final String MODIFIER_PROTECTED = "protected";
    /**
     * The {@code private} modifier.
     */
    public static final String MODIFIER_PRIVATE = "private";
    /**
     * The {@code abstract} modifier.
     */
    public static final String MODIFIER_ABSTRACT = "abstract";
    /**
     * The {@code default} modifier.
     */
    public static final String MODIFIER_DEFAULT = "default";
    /**
     * The {@code static} modifier.
     */
    public static final String MODIFIER_STATIC = "static";
    /**
     * The {@code sealed} modifier.
     */
    public static final String MODIFIER_SEALED = "sealed";
    /**
     * The {@code final} modifier.
     */
    public static final String MODIFIER_FINAL = "final";
    /**
     * Field element type kind.
     * See javax.lang.model.element.ElementKind#FIELD
     */
    public static final String KIND_FIELD = "FIELD";
    /**
     * Method element type kind.
     * See javax.lang.model.element.ElementKind#METHOD
     */
    public static final String KIND_METHOD = "METHOD";
    /**
     * Constructor element type kind.
     * See javax.lang.model.element.ElementKind#CONSTRUCTOR
     */
    public static final String KIND_CONSTRUCTOR = "CONSTRUCTOR";
    /**
     * Parameter element type kind.
     * See javax.lang.model.element.ElementKind#PARAMETER
     */
    public static final String KIND_PARAMETER = "PARAMETER";
    /**
     * Interface element type kind.
     * See javax.lang.model.element.ElementKind#INTERFACE
     */
    public static final String KIND_INTERFACE = "INTERFACE";
    /**
     * Interface element type kind.
     * See javax.lang.model.element.ElementKind#CLASS
     */
    public static final String KIND_CLASS = "CLASS";
    /**
     * Enum element type kind.
     * See javax.lang.model.element.ElementKind#ENUM
     */
    public static final String KIND_ENUM = "ENUM";
    /**
     * Annotation element type kind.
     * See javax.lang.model.element.ElementKind#ANNOTATION_TYPE
     */
    public static final String KIND_ANNOTATION_TYPE = "ANNOTATION_TYPE";
    /**
     * Package element type kind.
     * See javax.lang.model.element.ElementKind#PACKAGE
     */
    public static final String KIND_PACKAGE = "PACKAGE";
    /**
     * Record element type kind (since Java 16).
     * See javax.lang.model.element.ElementKind#RECORD
     */
    public static final String KIND_RECORD = "RECORD";

    private TypeValues() {
    }
}
