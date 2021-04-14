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

package io.helidon.examples.integrations.oci.objecstorage.reactive;

import java.util.Optional;
import java.util.OptionalLong;

import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Http;
import io.helidon.integrations.oci.objectstorage.DeleteObject;
import io.helidon.integrations.oci.objectstorage.GetObjectRx;
import io.helidon.integrations.oci.objectstorage.OciObjectStorageRx;
import io.helidon.integrations.oci.objectstorage.PutObject;
import io.helidon.integrations.oci.objectstorage.RenameObject;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

class ObjectStorageService implements Service {
    private final OciObjectStorageRx objectStorage;
    private final String bucketName;

    ObjectStorageService(OciObjectStorageRx objectStorage, String bucketName) {
        this.objectStorage = objectStorage;
        this.bucketName = bucketName;
    }

    @Override
    public void update(Routing.Rules rules) {
        rules.get("/file/{file-name}", this::download)
                .post("/file/{file-name}", this::upload)
                .delete("/file/{file-name}", this::delete)
                .get("/rename/{old-name}/{new-name}", this::rename);
    }

    private void delete(ServerRequest req, ServerResponse res) {
        String objectName = req.path().param("file-name");

        objectStorage.deleteObject(DeleteObject.Request.builder()
                                           .bucket(bucketName)
                                           .objectName(objectName))
                .forSingle(response -> res.status(response.status()).send())
                .exceptionally(res::send);
    }

    private void rename(ServerRequest req, ServerResponse res) {
        String oldName = req.path().param("old-name");
        String newName = req.path().param("new-name");

        objectStorage.renameObject(RenameObject.Request.builder()
                                           .bucket(bucketName)
                                           .objectName(oldName)
                                           .newObjectName(newName))
                .forSingle(it -> res.send("Renamed to " + newName))
                .exceptionally(res::send);
    }

    private void upload(ServerRequest req, ServerResponse res) {
        OptionalLong contentLength = req.headers().contentLength();
        if (contentLength.isEmpty()) {
            req.content().forEach(DataChunk::release);
            res.status(Http.Status.BAD_REQUEST_400).send("Content length must be defined");
            return;
        }

        String objectName = req.path().param("file-name");

        PutObject.Request request = PutObject.Request.builder()
                .objectName(objectName)
                .bucket(bucketName)
                .contentLength(contentLength.getAsLong());

        req.headers().contentType().ifPresent(request::requestMediaType);

        objectStorage.putObject(request,
                                req.content())
                .forSingle(response -> res.send(response.requestId()))
                .exceptionally(res::send);
    }

    private void download(ServerRequest req, ServerResponse res) {
        String objectName = req.path().param("file-name");

        objectStorage.getObject(GetObjectRx.Request.builder()
                                        .bucket(bucketName)
                                        .objectName(objectName))
                .forSingle(apiResponse -> {
                    Optional<GetObjectRx.Response> entity = apiResponse.entity();
                    if (entity.isEmpty()) {
                        res.status(Http.Status.NOT_FOUND_404).send();
                    } else {
                        GetObjectRx.Response response = entity.get();
                        // copy the content length header to response
                        apiResponse.headers()
                                .first(Http.Header.CONTENT_LENGTH)
                                .ifPresent(res.headers()::add);
                        res.send(response.publisher());
                    }
                })
                .exceptionally(res::send);
    }
}
