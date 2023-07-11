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

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.Bytes;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.http.ClientRequestHeaders;
import io.helidon.common.http.ClientResponseHeaders;
import io.helidon.common.http.Headers;
import io.helidon.common.http.Http;
import io.helidon.common.http.Http.Header;
import io.helidon.common.http.Http.Method;
import io.helidon.common.http.Http1HeadersParser;
import io.helidon.common.http.WritableHeaders;
import io.helidon.nima.common.tls.Tls;
import io.helidon.nima.webclient.ClientConnection;
import io.helidon.nima.webclient.Proxy;
import io.helidon.nima.webclient.UriHelper;
import io.helidon.nima.webclient.WebClientServiceRequest;
import io.helidon.nima.webclient.WebClientServiceResponse;
import io.helidon.nima.webclient.spi.WebClientService;

abstract class HttpCallChainBase implements WebClientService.Chain {
    private final BufferData writeBuffer = BufferData.growing(128);
    private final Http1ClientConfig clientConfig;
    private final ClientConnection connection;
    private final Tls tls;
    private final Proxy proxy;
    private final boolean keepAlive;

    HttpCallChainBase(Http1ClientConfig clientConfig,
                      ClientConnection connection,
                      Tls tls,
                      Proxy proxy,
                      boolean keepAlive) {
        this.clientConfig = clientConfig;
        this.connection = connection;
        this.tls = tls;
        this.proxy = proxy;
        this.keepAlive = keepAlive;
    }

    static void writeHeaders(Headers headers, BufferData bufferData, boolean validate) {
        for (Http.HeaderValue header : headers) {
            if (validate) {
                header.validate();
            }
            header.writeHttp1Header(bufferData);
        }
        bufferData.write(Bytes.CR_BYTE);
        bufferData.write(Bytes.LF_BYTE);
    }

    @Override
    public WebClientServiceResponse proceed(WebClientServiceRequest serviceRequest) {
        // either use the explicit connection, or obtain one (keep alive or one-off)
        ClientConnection effectiveConnection = connection == null ? obtainConnection(serviceRequest) : connection;
        DataWriter writer = effectiveConnection.writer();
        DataReader reader = effectiveConnection.reader();
        UriHelper uri = serviceRequest.uri();
        ClientRequestHeaders headers = serviceRequest.headers();

        writeBuffer.clear();
        prologue(writeBuffer, serviceRequest, uri);
        headers.setIfAbsent(Http.Header.create(Http.Header.HOST, uri.authority()));

        return doProceed(effectiveConnection, serviceRequest, headers, writer, reader, writeBuffer);
    }

    abstract WebClientServiceResponse doProceed(ClientConnection connection,
                                                WebClientServiceRequest request,
                                                ClientRequestHeaders headers,
                                                DataWriter writer,
                                                DataReader reader,
                                                BufferData writeBuffer);

    void prologue(BufferData nonEntityData, WebClientServiceRequest request, UriHelper uri) {
        if (request.method() == Method.CONNECT) {
            // When CONNECT, the first line contains the remote host:port, in the same way as the HOST header.
            nonEntityData.writeAscii(request.method().text()
                    + " "
                    + request.headers().get(Header.HOST).value()
                    + " HTTP/1.1\r\n");
        } else {
            String schemeHostPort = clientConfig.relativeUris() ? "" : uri.scheme() + "://" + uri.host() + ":" + uri.port();
            nonEntityData.writeAscii(request.method().text()
                    + " "
                    + schemeHostPort
                    + uri.pathWithQueryAndFragment(request.query(), request.fragment())
                    + " HTTP/1.1\r\n");
        }
    }

    ClientResponseHeaders readHeaders(DataReader reader) {
        WritableHeaders<?> writable = Http1HeadersParser.readHeaders(reader,
                                                                     clientConfig.maxHeaderSize(),
                                                                     clientConfig.validateHeaders());

        return ClientResponseHeaders.create(writable, clientConfig.mediaTypeParserMode());
    }

    private ClientConnection obtainConnection(WebClientServiceRequest request) {
        return ConnectionCache.connection(clientConfig,
                                          tls,
                                          proxy,
                                          request.uri(),
                                          request.headers(),
                                          keepAlive);
    }
}
