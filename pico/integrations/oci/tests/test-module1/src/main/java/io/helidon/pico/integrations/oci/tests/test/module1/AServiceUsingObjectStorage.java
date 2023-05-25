/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.pico.integrations.oci.tests.test.module1;

import java.util.Objects;

import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.objectstorage.requests.GetNamespaceRequest;
import com.oracle.bmc.objectstorage.responses.GetNamespaceResponse;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

@Singleton
class AServiceUsingObjectStorage {

    private final ObjectStorage objStorageClient;
    private final Provider<ObjectStorage> standbyObjStorageClientProvider;

    @Inject
    AServiceUsingObjectStorage(ObjectStorage objStorage,
                               @Named("StandbyProfile") Provider<ObjectStorage> standbyObjStorageProvider) {
        this.objStorageClient = Objects.requireNonNull(objStorage);
        this.standbyObjStorageClientProvider = Objects.requireNonNull(standbyObjStorageProvider);
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
