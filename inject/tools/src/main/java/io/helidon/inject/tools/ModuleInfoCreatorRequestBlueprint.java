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

package io.helidon.inject.tools;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.types.TypeName;

/**
 * Used to represent the parameters that feed into the code generation of a module-info file specifically for Injection in that
 * it offers easy ability to add the {@link io.helidon.inject.api.ModuleComponent} as well as optionally the
 * {@link io.helidon.inject.api.Application}.
 */
@Prototype.Blueprint
interface ModuleInfoCreatorRequestBlueprint {

    /**
     * Optionally, the module name. If not provided then an attempt will be made to calculate the suggested name.
     *
     * @return module name
     */
    Optional<String> name();

    /**
     * The {@link io.helidon.inject.api.ModuleComponent} type name.
     *
     * @return module type name
     */
    TypeName moduleTypeName();

    /**
     * The {@link io.helidon.inject.api.Application} type name.
     *
     * @return application type name
     */
    Optional<TypeName> applicationTypeName();

    /**
     * Set to true if the {@link io.helidon.inject.api.ModuleComponent} should be created.
     *
     * @return true if the Module should be created
     */
    @Option.DefaultBoolean(true)
    boolean moduleCreated();

    /**
     * Set to true if the {@link io.helidon.inject.api.Application} should be created.
     *
     * @return true if the Application should be created
     */
    boolean applicationCreated();

    /**
     * The modules required list.
     *
     * @return modules required
     */
    List<String> modulesRequired();

    /**
     * The service type mapping to contracts for that service type.
     *
     * @return service type mapping to contracts
     */
    Map<TypeName, Set<TypeName>> contracts();

    /**
     * The service type mapping to external contracts for that service type.
     *
     * @return service type mapping to external contracts
     */
    Map<TypeName, Set<TypeName>> externalContracts();

    /**
     * Optionally, the path for where to access the module-info file.
     *
     * @return module info path
     */
    Optional<String> moduleInfoPath();

    /**
     * The class name prefix for the code generated class.
     *
     * @return class name prefix
     */
    String classPrefixName();

}
