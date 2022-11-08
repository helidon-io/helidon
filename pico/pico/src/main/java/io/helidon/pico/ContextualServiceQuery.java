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

package io.helidon.pico;

import io.helidon.common.LazyValue;
import io.helidon.pico.builder.Builder;

/**
 * Combines the {@link io.helidon.pico.ServiceInfo} criteria along with the {@link io.helidon.pico.InjectionPointInfo} context
 * that the query applies to.
 *
 * @see io.helidon.pico.InjectionPointProvider
 */
@Builder
public interface ContextualServiceQuery {

    /**
     * Denotes a match to any (default) service, but required to be matched to at least one.
     */
    LazyValue<ContextualServiceQuery> EMPTY_BUT_REQUIRED = LazyValue.create(() -> DefaultContextualServiceQuery.builder()
            .serviceInfo(ServiceInfo.EMPTY_CRITERIA.get())
            .expected(true)
            .build());

    /**
     * The criteria to use for the lookup into {@link io.helidon.pico.Services}.
     *
     * @return the service info criteria
     */
    ServiceInfo serviceInfo();

    /**
     * The injection point context this search applies to.
     *
     * @return the injection point context info
     */
    InjectionPointInfo ipInfo();

    /**
     * Set to true if there is an expectation that there is at least one match result from the search.
     *
     * @return true if it is expected there is at least a single match result
     */
    boolean expected();

}
