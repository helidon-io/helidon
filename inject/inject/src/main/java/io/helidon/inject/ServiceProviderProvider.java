/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.inject;

import java.util.List;
import java.util.Map;

import io.helidon.inject.service.Lookup;

/**
 * Instances of these provide lists and maps of {@link RegistryServiceProvider}s.
 */
public interface ServiceProviderProvider {

    /**
     * Returns a list of all matching service providers, potentially including itself in the result.
     *
     * @param criteria           the injection point criteria that must match
     * @param wantThis           if this instance matches criteria, do we want to return this instance as part of the result
     * @param thisAlreadyMatches an optimization that signals to the implementation that this instance has already
     *                           matched using the standard service info matching checks
     * @return the list of service providers matching
     */
    List<? extends RegistryServiceProvider<?>> serviceProviders(Lookup criteria,
                                                                boolean wantThis,
                                                                boolean thisAlreadyMatches);

    /**
     * This method will only apply to the managed instances being provided, not to itself as in the case for
     * {@link #serviceProviders(Lookup, boolean, boolean)}.
     *
     * @param criteria the injection point criteria that must match
     * @return the map of managed service providers matching the criteria, identified by its key/context
     */
    Map<String, ? extends RegistryServiceProvider<?>> managedServiceProviders(Lookup criteria);

}
