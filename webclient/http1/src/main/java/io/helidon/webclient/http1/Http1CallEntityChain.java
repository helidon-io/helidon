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

package io.helidon.webclient.http1;

import java.util.concurrent.CompletableFuture;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.webclient.api.ClientConnection;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;

class Http1CallEntityChain extends Http1CallChainBase {

    private final CompletableFuture<WebClientServiceRequest> whenSent;
    private final byte[] entity;

    Http1CallEntityChain(Http1ClientImpl http1Client,
                         Http1ClientRequestImpl request,
                         CompletableFuture<WebClientServiceRequest> whenSent,
                         CompletableFuture<WebClientServiceResponse> whenComplete,
                         byte[] entity) {
        super(http1Client, request, whenComplete);

        this.whenSent = whenSent;
        this.entity = entity;
    }

    @Override
    public WebClientServiceResponse doProceed(ClientConnection connection,
                                              WebClientServiceRequest serviceRequest,
                                              ClientRequestHeaders headers,
                                              DataWriter writer,
                                              DataReader reader,
                                              BufferData writeBuffer) {

        headers.set(HeaderValues.create(HeaderNames.CONTENT_LENGTH, entity.length));

        writeHeaders(headers, writeBuffer, protocolConfig().validateRequestHeaders());
        // we have completed writing the headers
        whenSent.complete(serviceRequest);

        if (entity.length > 0) {
            writeBuffer.write(entity);
        }
        writer.write(writeBuffer);
        writer.flush();

        return readResponse(serviceRequest, connection, reader);
    }
}
