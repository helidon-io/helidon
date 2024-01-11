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

/**
 * API required to define services, and to compile the code generated sources for Helidon Inject.
 * This is the minimal set of types that must be available both for compilation and runtime (if there is at least one service).
 * <p>
 * Types for defining services:
 * <ul>
 *     <li>{@link io.helidon.inject.service.Injection} - contains annotations to declare a service</li>
 *     <li>{@link io.helidon.inject.service.ConfigDriven} - contains annotations to declare config beans driven by
 *              configuration (when used, Config would need to be on classpath)</li>
 *     <li>{@link io.helidon.inject.service.InjectionPointProvider} - to define a service that resolves injection
 *              points (or {@link io.helidon.inject.service.Lookup})</li>
 *     <li>{@link io.helidon.inject.service.ServicesProvider} - to define a service that provides other services at runtime</li>
 *     <li>{@link io.helidon.inject.service.Interception} - to declare interceptor trigger annotations, and to implement
 *              interceptor services</li>
 * </ul>
 * <p>
 * Types required by code generated sources:
 * <ul>
 *     <li>{@link io.helidon.inject.service.ServiceDescriptor} - generated metadata and factory for a service</li>
 *     <li>{@link io.helidon.inject.service.ModuleComponent} - generated module to bind services to registry</li>
 * </ul>
 * <p>
 * The rest of the types are used as parameters in methods of the types described above.
 */
package io.helidon.inject.service;
