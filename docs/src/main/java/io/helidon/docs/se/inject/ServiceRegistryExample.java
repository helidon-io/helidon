/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.docs.se.inject;

import io.helidon.service.registry.ServiceRegistryManager;

class ServiceRegistryExample {

    public void programmatic() {
        // tag::snippet_1[]
        // create an instance of a registry manager - can be configured and shut down
        var registryManager = ServiceRegistryManager.create();
        // get the associated service registry
        var registry = registryManager.registry();
        // end::snippet_1[]
    }

    public void programmatic2() {
        // tag::snippet_2[]
        // create an instance of a registry manager - can be configured and shut down
        var registryManager = ServiceRegistryManager.create();
        // Your desired logic with ServiceRegistry

        // Once ServiceRegistryManager is no longer needed, it needs to be closed
        registryManager.shutdown();
        // end::snippet_2[]
    }

}
