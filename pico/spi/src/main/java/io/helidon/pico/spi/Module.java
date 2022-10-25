/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.pico.spi;

import io.helidon.pico.api.Contract;

/**
 * Provides aggregation of services to the "containing" (jar) module.
 * Modules can be provided explicitly by the developer, or automatically if using a pico apt processor during compile time.
 * <p>
 * Note: instances of this type are not eligible for injection.
 *
 * @see Application
 */
@Contract
public interface Module extends Named {

    /**
     * Called by the provider implementation at bootstrapping time to bind all services / service providers to the
     * service registry.
     *
     * @param binder the binder used to register the services to the registry
     */
    void configure(ServiceBinder binder);

}
