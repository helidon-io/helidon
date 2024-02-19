/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

package io.helidon.service.inject.api;

import java.util.List;
import java.util.Optional;

import io.helidon.builder.api.Prototype;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypedElementInfo;

/**
 * Invocation context provides metadata about the invoked element to an interceptor.
 * Used by {@link io.helidon.service.inject.api.Interception.Interceptor}.
 */
@Prototype.Blueprint
interface InvocationContextBlueprint {
    /**
     * The service instance being intercepted.
     * This always returns the underlying instance.
     *
     * @return instance being intercepted, or empty optional if the intercepted method is not done on an instance
     *         (i.e. a constructor interception)
     */
    Optional<Object> serviceInstance();

    /**
     * The service being intercepted.
     *
     * @return the service being intercepted
     */
    InjectServiceInfo serviceInfo();

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
