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

package io.helidon.inject.service;

import java.util.Optional;

import io.helidon.builder.api.Prototype;

/**
 * Combines the {@link Lookup} criteria along with
 * the {@link io.helidon.inject.service.Ip} context
 * that the query applies to.
 *
 * @see InjectionPointProvider
 */
@Prototype.Blueprint
@Prototype.CustomMethods(ContextualLookupSupport.CustomMethods.class)
interface ContextualLookupBlueprint extends LookupBlueprint {
    /**
     * Optionally, the injection point context this search applies to.
     *
     * @return the optional injection point context info
     */
    Optional<Ip> injectionPoint();
}
