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

package io.helidon.nima.webclient.http1;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.CompletableFuture;

import io.helidon.common.GenericType;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.http.ClientRequestHeaders;
import io.helidon.common.http.ClientResponseHeaders;
import io.helidon.common.http.Http;
import io.helidon.nima.http.media.EntityWriter;
import io.helidon.nima.webclient.api.ClientConnection;
import io.helidon.nima.webclient.api.HttpClientConfig;
import io.helidon.nima.webclient.api.WebClient;
import io.helidon.nima.webclient.api.WebClientServiceRequest;
import io.helidon.nima.webclient.api.WebClientServiceResponse;

class HttpCallEntityChain extends HttpCallChainBase {

    private final ClientRequestImpl request;
    private final CompletableFuture<WebClientServiceRequest> whenSent;
    private final CompletableFuture<WebClientServiceResponse> whenComplete;
    private final Object entity;

    HttpCallEntityChain(WebClient webClient,
                        ClientRequestImpl request,
                        HttpClientConfig clientConfig,
                        Http1ClientProtocolConfig protocolConfig,
                        CompletableFuture<WebClientServiceRequest> whenSent,
                        CompletableFuture<WebClientServiceResponse> whenComplete,
                        Object entity) {
        super(webClient,
              clientConfig,
              protocolConfig,
              request.connection().orElse(null),
              request.tls(),
              request.proxy(),
              request.keepAlive());

        this.request = request;
        this.whenSent = whenSent;
        this.whenComplete = whenComplete;
        this.entity = entity;
    }

    @Override
    public WebClientServiceResponse doProceed(ClientConnection connection,
                                              WebClientServiceRequest serviceRequest,
                                              ClientRequestHeaders headers,
                                              DataWriter writer,
                                              DataReader reader,
                                              BufferData writeBuffer) {
        byte[] entityBytes;
        if (entity == BufferData.EMPTY_BYTES) {
            entityBytes = BufferData.EMPTY_BYTES;
        } else {
            entityBytes = entityBytes(entity, headers);
        }
        connection.readTimeout(request.readTimeout());

        headers.set(Http.Header.create(Http.Header.CONTENT_LENGTH, entityBytes.length));

        writeHeaders(headers, writeBuffer, protocolConfig().validateHeaders());
        // we have completed writing the headers
        whenSent.complete(serviceRequest);

        if (entityBytes.length > 0) {
            writeBuffer.write(entityBytes);
        }
        writer.write(writeBuffer);

        Http.Status responseStatus = Http1StatusParser.readStatus(reader, protocolConfig().maxStatusLineLength());
        ClientResponseHeaders responseHeaders = readHeaders(reader);

        return WebClientServiceResponse.builder()
                .connection(connection)
                .reader(reader)
                .headers(responseHeaders)
                .status(responseStatus)
                .whenComplete(whenComplete)
                .serviceRequest(serviceRequest)
                .build();
    }

    byte[] entityBytes(Object entity, ClientRequestHeaders headers) {
        if (entity instanceof byte[]) {
            return (byte[]) entity;
        }
        GenericType<Object> genericType = GenericType.create(entity);
        EntityWriter<Object> writer = clientConfig().mediaContext().writer(genericType, headers);

        // todo this should use output stream of client, but that would require delaying header write
        // to first byte written
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        writer.write(genericType, entity, bos, headers);
        return bos.toByteArray();
    }
}
