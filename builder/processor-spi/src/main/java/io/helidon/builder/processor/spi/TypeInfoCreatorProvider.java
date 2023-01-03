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

package io.helidon.builder.processor.spi;

import java.util.Optional;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;

import io.helidon.pico.types.AnnotationAndValue;
import io.helidon.pico.types.TypeName;

/**
 * Java {@link java.util.ServiceLoader} provider interface used to discover type info creators.
 * <p>
 * Used to create a {@link TypeInfo} from the provided arguments.
 */
public interface TypeInfoCreatorProvider {

    /**
     * Creates a {@link TypeInfo}.
     *
     * @param annotation    the annotation that triggered the creation
     * @param typeName      the type name that is being processed that is annotated with the triggering annotation
     * @param element       the element representative of the typeName
     * @param processingEnv the processing environment
     * @return the type info associated with the arguments being processed, or empty if not able to process the type
     */
    Optional<TypeInfo> createTypeInfo(AnnotationAndValue annotation,
                                      TypeName typeName,
                                      TypeElement element,
                                      ProcessingEnvironment processingEnv);

}
