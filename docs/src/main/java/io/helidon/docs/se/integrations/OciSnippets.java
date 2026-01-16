/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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
package io.helidon.docs.se.integrations;

import java.io.IOException;

import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.service.registry.Services;

import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.objectstorage.ObjectStorageClient;

@SuppressWarnings("ALL")
class OciSnippets {

    void snippet_1() throws IOException {
        // tag::snippet_1[]
        ServiceRegistryManager registryManager = ServiceRegistryManager.create();
        ServiceRegistry registry = registryManager.registry();
        BasicAuthenticationDetailsProvider authProvider = registry.get(BasicAuthenticationDetailsProvider.class);
        // end::snippet_1[]
    }

    void snippet_2() throws IOException {
        // tag::snippet_2[]
        BasicAuthenticationDetailsProvider authProvider = Services.get(BasicAuthenticationDetailsProvider.class);
        ObjectStorage objectStorageClient = ObjectStorageClient.builder().build(authProvider);
        // end::snippet_2[]
    }
}
