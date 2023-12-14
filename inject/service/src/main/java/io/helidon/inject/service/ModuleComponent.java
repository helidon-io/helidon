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

package io.helidon.inject.service;

/**
 * Provides aggregation of services to the "containing" (jar) module.
 * <p>
 * Implementations of this contract are normally code generated, although then can be programmatically written by the developer
 * for special cases.
 * <p>
 * Note: instances of this type are not eligible for injection.
 */
@Injection.Contract
public interface ModuleComponent {

    /**
     * Called by the provider implementation at bootstrapping time to bind all service descriptors to the
     * service registry.
     *
     * @param binder the binder used to register the services to the registry
     */
    void configure(ServiceBinder binder);

    /**
     * This module name. If the module does not have a JPMS {@code module-info.java}, use {@code unnamed/package}.
     *
     * @return name of this module
     */
    String name();

}
