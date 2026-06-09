/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

package io.helidon.webclient.http2;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.CompletableFuture;

import io.helidon.common.GenericType;
import io.helidon.common.buffers.BufferData;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.media.EntityWriter;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;

class Http2CallEntityChain extends Http2CallChainBase {
    private final CompletableFuture<WebClientServiceRequest> whenSent;
    private final Object entity;
    private boolean hasRequestEntity;
    private Object requestEntity;

    Http2CallEntityChain(Http2ClientImpl http2Client,
                         Http2ClientRequestImpl request,
                         CompletableFuture<WebClientServiceRequest> whenSent,
                         CompletableFuture<WebClientServiceResponse> whenComplete,
                         Object entity) {
        super(http2Client, request, whenComplete, it -> it.submit(entity));
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
        // Keep the serialized request body available for a possible 307/308 replay decision.
        requestEntity = entityBytes;

        if (!clientRequest().outputStreamRedirect()) {
            headers.set(HeaderValues.create(HeaderNames.CONTENT_LENGTH, entityBytes.length));
        }
        hasRequestEntity = entityBytes.length > 0;

        ClientUri uri = serviceRequest.uri();

        Http2Headers http2Headers = prepareHeaders(serviceRequest.method(), headers, uri);

        stream.writeHeaders(http2Headers, !clientRequest().outputStreamRedirect() && entityBytes.length == 0);
        stream.flowControl().inbound().incrementWindowSize(clientRequest().requestPrefetch());
        whenSent.complete(serviceRequest);

        waitFor100Continue(stream, clientRequest().readContinueTimeout());

        if (entityBytes.length != 0) {
            stream.writeData(BufferData.create(entityBytes), true);
        }

        return clientRequest().outputStreamRedirect()
                ? readResponse(serviceRequest, stream, clientRequest().readContinueTimeout())
                : readResponse(serviceRequest, stream);
    }

    @Override
    protected WebClientServiceResponse doProceed(WebClientServiceRequest serviceRequest, HttpClientResponse response) {
        if (RedirectionProcessor.keepsMethodAndEntity(response.status())) {
            // HTTP/1 fallback can receive a redirect before an HTTP/2 stream exists; record entity state here.
            byte[] entityBytes = entity == BufferData.EMPTY_BYTES
                    ? BufferData.EMPTY_BYTES
                    : entityBytes(entity, serviceRequest.headers());
            hasRequestEntity = entityBytes.length > 0;
            requestEntity = entityBytes;
        }
        return super.doProceed(serviceRequest, response);
    }

    @Override
    boolean hasRequestEntity() {
        return hasRequestEntity;
    }

    @Override
    Object requestEntity() {
        return requestEntity;
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
