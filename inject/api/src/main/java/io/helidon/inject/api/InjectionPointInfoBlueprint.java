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

package io.helidon.inject.api;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.types.TypeName;

/**
 * Describes a receiver for injection - identifies who/what is requesting an injection that needs to be satisfied.
 */
@Prototype.Blueprint
interface InjectionPointInfoBlueprint extends ElementInfo, ElementInfoBlueprint {
    /**
     * Name of the field or argument we are injecting into.
     * Best effort, if cannot be found, an indexed approach may be used (such as {@code arg0}).
     *
     * @return name of the injection point field or argument
     */
    String ipName();

    /**
     * Type of the field or argument we are injecting into.
     *
     * @return type of the field or argument, including generic type declarations
     */
    TypeName ipType();

    /**
     * The identity (aka id) for this injection point. The id should be unique for the service type it is contained within.
     * <p>
     * This method will return the {@link #baseIdentity()} when {@link #elementOffset()} is null.  If not null
     * then the elemOffset is part of the returned identity.
     *
     * @return the unique identity
     */
    String id();

    /**
     * The base identifying name for this injection point. If the element represents a function, then the function arguments
     * are encoded in its base identity.
     *
     * @return the base identity of the element
     */
    String baseIdentity();

    /**
     * True if the injection point is of type {@link java.util.List}.
     *
     * @return true if list based receiver
     */
    @Option.DefaultBoolean(false)
    boolean listWrapped();

    /**
     * True if the injection point is of type {@link java.util.Optional}.
     *
     * @return true if optional based receiver
     */
    @Option.DefaultBoolean(false)
    boolean optionalWrapped();

    /**
     * True if the injection point is of type Provider (or Supplier).
     *
     * @return true if provider based receiver
     */
    @Option.DefaultBoolean(false)
    boolean providerWrapped();

    /**
     * The service info criteria/dependency this is dependent upon.
     *
     * @return the service info criteria we are dependent upon
     */
    ServiceInfoCriteria dependencyToServiceInfo();

}
