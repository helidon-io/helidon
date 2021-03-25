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

import java.util.Optional;
import java.util.concurrent.Flow;

import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Http;
import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;
import io.helidon.integrations.common.rest.ApiOptionalResponse;
import io.helidon.integrations.oci.connect.OciApiException;
import io.helidon.integrations.oci.connect.OciRestApi;

class OciObjectStorageImpl implements OciObjectStorage {
    private final OciRestApi restApi;
    private final Optional<String> defaultNamespace;

    OciObjectStorageImpl(Builder builder) {
        restApi = builder.restApi();
        defaultNamespace = builder.namespace();
    }

    @Override
    public Single<ApiOptionalResponse<GetObject.Response>> getObject(GetObject.Request request) {
        String namespace = namespace(request);
        String apiPath = "/n/" + namespace + "/b/" + request.bucket() + "/o/" + request.objectName();

        return restApi
                .getPublisher(apiPath, request, ApiOptionalResponse.<Multi<DataChunk>, GetObject.Response>apiResponseBuilder()
                        .entityProcessor(GetObject.Response::create));
    }

    @Override
    public Single<PutObject.Response> putObject(PutObject.Request request, Flow.Publisher<DataChunk> publisher) {
        String namespace = namespace(request);
        String apiPath = "/n/" + namespace + "/b/" + request.bucket() + "/o/" + request.objectName();

        request.addHeader("content-length", String.valueOf(request.contentLength()));

        return restApi.invokeBytesRequest(Http.Method.PUT, apiPath, request, publisher, PutObject.Response.builder());
    }

    @Override
    public Single<DeleteObject.Response> deleteObject(DeleteObject.Request request) {
        String namespace = namespace(request);
        String apiPath = "/n/" + namespace + "/b/" + request.bucket() + "/o/" + request.objectName();

        return restApi.delete(apiPath, request, DeleteObject.Response.builder());
    }

    @Override
    public Single<RenameObject.Response> renameObject(RenameObject.Request request) {
        String namespace = namespace(request);
        String apiPath = "/n/" + namespace + "/b/" + request.bucket() + "/actions/renameObject";

        return restApi.post(apiPath, request, RenameObject.Response.builder());
    }

    private String namespace(ObjectRequest<?> request) {
        return request.namespace()
                .or(() -> defaultNamespace)
                .orElseThrow(() -> new OciApiException("Namespace must be defined for Object Storage requests either "
                                                               + "in configuration of Object Storage, or on request."));
    }
}
