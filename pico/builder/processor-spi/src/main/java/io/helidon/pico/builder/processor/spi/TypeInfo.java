/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.pico.builder.processor.spi;

import java.util.List;
import java.util.Optional;

import io.helidon.pico.types.AnnotationAndValue;
import io.helidon.pico.types.TypeName;
import io.helidon.pico.types.TypedElementName;

/**
 * Represents the model object for an interface type (e.g., one that was annotated with {@link io.helidon.pico.builder.Builder}).
 */
public interface TypeInfo {

    /**
     * The type name.
     *
     * @return the type name
     */
    TypeName typeName();

    /**
     * The type element kind.
     *
     * @return the type element kind (e.g., "INTERFACE", "ANNOTATION_TYPE", etc.)
     */
    String typeKind();

    /**
     * The annotations on the type.
     *
     * @return the annotations on the type
     */
    List<AnnotationAndValue> annotations();

    /**
     * The elements that make up the type.
     *
     * @return the elements that make up the type
     */
    List<TypedElementName> elementInfo();

    /**
     * The parent/super class for this type info.
     *
     * @return the super type.
     */
    Optional<TypeInfo> superTypeInfo();

}
