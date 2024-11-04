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

package io.helidon.service.inject.codegen;

/**
 * Described service type.
 * <p>
 * Core services (services defined for core service registry) can be only {@link #SERVICE} or {@link #SUPPLIER}.
 * <p>
 * This enum is duplicated in Inject API, as we do not want to have a common dependency.
 */
enum FactoryType {
    /**
     * This is just a descriptor that cannot instantiate anything.
     */
    NONE,
    /**
     * Direct implementation of a service.
     * <p>
     * This is the case when service does not implement any of the service provider interfaces, but it does
     * implement at least one contract.
     */
    SERVICE,
    /**
     * The service implements a {@link java.util.function.Supplier} of a contract.
     */
    SUPPLIER,
    /**
     * The service implements a provider of a list of contract instances.
     */
    SERVICES,
    /**
     * The service implements a provider that satisfies a specific injection point (either a single contract,
     * or a list of contract instances).
     */
    INJECTION_POINT,
    /**
     * The service implements a provider that is called for specific qualifiers (either a single contract,
     * or a list of contract instances).
     */
    QUALIFIED
}
