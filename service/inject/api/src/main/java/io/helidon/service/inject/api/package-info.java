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
 * API required to define services, and to compile the code generated sources for Helidon Inject,
 * with a core service registry implementation (replacement for {@link java.util.ServiceLoader}).
 * <p>
 * The following main entry points for declaring services are available:
 * <ul>
 *     <li>{@link io.helidon.service.registry.Service} - for core registry</li>
 *     <li>{@link io.helidon.service.inject.api.Injection} - for injection support</li>
 *     <li>{@link io.helidon.service.inject.api.ConfigDriven} - for config beans and config driven</li>
 *     <li>{@link io.helidon.service.inject.api.Interception} - for interception</li>
 * </ul>
 */
package io.helidon.service.inject.api;
