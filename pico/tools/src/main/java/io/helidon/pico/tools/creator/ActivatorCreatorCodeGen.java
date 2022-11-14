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

import java.util.List;
import java.util.Map;
import java.util.Set;

import io.helidon.pico.Contract;
import io.helidon.pico.ExternalContracts;
import io.helidon.pico.RunLevel;
import io.helidon.pico.InjectionPointInfo;
import io.helidon.pico.PostConstructMethod;
import io.helidon.pico.PreDestroyMethod;
import io.helidon.pico.QualifierAndValue;
import io.helidon.pico.ServiceProvider;
import io.helidon.pico.spi.ext.Dependencies;
import io.helidon.pico.types.TypeName;

/**
 * Codegen request options applicable for {@link ActivatorCreatorRequest}.
 *
 * @see ActivatorCreatorRequest
 */
public interface ActivatorCreatorCodeGen {

    /**
     * @return Optionally, for each service type also provide its parent (super class) service type mapping.
     */
    Map<TypeName, TypeName> getServiceTypeToParentServiceTypes();

    /**
     * @return Optionally, for each service, provide the generic declaration portion for the activator generic class name.
     */
    Map<TypeName, String> getServiceTypeToActivatorGenericDecl();

    /**
     * @return the map of service type names to each respective access level.
     */
    Map<TypeName, InjectionPointInfo.Access> getServiceTypeAccessLevels();

    /**
     * @return the map of service type names to whether they are abstract. If not found then assume concrete.
     */
    Map<TypeName, Boolean> getServiceTypeIsAbstractTypes();

    /**
     * @return The {@link Contract}s associated with each service type.
     */
    Map<TypeName, Set<TypeName>> getServiceTypeContracts();

    /**
     * @return The {@link ExternalContracts} associated with each service type.
     */
    Map<TypeName, Set<TypeName>> getServiceTypeExternalContracts();

    /**
     * @return The injection point dependencies for each service type.
     */
    Map<TypeName, Dependencies> getServiceTypeInjectionPointDependencies();

    /**
     * @return The PreDestroy name for each service type.
     * @see PreDestroyMethod
     */
    Map<TypeName, String> getServiceTypePreDestroyMethodNames();

    /**
     * @return The {@link PostConstructMethod} name for each service type.
     * @see PostConstructMethod
     */
    Map<TypeName, String> getServiceTypePostConstructMethodNames();

    /**
     * @return The {@link io.helidon.common.Weighted} name for each service type.
     */
    Map<TypeName, Double> getServiceTypeWeightedPriorities();

    /**
     * @return The {@link RunLevel} name for each service type.
     */
    Map<TypeName, Integer> getServiceTypeRunLevels();

    /**
     * @return The Scope type name for each service type.
     */
    Map<TypeName, Set<String>> getServiceTypeScopeNames();

    /**
     * @return The set of {@link jakarta.inject.Qualifier}s for each service type.
     */
    Map<TypeName, Set<QualifierAndValue>> getServiceTypeQualifiers();

    /**
     * @return The {@link ServiceProvider#isProvider()} value for each service type.
     */
    Map<TypeName, Set<TypeName>> getServiceTypeToProviderForTypes();

    /**
     * @return The class hierarchy from Object down to and including this service type.
     */
    Map<TypeName, List<TypeName>> getServiceTypeHierarchy();

    /**
     * @return The service type's that are intercepted.
     */
    Map<TypeName, InterceptionPlan> getServiceTypeInterceptionPlan();

    /**
     * @return The extra source code that needs to be appended to the implementation.
     */
    Map<TypeName, List<String>> getExtraCodeGen();

    /**
     * @return The set of external modules used/required.
     */
    Set<String> getModulesRequired();

    /**
     * @return Populated as "test" if test scoped.
     */
    String getClassPrefixName();

}
