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

package io.helidon.inject.api;

import java.util.List;
import java.util.Map;

import io.helidon.builder.api.Prototype;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

import jakarta.inject.Provider;

/**
 * Used by {@link Interceptor}.
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
@Prototype.Blueprint
interface InvocationContextBlueprint {

    /**
     * The service provider being intercepted.
     *
     * @return the service provider being intercepted
     */
    ServiceProvider<?> serviceProvider();

    /**
     * The service type name for the root service provider.
     *
     * @return the service type name for the root service provider
     */
    TypeName serviceTypeName();

    /**
     * The annotations on the enclosing type.
     *
     * @return the annotations on the enclosing type
     */
    List<Annotation> classAnnotations();

    /**
     * The element info represents the method (or the constructor) being invoked.
     *
     * @return the element info represents the method (or the constructor) being invoked
     */
    TypedElementInfo elementInfo();

    /**
     * The method/element argument info.
     *
     * @return the method/element argument info
     */
    List<TypedElementInfo> elementArgInfo();

    /**
     * The interceptor chain.
     *
     * @return the interceptor chain
     */
    List<Provider<Interceptor>> interceptors();

    /**
     * The contextual info that can be shared between interceptors.
     *
     * @return the read/write contextual data that is passed between each chained interceptor
     */
    Map<String, Object> contextData();

}
