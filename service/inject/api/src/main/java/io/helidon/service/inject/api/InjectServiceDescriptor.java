/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

import java.util.Set;

import io.helidon.service.registry.DependencyContext;
import io.helidon.service.registry.ServiceDescriptor;

/**
 * A descriptor of a service. In addition to providing service metadata, this also allows instantiation
 * and injection to the service instance. The descriptor is usually code generated, though it can be
 * handcrafted (it must be annotated with {@link io.helidon.service.registry.Service.Descriptor} in such a case).
 *
 * @param <T> type of the service this descriptor describes
 */
public interface InjectServiceDescriptor<T> extends ServiceDescriptor<T>, InjectServiceInfo {
    /**
     * Create a new service instance.
     *
     * @param ctx                  injection context with all injection points data
     * @param interceptionMetadata interception metadata to use when the constructor should be intercepted
     * @return a new instance, must be of the type T or a subclass
     */
    // we cannot return T, as it does not allow us to correctly handle inheritance
    default Object instantiate(DependencyContext ctx, InterceptionMetadata interceptionMetadata) {
        throw new IllegalStateException("Cannot instantiate type " + serviceType().fqName() + ", as it is either abstract,"
                                                + " or an interface.");
    }

    /**
     * Inject fields and methods.
     *
     * @param ctx                  injection context
     * @param interceptionMetadata interception metadata to support interception of field injection
     * @param injected             mutable set of already injected methods from subtypes
     * @param instance             instance to update
     */
    default void inject(DependencyContext ctx,
                        InterceptionMetadata interceptionMetadata,
                        Set<String> injected,
                        T instance) {
    }
}
