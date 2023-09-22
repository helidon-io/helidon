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

package io.helidon.webclient.http2;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.CompletableFuture;

import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.http2.Http2Headers;
import io.helidon.webclient.api.ClientRequest;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;

class Http2CallOutputStreamChain extends Http2CallChainBase {
    private final CompletableFuture<WebClientServiceRequest> whenSent;
    private final ClientRequest.OutputStreamHandler streamHandler;

    Http2CallOutputStreamChain(Http2ClientImpl http2Client,
                               Http2ClientRequestImpl http2ClientRequest,
                               CompletableFuture<WebClientServiceRequest> whenSent,
                               CompletableFuture<WebClientServiceResponse> whenComplete,
                               ClientRequest.OutputStreamHandler streamHandler) {
        super(http2Client,
              http2ClientRequest,
              whenComplete,
              req -> req.outputStream(streamHandler));

        this.whenSent = whenSent;
        this.streamHandler = streamHandler;
    }

    @Override
    protected WebClientServiceResponse doProceed(WebClientServiceRequest serviceRequest,
                                                 ClientRequestHeaders headers,
                                                 Http2ClientStream stream) {

        ClientUri uri = serviceRequest.uri();
        Http2Headers http2Headers = prepareHeaders(serviceRequest.method(), headers, uri);

        stream.writeHeaders(http2Headers, false);
        whenSent.complete(serviceRequest);

        stream.waitFor100Continue();

        Http2ClientStream.ClientOutputStream outputStream;
        try {
            outputStream = stream.outputStream();
            streamHandler.handle(outputStream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        if (!outputStream.closed()) {
            throw new IllegalStateException("Output stream was not closed in handler");
        }

        return readResponse(serviceRequest, stream);
    }
}
