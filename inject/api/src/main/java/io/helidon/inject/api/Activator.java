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

/**
 * Activators are responsible for lifecycle creation and lazy activation of service providers. They are responsible for taking the
 * {@link ServiceProvider}'s manage service instance from {@link Phase#PENDING}
 * through {@link Phase#POST_CONSTRUCTING} (i.e., including any
 * {@link PostConstructMethod} invocations, etc.), and finally into the
 * {@link Phase#ACTIVE} phase.
 * <p>
 * Assumption:
 * <ol>
 *  <li>Each {@link ServiceProvider} managing its backing service will have an activator strategy conforming to the DI
 *  specification.</li>
 *  <li>Each services activation is expected to be non-blocking, but may in fact require deferred blocking activities to become
 *  fully ready for runtime operation.</li>
 * </ol>
 * Activation includes:
 * <ol>
 *  <li>Management of the service's {@link Phase}.</li>
 *  <li>Control over creation (i.e., invoke the constructor non-reflectively).</li>
 *  <li>Control over gathering the service requisite dependencies (ctor, field, setters) and optional activation of those.</li>
 *  <li>Invocation of any {@link PostConstructMethod}.</li>
 *  <li>Responsible to logging to the {@link ActivationLog} - see {@link InjectionServices#activationLog()}.</li>
 * </ol>
 *
 * @see DeActivator
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
public interface Activator {

    /**
     * Activate a managed service/provider.
     *
     * @param activationRequest activation request
     * @return the result of the activation
     */
    ActivationResult activate(ActivationRequest activationRequest);

}
