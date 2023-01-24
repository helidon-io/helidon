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

package io.helidon.pico;

import java.util.List;
import java.util.Map;

import io.helidon.builder.Builder;
import io.helidon.pico.types.AnnotationAndValue;
import io.helidon.pico.types.TypeName;
import io.helidon.pico.types.TypedElementName;

import jakarta.inject.Provider;

/**
 * Used by {@link Interceptor}.
 */
@Builder
public interface InvocationContext {

    /**
     * The root service provider being intercepted.
     *
     * @return the root service provider being intercepted
     */
    ServiceProvider<?> rootServiceProvider();

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
    List<AnnotationAndValue> classAnnotations();

    /**
     * The element info represents the method (or the constructor) being invoked.
     *
     * @return the element info represents the method (or the constructor) being invoked
     */
    TypedElementName elementInfo();

    /**
     * The method/element argument info.
     *
     * @return the method/element argument info.
     */
    TypedElementName[] elementArgInfo();

    /**
     * The arguments to the method.
     *
     * @return the read/write method/element arguments
     */
    Object[] elementArgs();

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
