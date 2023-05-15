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
import io.helidon.nima.common.tls.Tls;
import io.helidon.nima.http.media.EntityWriter;
import io.helidon.nima.http.media.MediaContext;
import io.helidon.nima.webclient.ClientConnection;
import io.helidon.nima.webclient.Proxy;
import io.helidon.nima.webclient.WebClientServiceRequest;
import io.helidon.nima.webclient.WebClientServiceResponse;
import io.helidon.nima.webclient.WebClientServiceResponseDefault;

class HttpCallEntityChain extends HttpCallChainBase {

    private final MediaContext mediaContext;
    private final int maxStatusLineLength;
    private final CompletableFuture<WebClientServiceRequest> whenSent;
    private final CompletableFuture<WebClientServiceResponse> whenComplete;
    private final Object entity;

    HttpCallEntityChain(Http1ClientConfig clientConfig,
                        ClientConnection connection,
                        Tls tls,
                        Proxy proxy,
                        CompletableFuture<WebClientServiceRequest> whenSent,
                        CompletableFuture<WebClientServiceResponse> whenComplete,
                        Object entity) {
        super(clientConfig, connection, tls, proxy);
        this.mediaContext = clientConfig.mediaContext();
        this.maxStatusLineLength = clientConfig.maxStatusLineLength();
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

        headers.set(Http.Header.create(Http.Header.CONTENT_LENGTH, entityBytes.length));

        // todo validate request headers
        writeHeaders(headers, writeBuffer);
        // we have completed writing the headers
        whenSent.complete(serviceRequest);

        if (entityBytes.length > 0) {
            writeBuffer.write(entityBytes);
        }
        writer.write(writeBuffer);

        Http.Status responseStatus = Http1StatusParser.readStatus(reader, maxStatusLineLength);
        ClientResponseHeaders responseHeaders = readHeaders(reader);

        return WebClientServiceResponseDefault.builder()
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
        EntityWriter<Object> writer = mediaContext.writer(genericType, headers);

        // todo this should use output stream of client, but that would require delaying header write
        // to first byte written
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        writer.write(genericType, entity, bos, headers);
        return bos.toByteArray();
    }
}
