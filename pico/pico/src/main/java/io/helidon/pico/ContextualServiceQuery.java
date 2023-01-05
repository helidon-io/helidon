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

package io.helidon.pico;

import java.util.Objects;
import java.util.Optional;

import io.helidon.builder.Builder;

/**
 * Combines the {@link ServiceInfo} criteria along with the {@link InjectionPointInfo} context
 * that the query applies to.
 *
 * @see InjectionPointProvider
 */
@Builder
public interface ContextualServiceQuery {

    /**
     * The criteria to use for the lookup into {@link io.helidon.pico.Services}.
     *
     * @return the service info criteria
     */
    ServiceInfoCriteria serviceInfoCriteria();

    /**
     * Optionally, the injection point context this search applies to.
     *
     * @return the optional injection point context info
     */
    Optional<InjectionPointInfo> injectionPointInfo();

    /**
     * Set to true if there is an expectation that there is at least one match result from the search.
     *
     * @return true if it is expected there is at least a single match result
     */
    boolean expected();

    /**
     * Creates a contextual service query given the injection point info.
     *
     * @param ipInfo    the injection point info
     * @param expected  true if the query is expected to at least have a single match
     * @return the query
     */
    static ContextualServiceQuery create(
            InjectionPointInfo ipInfo,
            boolean expected) {
        Objects.requireNonNull(ipInfo);
        return DefaultContextualServiceQuery.builder()
                .expected(expected)
                .injectionPointInfo(ipInfo)
                .serviceInfoCriteria(ipInfo.dependencyToServiceInfo())
                .build();
    }

}
