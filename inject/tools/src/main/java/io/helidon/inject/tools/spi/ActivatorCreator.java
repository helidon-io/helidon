/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.inject.tools.spi;

import io.helidon.common.types.TypeName;
import io.helidon.inject.api.Activator;
import io.helidon.inject.api.Contract;
import io.helidon.inject.api.ModuleComponent;
import io.helidon.inject.api.ServiceProvider;
import io.helidon.inject.api.Services;
import io.helidon.inject.tools.ActivatorCreatorProvider;
import io.helidon.inject.tools.ActivatorCreatorRequest;
import io.helidon.inject.tools.ActivatorCreatorResponse;
import io.helidon.inject.tools.CodeGenInterceptorRequest;
import io.helidon.inject.tools.InterceptorCreatorResponse;

/**
 * Implementors of this contract are responsible for code-generating the
 * {@link Activator}s and {@link ServiceProvider}s for service types found in your
 * DI-enabled
 * module.
 * <p>
 * The typical scenario will have 1-SingletonServiceType:1-GeneratedInjectionActivatorClassForThatService:1-ServiceProvider
 * representation in the {@link Services} registry that can be lazily activated.
 * <p>
 * Activators are only generated if your service is marked as a {@code jakarta.inject.Singleton} scoped service.
 * <p>
 * All activators for your jar module are then aggregated and registered into a code-generated
 * {@link ModuleComponent} class.
 *
 * @see ActivatorCreatorProvider
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
@Contract
public interface ActivatorCreator {

    /**
     * Used during annotation processing in compile time to automatically generate {@link Activator}'s
     * and optionally an aggregating {@link ModuleComponent} for those activators.
     *
     * @param request the request for what to generate
     * @return the response result for the create operation
     */
    ActivatorCreatorResponse createModuleActivators(ActivatorCreatorRequest request);

    /**
     * Generates just the interceptors.
     *
     * @param request the request for what to generate
     * @return the response result for the create operation
     */
    InterceptorCreatorResponse codegenInterceptors(CodeGenInterceptorRequest request);

    /**
     * Generates the would-be implementation type name that will be generated if
     * {@link #createModuleActivators(ActivatorCreatorRequest)} were to be called on this creator.
     *
     * @param activatorTypeName the service/activator type name of the developer provided service type.
     *
     * @return the code generated implementation type name that would be code generated
     */
    TypeName toActivatorImplTypeName(TypeName activatorTypeName);

}
