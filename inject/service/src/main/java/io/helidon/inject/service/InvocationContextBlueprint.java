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

package io.helidon.inject.service;

import java.util.List;

import io.helidon.builder.api.Prototype;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypedElementInfo;

/**
 * Invocation context provides metadata about the invoked element to an interceptor.
 * Used by {@link io.helidon.inject.service.Interception.Interceptor}.
 */
@Prototype.Blueprint
interface InvocationContextBlueprint {
    /**
     * The service being intercepted.
     *
     * @return the service being intercepted
     */
    ServiceInfo serviceInfo();

    /**
     * Annotations on the enclosing type.
     *
     * @return the annotations on the enclosing type
     */
    List<Annotation> typeAnnotations();

    /**
     * The element info represents the method, field, or the constructor being invoked.
     *
     * @return the element info of element being intercepted
     */
    TypedElementInfo elementInfo();
}