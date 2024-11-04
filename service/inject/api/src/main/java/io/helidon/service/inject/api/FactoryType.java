/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.service.inject.api;

/**
 * Described service factory type.
 * <p>
 * Core services (services defined for core service registry) can be only {@link #SERVICE} or {@link #SUPPLIER}.
 */
public enum FactoryType {
    /**
     * This service descriptor cannot provide an instance (such as service descriptors generated from interfaces,
     * where we provide instances as part of creating a scope).
     */
    NONE,
    /**
     * Direct implementation of a service.
     * <p>
     * This is the case when service does not implement any of the service factory interfaces, but it does
     * implement at least one contract.
     */
    SERVICE,
    /**
     * The service implements a {@link java.util.function.Supplier} of a contract.
     */
    SUPPLIER,
    /**
     * The service implements a {@link io.helidon.service.inject.api.Injection.ServicesFactory}.
     */
    SERVICES,
    /**
     * The service implements an {@link io.helidon.service.inject.api.Injection.InjectionPointFactory}.
     */
    INJECTION_POINT,
    /**
     * The service implements a {@link io.helidon.service.inject.api.Injection.QualifiedFactory}.
     */
    QUALIFIED
}
