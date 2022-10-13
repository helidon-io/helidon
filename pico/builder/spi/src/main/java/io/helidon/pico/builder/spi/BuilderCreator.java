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
import java.util.Optional;
import java.util.Set;

import io.helidon.pico.types.AnnotationAndValue;

/**
 * Implementors of this contract will be called to process {@link io.helidon.pico.builder.api.BuilderTrigger}-annotated annotation
 * types that they know how to handle. This is based upon the {@link #getSupportedAnnotationTypes()} as well as the {@link io.helidon.common.Weight}
 * assigned to the implementation class implementing this interface.
 */
public interface BuilderCreator {

    /**
     * The set of {@link io.helidon.pico.builder.api.Builder}-like annotations that this creator knows how to handle. Note that
     * this annotation must also be annotated with {@link io.helidon.pico.builder.api.BuilderTrigger} to qualify for inclusion.
     *
     * @return Implementors should return the set of annotations they can handle
     */
    Set<Class<? extends Annotation>> getSupportedAnnotationTypes();

    /**
     * Implementors should return {@link java.util.Optional#empty()} if the given typeInfo is not supported by this creator.
     * Note that the actual code generation is provided directly by the built-in builder tooling module. Implementors, therefore,
     * should only return the {@link io.helidon.pico.builder.spi.TypeAndBody} that describes the code generated class, and only
     * if they are prepared to code generate the builder implementation.
     *
     * @param typeInfo          the target type being processed - this type was found to have one of our supported annotations
     *                          mentioned in {@link #getSupportedAnnotationTypes()}
     * @param builderAnnotation the annotation that triggered the builder creation
     * @return the type and body of the source to code-generate (tooling will handle the code gen aspects)
     */
    Optional<TypeAndBody> create(TypeInfo typeInfo, AnnotationAndValue builderAnnotation);

}
