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

package io.helidon.pico.builder.spi;

import java.lang.annotation.Annotation;
import java.util.Set;

import io.helidon.pico.types.AnnotationAndValue;

/**
 * Implementors of this contract will be called to process annotation types declared in the API.
 */
public interface BuilderCreator {

    /**
     * @return Implementors should return the set of annotations they can handle.
     */
    Set<Class<? extends Annotation>> getSupportedAnnotationTypes();

    /**
     * Implementors should return null if the type is not supported.
     *
     * @param typeInfo the type info
     * @param builderAnnotation the builderAnnotation that triggered the creation
     * @return the body of the source to code-gen (tooling will handle the code gen aspects)
     */
    TypeAndBody create(TypeInfo typeInfo, AnnotationAndValue builderAnnotation);

}
