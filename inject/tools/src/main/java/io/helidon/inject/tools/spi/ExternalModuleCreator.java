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

import io.helidon.inject.api.Contract;
import io.helidon.inject.tools.ActivatorCreatorRequest;
import io.helidon.inject.tools.ExternalModuleCreatorRequest;
import io.helidon.inject.tools.ExternalModuleCreatorResponse;

/**
 * Implementors are responsible for creating an {@link ActivatorCreatorRequest} that can be then passed
 * to the
 * {@link ActivatorCreator} based upon the scanning and reflective introspection of a set of classes
 * found in an external
 * jar module.
 * This involves a two-step process of first preparing to create using
 * {@link #prepareToCreateExternalModule(ExternalModuleCreatorRequest)}, followed by taking the response
 * and proceeding
 * to call
 * {@link ActivatorCreator#createModuleActivators(ActivatorCreatorRequest)}.
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
@Contract
public interface ExternalModuleCreator {

    /**
     * Prepares the activator and module creation by reflectively scanning and analyzing the context of the request
     * to build a model payload that can then be pipelined to the activator creator.
     *
     * @param request the request
     * @return the response
     */
    ExternalModuleCreatorResponse prepareToCreateExternalModule(ExternalModuleCreatorRequest request);

}
