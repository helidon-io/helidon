/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.pico.tools.creator;

import io.helidon.pico.Contract;
import io.helidon.pico.Activator;
import io.helidon.pico.ServiceProvider;
import io.helidon.pico.types.TypeName;

/**
 * Responsible for code-generating pico
 * {@link Activator}s and {@link ServiceProvider}s for service types found in your DI-enabled module.
 *
 * The typical scenario will have 1-SingletonServiceType:1-GeneratedPicoActivatorClassForThatService:1-ServiceProvider
 * representation in the {@link io.helidon.pico.Services} registry that can be lazily activated.
 *
 * Activators are only generated if your service is marked as a {@link jakarta.inject.Singleton} scoped service.
 *
 * All activators for your jar module are then aggregated and registered into a pico code-generated
 * {@link io.helidon.pico.Module}.
 */
@Contract
public interface ActivatorCreator {

    /**
     * Used during annotation processing in compile time to automatically generate {@link io.helidon.pico.Activator}s
     * and optionally an aggregating {@link io.helidon.pico.Module} for those activators.
     *
     * @param request the request for what to generate
     * @return the response result for the create operation
     */
    ActivatorCreatorResponse createModuleActivators(ActivatorCreatorRequest request);

    /**
     * Generates the would-be implementation type name that will be generated if
     * {@link #createModuleActivators(ActivatorCreatorRequest)} were to be called.
     *
     * @param activatorTypeName the service/activator type name of the developer provided service type.
     *
     * @return the code generated implementation type name
     */
    TypeName toActivatorImplTypeName(TypeName activatorTypeName);

}
