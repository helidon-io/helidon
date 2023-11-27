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

package io.helidon.inject.tools;

import java.util.List;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.inject.api.ServiceInfoBasics;

/**
 * The request will be generated internally and then passed to the appropriate
 * {@link io.helidon.inject.tools.spi.CustomAnnotationTemplateCreator} to handle the request.
 */
@Prototype.Blueprint
interface CustomAnnotationTemplateRequestBlueprint {

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
    TypedElementInfo targetElement();

    /**
     * The access modifier of the element.
     *
     * @return the access modifier of the element
     */
    AccessModifier targetElementAccess();

    /**
     * Only applicable for {@link javax.lang.model.element.ElementKind#METHOD} or
     * {@link javax.lang.model.element.ElementKind#CONSTRUCTOR}.
     *
     * @return the list of typed arguments for this method or constructor
     */
    List<TypedElementInfo> targetElementArgs();

    /**
     * Returns true if the element is declared to be static.
     *
     * @return returns true if the element is declared to be private
     */
    @Option.DefaultBoolean(false)
    boolean isElementStatic();

    /**
     * Projects the {@link #enclosingTypeInfo()} as a {@link io.helidon.inject.api.ServiceInfoBasics} type.
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
    @Option.DefaultBoolean(true)
    boolean isFilerEnabled();

    /**
     * Generic template creator.
     *
     * @return the generic template creator
     */
    GenericTemplateCreator genericTemplateCreator();
}
