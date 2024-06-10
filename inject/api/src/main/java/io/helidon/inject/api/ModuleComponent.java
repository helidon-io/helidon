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

package io.helidon.inject.api;

import java.util.Optional;

/**
 * Provides aggregation of services to the "containing" (jar) module.
 * <p>
 * Implementations of this contract are normally code generated, although then can be programmatically written by the developer
 * for special cases.
 * <p>
 * Note: instances of this type are not eligible for injection.
 *
 * @see Application
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
@Contract
public interface ModuleComponent extends OptionallyNamed {

    /**
     * Called by the provider implementation at bootstrapping time to bind all services / service providers to the
     * service registry.
     *
     * @param binder the binder used to register the services to the registry
     */
    void configure(ServiceBinder binder);

    @Override
    default Optional<String> named() {
        return Optional.empty();
    }

}
