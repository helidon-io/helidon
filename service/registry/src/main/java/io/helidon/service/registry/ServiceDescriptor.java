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

package io.helidon.service.registry;

/**
 * A descriptor of a service. In addition to providing service metadata, this also allows instantiation
 * of the service instance, with dependent services as parameters.
 *
 * @param <T> type of the described service
 */
public interface ServiceDescriptor<T> extends ServiceInfo {
    /**
     * Create a new service instance.
     *
     * @param ctx dependency context with all dependencies of this service
     * @return a new instance, must be of the type T or a subclass
     */
    // we cannot return T, as it does not allow us to correctly handle inheritance
    default Object instantiate(DependencyContext ctx) {
        throw new IllegalStateException("Cannot instantiate type " + serviceType().fqName() + ", as it is either abstract,"
                                                + " or an interface.");
    }

    /**
     * Invoke {@link io.helidon.service.registry.Service.PostConstruct} annotated method(s).
     *
     * @param instance instance to use
     */
    default void postConstruct(T instance) {
    }

    /**
     * Invoke {@link io.helidon.service.registry.Service.PreDestroy} annotated method(s).
     *
     * @param instance instance to use
     */
    default void preDestroy(T instance) {
    }
}
