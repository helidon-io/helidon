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
import java.util.Set;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.TypeName;
import io.helidon.inject.api.DependenciesInfo;
import io.helidon.inject.api.Qualifier;

/**
 * Codegen request options applicable as part of the overall {@link ActivatorCreatorRequest}.
 *
 * @see ActivatorCreatorRequest
 */
@Prototype.Blueprint
interface ActivatorCreatorCodeGenBlueprint {

    /**
     * The default prefix for {@link #classPrefixName()}.
     */
    String DEFAULT_CLASS_PREFIX_NAME = "";

    /**
     * Optionally, for each service type also provide its parent (super class) service type mapping.
     *
     * @return the service type to parent (super class) service type mapping
     */
    Map<TypeName, TypeName> serviceTypeToParentServiceTypes();

    /**
     * The class hierarchy from Object down to and including this service type.
     *
     * @return the map of service type names to its class hierarchy
     */
    Map<TypeName, List<TypeName>> serviceTypeHierarchy();

    /**
     * Optionally, for each service, provide the generic declaration portion for the activator generic class name.
     *
     * @return the generic declaration portion for the activator generic class name
     */
    Map<TypeName, String> serviceTypeToActivatorGenericDecl();

    /**
     * The map of service type names to access level.
     *
     * @return the map of service type names to each respective access level
     */
    Map<TypeName, AccessModifier> serviceTypeAccessLevels();

    /**
     * The map of service type names to whether they are abstract. If not found then assume concrete.
     *
     * @return the map of service type names to whether they are abstract
     */
    Map<TypeName, Boolean> serviceTypeIsAbstractTypes();

    /**
     * The {@link io.helidon.inject.api.Contract}'s associated with each service type.
     *
     * @return the map of service type names to {@link io.helidon.inject.api.Contract}'s implemented
     */
    Map<TypeName, Set<TypeName>> serviceTypeContracts();

    /**
     * The {@link io.helidon.inject.api.ExternalContracts} associated with each service type.
     *
     * @return the map of service type names to {@link io.helidon.inject.api.ExternalContracts} implemented
     */
    Map<TypeName, Set<TypeName>> serviceTypeExternalContracts();

    /**
     * The injection point dependencies for each service type.
     *
     * @return the map of service type names to injection point dependencies info
     */
    Map<TypeName, DependenciesInfo> serviceTypeInjectionPointDependencies();

    /**
     * Default constructor override for each service type.
     *
     * @return map of service types to custom constructor code
     */
    Map<TypeName, String> serviceTypeDefaultConstructors();

    /**
     * The {@code PreDestroy} method name for each service type.
     *
     * @return the map of service type names to PreDestroy method names
     * @see io.helidon.inject.api.PreDestroyMethod
     */
    Map<TypeName, String> serviceTypePreDestroyMethodNames();

    /**
     * The {@code PostConstruct} method name for each service type.
     *
     * @return the map of service type names to PostConstruct method names
     * @see io.helidon.inject.api.PostConstructMethod
     */
    Map<TypeName, String> serviceTypePostConstructMethodNames();

    /**
     * The declared {@link io.helidon.common.Weighted} value for each service type.
     *
     * @return the map of service type names to declared weight
     */
    Map<TypeName, Double> serviceTypeWeights();

    /**
     * The declared {@link io.helidon.inject.api.RunLevel} value for each service type.
     *
     * @return the map of service type names to declared run level
     */
    Map<TypeName, Integer> serviceTypeRunLevels();

    /**
     * The declared {@code Scope} value for each service type.
     *
     * @return the map of service type names to declared scope name
     */
    Map<TypeName, Set<TypeName>> serviceTypeScopeNames();

    /**
     * The set of {@link jakarta.inject.Qualifier}'s for each service type.
     *
     * @return the map of service type names to qualifiers
     */
    Map<TypeName, Set<Qualifier>> serviceTypeQualifiers();

    /**
     * The set of type names that the service type acts as an "is provider" for (i.e., {@link jakarta.inject.Provider}).
     *
     * @return the map of service type names to "is provider" flag values
     */
    Map<TypeName, Set<TypeName>> serviceTypeToProviderForTypes();

    /**
     * The service type's interception plan.
     *
     * @return the map of service type names to the interception plan
     */
    Map<TypeName, InterceptionPlan> serviceTypeInterceptionPlan();

    /**
     * The extra source code that needs to be appended to the implementation.
     *
     * @return the map of service type names to the extra source code that should be added
     */
    Map<TypeName, List<String>> extraCodeGen();

    /**
     * The extra source code class comments that needs to be appended to the implementation.
     *
     * @return the map of service type names to the extra source code class comments that should be added
     */
    Map<TypeName, List<String>> extraClassComments();

    /**
     * The set of external modules used and/or required.
     *
     * @return the set of external modules used and/or required
     */
    Set<String> modulesRequired();

    /**
     * Typically populated as "test" if test scoped, otherwise left blank.
     *
     * @return production or test scope
     */
    @Option.Default(DEFAULT_CLASS_PREFIX_NAME)
    String classPrefixName();

    /**
     * Used in conjunction with {@link ActivatorCreatorConfigOptions#moduleCreated()}.
     * If a module is created and this set is
     * populated then this set will be used to represent all {@link io.helidon.inject.api.Activator} type names that should be code
     * generated for this {@link io.helidon.inject.api.ModuleComponent}.
     *
     * @return all module activator type names known for this given module being processed
     */
    Set<TypeName> allModuleActivatorTypeNames();

}
