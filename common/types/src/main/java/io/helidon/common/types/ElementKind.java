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
 * Kind of element.
 * Order is significant, as it is used downstream in comparator of injection point ids.
 */
public enum ElementKind {
    /**
     * Constructor element type kind.
     * See javax.lang.model.element.ElementKind#CONSTRUCTOR
     */
    CONSTRUCTOR,
    /**
     * Field element type kind.
     * See javax.lang.model.element.ElementKind#FIELD
     */
    FIELD,
    /**
     * Method element type kind.
     * See javax.lang.model.element.ElementKind#METHOD
     */
    METHOD,
    /**
     * Parameter element type kind.
     * See javax.lang.model.element.ElementKind#PARAMETER
     */
    PARAMETER,
    /**
     * Interface element type kind.
     * See javax.lang.model.element.ElementKind#INTERFACE
     */
    INTERFACE,
    /**
     * Interface element type kind.
     * See javax.lang.model.element.ElementKind#CLASS
     */
    CLASS,
    /**
     * Enum element type kind.
     * See javax.lang.model.element.ElementKind#ENUM
     */
    ENUM,
    /**
     * Annotation element type kind.
     * See javax.lang.model.element.ElementKind#ANNOTATION_TYPE
     */
    ANNOTATION_TYPE,
    /**
     * Package element type kind.
     * See javax.lang.model.element.ElementKind#PACKAGE
     */
    PACKAGE,
    /**
     * Record element type kind (since Java 16).
     * See javax.lang.model.element.ElementKind#RECORD
     */
    RECORD,
    /**
     * Component of a record.
     */
    RECORD_COMPONENT,
    /**
     * Static initialization block.
     */
    STATIC_INIT,
    /**
     * Instance initialization block.
     */
    INSTANCE_INIT,
    /**
     * Enumeration constant.
     */
    ENUM_CONSTANT,
    /**
     * Local variable.
     */
    LOCAL_VARIABLE,
    /**
     * Not the stuff you are looking for.
     */
    OTHER
}
