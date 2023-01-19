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

package io.helidon.pico.tools;

import java.util.List;

import io.helidon.builder.Builder;
import io.helidon.builder.processor.spi.TypeInfo;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.pico.InjectionPointInfo;
import io.helidon.pico.ServiceInfoBasics;
import io.helidon.pico.types.TypeName;
import io.helidon.pico.types.TypedElementName;

/**
 * The request will be generated internally and then passed to the appropriate
 * {@link CustomAnnotationTemplateCreator} to handle the request.
 */
@Builder
public interface CustomAnnotationTemplateRequest {

    /**
     * The type of the annotation being processed.
     *
     * @return the type of the annotation being processed
     */
    TypeName annoTypeName();

    /**
     * The target element being processed. This element is the one with the {@link #annoTypeName()} assigned to it.
     *
     * @return the target element being processed
     */
    TypedElementName targetElement();

    /**
     * The access modifier of the element.
     *
     * @return the access modifier of the element
     */
    InjectionPointInfo.Access targetElementAccess();

    /**
     * Only applicable for {@link javax.lang.model.element.ElementKind#METHOD} or
     * {@link javax.lang.model.element.ElementKind#CONSTRUCTOR}.
     *
     * @return the list of typed arguments for this method or constructor
     */
    List<TypedElementName> targetElementArgs();

    /**
     * Returns true if the element is declared to be static.
     *
     * @return returns true if the element is declared to be private.
     */
    boolean isElementStatic();

    /**
     * Projects the {@link #enclosingTypeInfo()} as a {@link ServiceInfoBasics} type.
     *
     * @return the basic service info of the element being processed
     */
    ServiceInfoBasics serviceInfo();

    /**
     * The enclosing class type info of the target element being processed.
     *
     * @return the enclosing class type info of the target element being processed
     */
    TypeInfo enclosingTypeInfo();

    /**
     * Returns true if the code should be literally generated with the provided {@code filer}.
     *
     * @return true if the code should be literally generated with the filer
     */
    @ConfiguredOption("true")
    boolean isFilerEnabled();

    /**
     * Helper tools.
     *
     * @return helper tools
     */
    TemplateHelperTools templateHelperTools();

}
