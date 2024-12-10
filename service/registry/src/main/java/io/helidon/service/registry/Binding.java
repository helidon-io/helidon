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

import io.helidon.common.types.TypeName;

/**
 * A binding instance, if available at runtime, will be expected to provide a plan for all service provider's injection
 * points.
 * <p>
 * Implementations of this contract are normally code generated, although then can be programmatically written by the developer
 * for special cases.
 * <p>
 * Binding instances MUST NOT have injection points.
 */
@Service.Contract
public interface Binding {
    /**
     * Type name of this interface.
     */
    TypeName TYPE = TypeName.create(Binding.class);

    /**
     * Name of this application binding.
     *
     * @return binding name
     */
    String name();

    /**
     * Configure injection points and dependencies in this application.
     *
     * @param binder the binder used to register the service provider injection plans
     */
    void configure(DependencyPlanBinder binder);
}
