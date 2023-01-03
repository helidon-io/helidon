/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.nima.grpc.webserver;

import io.helidon.common.http.Headers;
import io.helidon.common.http.Http;
import io.helidon.common.http.Http.Header;
import io.helidon.common.http.HttpPrologue;
import io.helidon.nima.http2.Http2Headers;
import io.helidon.nima.http2.Http2Settings;
import io.helidon.nima.http2.Http2StreamState;
import io.helidon.nima.http2.Http2StreamWriter;
import io.helidon.nima.http2.webserver.spi.Http2SubProtocolProvider;
import io.helidon.nima.http2.webserver.spi.SubProtocolResult;
import io.helidon.nima.webserver.ConnectionContext;
import io.helidon.nima.webserver.Router;

/**
 * {@link java.util.ServiceLoader} provider implementation of grpc sub-protocol of HTTP/2.
 */
public class GrpcProtocolProvider implements Http2SubProtocolProvider {
    /**
     * Default constructor required by Java {@link java.util.ServiceLoader}.
     *
     * @deprecated please do not use directly outside of testing, this is reserved for Java {@link java.util.ServiceLoader}
     */
    @Deprecated
    public GrpcProtocolProvider() {
    }

    @Override
    public SubProtocolResult subProtocol(ConnectionContext ctx,
                                         HttpPrologue prologue,
                                         Http2Headers headers,
                                         Http2StreamWriter streamWriter,
                                         int streamId,
                                         Http2Settings serverSettings,
                                         Http2Settings clientSettings,
                                         Http2StreamState currentStreamState,
                                         Router router) {
        if (prologue.method() != Http.Method.POST) {
            return NOT_SUPPORTED;
        }

        // we know this is HTTP/2, so no need to check protocol and version
        Headers httpHeaders = headers.httpHeaders();

        if (httpHeaders.contains(Header.CONTENT_TYPE)) {
            String contentType = httpHeaders.get(Header.CONTENT_TYPE).value();

            if (contentType.startsWith("application/grpc")) {
                GrpcRouting routing = router.routing(GrpcRouting.class, GrpcRouting.empty());

                Grpc<?, ?> route = routing.findRoute(prologue);

                if (route == null) {
                    return new SubProtocolResult(true,
                                                 new GrpcProtocolHandlerNotFound(streamWriter, streamId, currentStreamState));
                }
                return new SubProtocolResult(true,
                                             new GrpcProtocolHandler(prologue,
                                                                     headers,
                                                                     streamWriter,
                                                                     streamId,
                                                                     serverSettings,
                                                                     clientSettings,
                                                                     currentStreamState,
                                                                     route));
            }
        }
        return NOT_SUPPORTED;
    }
}
