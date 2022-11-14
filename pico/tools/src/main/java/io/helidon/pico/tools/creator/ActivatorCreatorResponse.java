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

import java.util.Map;

import io.helidon.pico.types.TypeName;

/**
 * The result of calling {@link ActivatorCreator} assuming no errors are thrown.
 */
public interface ActivatorCreatorResponse extends GeneralCreatorResponse {

    /**
     * @return The configuration options that were applied.
     */
    ActivatorCreatorConfigOptions getConfigOptions();

    /**
     * @return The detailed information by service type provider generated.
     */
    @Override
    Map<TypeName, ActivatorCodeGenDetail> getServiceTypeDetails();

    /**
     * @return The interceptors that generated.
     */
    Map<TypeName, InterceptionPlan> getServiceTypeInterceptorPlans();

    /**
     * @return The module detail, if a module was created.
     */
    ModuleDetail getModuleDetail();

    /**
     * @return Set iff the application stub was requested to have been created.
     */
    TypeName getApplicationTypeName();

}
