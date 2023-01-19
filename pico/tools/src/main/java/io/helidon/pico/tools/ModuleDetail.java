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

import java.util.List;
import java.util.Optional;

import io.helidon.builder.Builder;
import io.helidon.pico.types.TypeName;

/**
 * The specifics for a single {@link io.helidon.pico.Module} that was codegen'ed.
 *
 * @see ActivatorCreatorResponse#moduleDetail
 */
@Builder
public interface ModuleDetail {

    /**
     * name of the service provider activators for this module.
     *
     * @return name of the service provider activators for this module
     */
    List<TypeName> serviceProviderActivatorTypeNames();

    /**
     * The name of this module.
     *
     * @return name of this module.
     */
    String moduleName();

    /**
     * The FQN of the module class name.
     *
     * @return The fqn of the module class name
     */
    TypeName moduleTypeName();

    /**
     * The codegen body for the module.
     *
     * @return body for the module
     */
    Optional<String> moduleBody();

    /**
     * The Java 9+ module-info.java contents.
     *
     * @return contents for module-info body
     */
    Optional<String> moduleInfoBody();

    /**
     * The descriptor cooresponding to any {@link #moduleInfoBody()}.
     *
     * @return descriptor creator
     */
    Optional<ModuleInfoDescriptor> descriptor();

}
