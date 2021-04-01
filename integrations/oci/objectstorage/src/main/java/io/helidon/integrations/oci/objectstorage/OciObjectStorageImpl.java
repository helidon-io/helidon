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

package io.helidon.integrations.oci.objectstorage;

import java.nio.channels.ReadableByteChannel;

import io.helidon.common.http.DataChunk;
import io.helidon.common.reactive.IoMulti;
import io.helidon.integrations.common.rest.ApiOptionalResponse;

class OciObjectStorageImpl implements OciObjectStorage {
    private final OciObjectStorageRx delegate;

    OciObjectStorageImpl(OciObjectStorageRx delegate) {
        this.delegate = delegate;
    }

    @Override
    public ApiOptionalResponse<GetObject.Response> getObject(GetObjectRx.Request request) {
        return delegate.getObject(request).await()
                .map(reactiveResponse -> GetObject.Response.create(reactiveResponse.bytePublisher()));
    }

    @Override
    public PutObject.Response putObject(PutObject.Request request, ReadableByteChannel channel) {
        return delegate.putObject(request, IoMulti.multiFromByteChannel(channel).map(DataChunk::create))
                .await();
    }

    @Override
    public DeleteObject.Response deleteObject(DeleteObject.Request request) {
        return delegate.deleteObject(request)
                .await();
    }

    @Override
    public RenameObject.Response renameObject(RenameObject.Request request) {
        return delegate.renameObject(request)
                .await();
    }
}
