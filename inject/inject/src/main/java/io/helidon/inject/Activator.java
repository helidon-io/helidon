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

package io.helidon.inject;

/**
 * Activators are responsible for lifecycle creation and lazy activation of service providers.
 * They are responsible for taking the
 * {@link RegistryServiceProvider}'s managed service instance from {@link io.helidon.inject.Phase#PENDING}
 * through {@link io.helidon.inject.Phase#POST_CONSTRUCTING} (i.e., including any
 * {@link io.helidon.inject.service.Injection.PostConstruct} invocations, etc.), and finally into the
 * {@link io.helidon.inject.Phase#ACTIVE} phase.
 * <p>
 * Assumption:
 * <ol>
 *  <li>Each {@link RegistryServiceProvider} managing its backing service will have an activator strategy conforming
 *  to the DI
 *  specification.</li>
 * </ol>
 * Activation includes:
 * <ol>
 *  <li>Management of the service's {@link io.helidon.inject.Phase}.</li>
 *  <li>Control over creation (i.e., invoke the constructor non-reflectively).</li>
 *  <li>Control over gathering the service requisite dependencies (ctor, field, setters) and optional activation of those.</li>
 *  <li>Invocation of any {@link io.helidon.inject.service.Injection.PostConstruct} method.</li>
 * </ol>
 *
 * The activator also supports the inverse process of deactivation, where any
 * {@link io.helidon.inject.service.Injection.PreDestroy}
 * methods may be called, and which moves the service to a terminal {@link io.helidon.inject.Phase#DESTROYED phase}.
 *
 * @param <T> type of the service provided by the activated service provider
 */
public interface Activator<T> {

    /**
     * Activate a managed service/provider.
     *
     * @param activationRequest activation request
     * @return the result of the activation
     */
    ActivationResult activate(ActivationRequest activationRequest);

    /**
     * Deactivate a managed service. This will trigger any {@link io.helidon.inject.service.Injection.PreDestroy} method on the
     * underlying service type instance. The service will read terminal {@link io.helidon.inject.Phase#DESTROYED}
     * phase, regardless of its activation status.
     *
     * @param request deactivation request
     * @return the result
     */
    ActivationResult deactivate(DeActivationRequest request);

    /**
     * Service provider managed by this activator.
     *
     * @return service provider
     */
    RegistryServiceProvider<T> serviceProvider();
}
