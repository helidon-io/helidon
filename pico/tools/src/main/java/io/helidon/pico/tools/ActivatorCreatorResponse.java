/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.pico.tools;

import java.util.Map;
import java.util.Optional;

import io.helidon.builder.Builder;
import io.helidon.builder.Singular;
import io.helidon.common.types.TypeName;

/**
 * The result of calling {@link ActivatorCreator} assuming no errors are thrown.
 */
@Builder
public interface ActivatorCreatorResponse extends GeneralCreatorResponse {

    /**
     * The configuration options that were applied.
     *
     * @return config options
     */
    ActivatorCreatorConfigOptions getConfigOptions();

    /**
     * return The interceptors that were generated.
     *
     * @return interceptors generated
     */
    @Singular
    Map<TypeName, InterceptionPlan> serviceTypeInterceptorPlans();

    /**
     * The module-info detail, if a module was created.
     *
     * @return any module-info detail created
     */
    Optional<ModuleDetail> moduleDetail();

    /**
     * Set if the application stub was requested to have been created.
     *
     * @return the application name that was created.
     */
    Optional<TypeName> applicationTypeName();

}
