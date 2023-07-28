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

package io.helidon.nima.http2.webclient;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.CompletableFuture;

import io.helidon.common.GenericType;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.http.ClientRequestHeaders;
import io.helidon.common.http.Http;
import io.helidon.nima.http.media.EntityWriter;
import io.helidon.nima.http2.Http2Headers;
import io.helidon.nima.webclient.api.ClientUri;
import io.helidon.nima.webclient.api.HttpClientConfig;
import io.helidon.nima.webclient.api.WebClient;
import io.helidon.nima.webclient.api.WebClientServiceRequest;
import io.helidon.nima.webclient.api.WebClientServiceResponse;

class Http2CallEntityChain extends Http2CallChainBase {
    private final CompletableFuture<WebClientServiceRequest> whenSent;
    private final Object entity;

    Http2CallEntityChain(WebClient webClient,
                         HttpClientConfig clientConfig,
                         Http2ClientProtocolConfig protocolConfig,
                         Http2ClientRequestImpl request,
                         CompletableFuture<WebClientServiceRequest> whenSent,
                         CompletableFuture<WebClientServiceResponse> whenComplete,
                         Object entity) {
        super(webClient, clientConfig, protocolConfig, request, whenComplete, it -> it.submit(entity));
        this.whenSent = whenSent;
        this.entity = entity;
    }

    @Override
    protected WebClientServiceResponse doProceed(WebClientServiceRequest serviceRequest,
                                                 ClientRequestHeaders headers,
                                                 Http2ClientStream stream) {

        byte[] entityBytes;
        if (entity == BufferData.EMPTY_BYTES) {
            entityBytes = BufferData.EMPTY_BYTES;
        } else {
            entityBytes = entityBytes(entity, headers);
        }

        headers.set(Http.Header.create(Http.Header.CONTENT_LENGTH, entityBytes.length));

        ClientUri uri = serviceRequest.uri();

        Http2Headers http2Headers = prepareHeaders(serviceRequest.method(), headers, uri);

        stream.write(http2Headers, entityBytes.length == 0);
        stream.flowControl().inbound().incrementWindowSize(clientRequest().requestPrefetch());
        whenSent.complete(serviceRequest);

        if (entityBytes.length != 0) {
            stream.writeData(BufferData.create(entityBytes), true);
        }

        return readResponse(serviceRequest, stream);
    }

    private byte[] entityBytes(Object entity, ClientRequestHeaders headers) {
        if (entity instanceof byte[] bytes) {
            return bytes;
        }

        GenericType<Object> genericType = GenericType.create(entity);
        EntityWriter<Object> writer = clientConfig().mediaContext().writer(genericType, headers);

        // This uses an in-memory buffer, which would cause damage for writing big objects (such as Path)
        // we have a follow-up issue to make sure this is fixed
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        writer.write(genericType, entity, bos, headers);
        return bos.toByteArray();
    }
}
