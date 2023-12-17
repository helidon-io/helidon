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

package io.helidon.inject;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.helidon.common.types.TypeName;
import io.helidon.inject.service.Ip;
import io.helidon.inject.service.Qualifier;
import io.helidon.inject.service.ServiceInfo;

/**
 * Provides management lifecycle around services.
 *
 * @param <T> the type that this service provider manages
 */
public interface ServiceProvider<T> extends ServiceInfo, InjectionPointProvider<T> {

    /**
     * Identifies the service provider physically and uniquely.
     *
     * @return the unique identity of the service provider
     */
    default String id() {
        return serviceInfo().serviceType().fqName();
    }

    /**
     * Describe the service provider. This will change based upon activation state.
     *
     * @return the logical and immutable description
     */
    default String description() {
        return serviceType().classNameWithEnclosingNames() + "[" + currentActivationPhase() + "]";
    }

    /**
     * Does the service provide singletons, does it always produce the same result for every call to {@link #get()}.
     * I.e., if the managed service implements Provider or
     * {@link io.helidon.inject.InjectionPointProvider} then this typically is considered not a singleton provider.
     * I.e., If the managed services is NOT {@link io.helidon.inject.service.Injection.Singleton},
     * then it will be treated as per request / dependent
     * scope.
     * Note that this is similar in nature to RequestScope, except the "official" request scope is bound to the
     * web request. Here, we are speaking about contextually any caller asking for a new instance of the service in
     * question. The requester in question will ideally be able to identify itself to this provider via
     * {@link io.helidon.inject.InjectionPointProvider#first(ContextualServiceQuery)} so that this
     * provider can properly service the "provide" request.
     *
     * @return true if the service provider provides per-request instances for each caller
     */
    default boolean isProvider() {
        return false;
    }

    /**
     * Service descriptor. The type is expected to be generated at compile time, and contains only statically known information.
     * As a result, methods on this type may provide different results than methods on the descriptor returned by this method,
     * as this type honors runtime state.
     *
     * @return descriptor of this service
     */
    ServiceInfo serviceInfo();

    /**
     * The current activation phase for this service provider.
     *
     * @return the activation phase
     */
    Phase currentActivationPhase();

    /**
     * The agent/instance to be used for binding this service provider to the injectable application that was code generated.
     *
     * @return the service provider that should be used for binding, or empty if this provider does not support binding
     * @see io.helidon.inject.service.ModuleComponent
     * @see io.helidon.inject.service.ServiceBinder
     * @see io.helidon.inject.ServiceProviderBindable
     */
    default Optional<ServiceProviderBindable<T>> serviceProviderBindable() {
        return Optional.empty();
    }

    @Override
    default TypeName serviceType() {
        return serviceInfo().serviceType();
    }

    @Override
    default double weight() {
        return serviceInfo().weight();
    }

    @Override
    default String runtimeId() {
        return serviceInfo().runtimeId();
    }

    @Override
    default Set<TypeName> contracts() {
        return serviceInfo().contracts();
    }

    @Override
    default List<Ip> dependencies() {
        return serviceInfo().dependencies();
    }

    @Override
    default Set<Qualifier> qualifiers() {
        return serviceInfo().qualifiers();
    }

    @Override
    default int runLevel() {
        return serviceInfo().runLevel();
    }

    @Override
    default Set<TypeName> scopes() {
        return serviceInfo().scopes();
    }

    @Override
    default TypeName infoType() {
        return serviceInfo().infoType();
    }

    @Override
    default boolean isAbstract() {
        return serviceInfo().isAbstract();
    }
}
