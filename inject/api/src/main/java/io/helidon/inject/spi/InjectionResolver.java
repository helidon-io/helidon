/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

package io.helidon.inject.spi;

import java.util.Optional;

import io.helidon.inject.api.InjectionPointInfo;
import io.helidon.inject.api.InjectionServices;
import io.helidon.inject.api.ServiceProvider;

/**
 * Implementors of this contract can assist with resolving injection points.
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
public interface InjectionResolver {

    /**
     * Attempts to resolve the injection point info for a given service provider.
     * <p>
     * There are two modes that injection resolvers run through.
     * Phase 1 (resolveIps=false) is during the time when the injection plan is being formulated. This is the time we need
     * to identify which {@link ServiceProvider} instances qualify.
     * Phase 2 (resolveIps=true) is during actual resolution, and typically comes during the service activation lifecycle.
     *
     * @param ipInfo            the injection point being resolved
     * @param injectionServices the services registry
     * @param serviceProvider   the service provider this pertains to
     * @param resolveIps        flag indicating whether injection points should be resolved
     * @return the resolution for the plan or the injection point, or empty if unable to resolve the injection point context
     */
    Optional<Object> resolve(InjectionPointInfo ipInfo,
                             InjectionServices injectionServices,
                             ServiceProvider<?> serviceProvider,
                             boolean resolveIps);

}
