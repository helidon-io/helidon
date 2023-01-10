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

package io.helidon.pico.services;

import java.util.List;

import io.helidon.builder.Builder;
import io.helidon.builder.Singular;
import io.helidon.pico.spi.BasicInjectionPlan;

/**
 * The injection plan for a given service provider and element belonging to that service provider. This plan can be created during
 * compile-time, and then just loaded from the {@link io.helidon.pico.Application} during Pico bootstrap initialization, or it can
 * be produced during the same startup processing sequence if the Application was not found, or if it was not permitted to be
 * loaded.
 */
@Builder
public interface InjectionPlan extends BasicInjectionPlan {

    /**
     * The list of services/providers that are unqualified to satisfy the given injection point but were considered.
     *
     * @return the unqualified services/providers for this injection point
     */
    @Singular
    List<?> unqualifiedProviders();

}
