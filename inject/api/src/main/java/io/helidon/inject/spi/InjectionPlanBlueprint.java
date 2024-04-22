/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

import java.util.List;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.inject.api.ServiceProvider;

/**
 * Represents the injection plan targeting a given {@link io.helidon.inject.api.ServiceProvider}.
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
@Prototype.Blueprint
interface InjectionPlanBlueprint {

    /**
     * The service provider this plan pertains to.
     *
     * @return the service provider this plan pertains to
     */
    ServiceProvider<?> serviceProvider();

    /**
     * The injection point info for this element, which will also include its identity information.
     *
     * @return the injection point info for this element
     */
    io.helidon.inject.api.InjectionPointInfo injectionPointInfo();

    /**
     * The list of service providers that are qualified to satisfy the given injection point for this service provider.
     *
     * @return the qualified service providers for this injection point
     */
    @Option.Singular
    List<ServiceProvider<?>> injectionPointQualifiedServiceProviders();

    /**
     * Flag indicating whether resolution occurred.
     *
     * @return true if resolution occurred
     */
    @ConfiguredOption("false")
    boolean wasResolved();

    /**
     * The resolved value, set only if {@link #wasResolved()}.
     *
     * @return any resolved value
     */
    Optional<Object> resolved();

}
