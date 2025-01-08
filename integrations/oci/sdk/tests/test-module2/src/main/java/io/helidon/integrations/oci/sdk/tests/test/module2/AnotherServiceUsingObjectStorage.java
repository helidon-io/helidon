/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

package io.helidon.integrations.oci.sdk.tests.test.module2;

import java.util.Objects;
import java.util.function.Supplier;

import io.helidon.service.registry.Service;

import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.objectstorage.requests.GetNamespaceRequest;
import com.oracle.bmc.objectstorage.responses.GetNamespaceResponse;

@Service.Singleton
class AnotherServiceUsingObjectStorage {

    @Service.Inject
    ObjectStorage objStorageClient;
    Supplier<ObjectStorage> standbyObjStorageClientProvider;

    @Service.Inject
    void setStandbyObjectStorageProvider(@Service.Named("StandbyProfile") Supplier<ObjectStorage> standbyObjStorageClientProvider) {
        this.standbyObjStorageClientProvider = Objects.requireNonNull(standbyObjStorageClientProvider);
    }

    String namespaceName() {
        GetNamespaceResponse namespaceResponse = objStorageClient
                .getNamespace(GetNamespaceRequest.builder().build());
        return namespaceResponse.getValue();
    }

    String namespaceNameOfStandby() {
        GetNamespaceResponse namespaceResponse = standbyObjStorageClientProvider.get()
                .getNamespace(GetNamespaceRequest.builder().build());
        return namespaceResponse.getValue();
    }

}
