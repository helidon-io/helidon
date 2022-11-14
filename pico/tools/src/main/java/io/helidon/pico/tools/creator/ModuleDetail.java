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

import java.util.Collection;

import io.helidon.pico.Module;
import io.helidon.pico.types.TypeName;

/**
 * The specifics for a single {@link Module} that was codegen'ed.
 *
 * @see io.helidon.pico.tools.creator.ActivatorCreatorResponse#getModuleDetail
 */
public interface ModuleDetail {

    /**
     * @return The name of the service provider activators for this module.
     */
    Collection<TypeName> getServiceProviderActivatorTypeNames();

    /**
     * @return The name of this module.
     */
    String getModuleName();

    /**
     * @return The fqn of the module class name.
     */
    TypeName getModuleTypeName();

    /**
     * @return The codegen body for the module.
     */
    String getModuleBody();

    /**
     * @return The Java 9+ module-info.java conrtents.
     */
    String getModuleInfoBody();

}
