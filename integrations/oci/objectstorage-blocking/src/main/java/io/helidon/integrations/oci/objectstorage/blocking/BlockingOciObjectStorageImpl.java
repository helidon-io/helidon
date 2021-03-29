/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.integrations.oci.objectstorage.blocking;

import java.nio.channels.ReadableByteChannel;

import io.helidon.common.http.DataChunk;
import io.helidon.common.reactive.IoMulti;
import io.helidon.config.Config;
import io.helidon.integrations.common.rest.ApiOptionalResponse;
import io.helidon.integrations.oci.connect.OciRestApi;
import io.helidon.integrations.oci.objectstorage.DeleteObject;
import io.helidon.integrations.oci.objectstorage.GetObject;
import io.helidon.integrations.oci.objectstorage.PutObject;
import io.helidon.integrations.oci.objectstorage.RenameObject;

class BlockingOciObjectStorageImpl implements OciObjectStorage {
    private final io.helidon.integrations.oci.objectstorage.OciObjectStorage reactiveStorage;

    private BlockingOciObjectStorageImpl(io.helidon.integrations.oci.objectstorage.OciObjectStorage reactiveStorage) {
        this.reactiveStorage = reactiveStorage;
    }

    static OciObjectStorage create(OciRestApi restApi, Config ociConfig) {
        io.helidon.integrations.oci.objectstorage.OciObjectStorage reactiveStorage =
                io.helidon.integrations.oci.objectstorage.OciObjectStorage
                .builder()
                .restApi(restApi)
                .config(ociConfig)
                .build();

        return new BlockingOciObjectStorageImpl(reactiveStorage);
    }

    @Override
    public ApiOptionalResponse<BlockingGetObject.Response> getObject(GetObject.Request request) {
        return reactiveStorage.getObject(request).await()
                .map(reactiveResponse -> BlockingGetObject.Response.create(reactiveResponse.bytePublisher()));
    }

    @Override
    public PutObject.Response putObject(PutObject.Request request, ReadableByteChannel channel) {
        return reactiveStorage.putObject(request, IoMulti.multiFromByteChannel(channel).map(DataChunk::create))
                .await();
    }

    @Override
    public DeleteObject.Response deleteObject(DeleteObject.Request request) {
        return reactiveStorage.deleteObject(request)
                .await();
    }

    @Override
    public RenameObject.Response renameObject(RenameObject.Request request) {
        return reactiveStorage.renameObject(request)
                .await();
    }
}
