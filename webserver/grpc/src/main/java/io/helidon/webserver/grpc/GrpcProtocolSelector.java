/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.webserver.grpc;

import io.helidon.http.HeaderNames;
import io.helidon.http.Headers;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2Settings;
import io.helidon.http.http2.Http2StreamState;
import io.helidon.http.http2.Http2StreamWriter;
import io.helidon.http.http2.StreamFlowControl;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.Router;
import io.helidon.webserver.http2.spi.Http2SubProtocolSelector;
import io.helidon.webserver.http2.spi.SubProtocolResult;

/**
 * Sub-protocol selector for HTTP/2.
 */
public class GrpcProtocolSelector implements Http2SubProtocolSelector {

    private GrpcProtocolSelector() {
    }

    /**
     * Create a new grpc protocol selector (default).
     *
     * @return a new default grpc protocol selector for HTTP/2
     */
    public static GrpcProtocolSelector create() {
        return new GrpcProtocolSelector();
    }

    @Override
    public SubProtocolResult subProtocol(ConnectionContext ctx,
                                         HttpPrologue prologue,
                                         Http2Headers headers,
                                         Http2StreamWriter streamWriter,
                                         int streamId,
                                         Http2Settings serverSettings,
                                         Http2Settings clientSettings,
                                         StreamFlowControl flowControl,
                                         Http2StreamState currentStreamState,
                                         Router router) {
        if (prologue.method() != Method.POST) {
            return NOT_SUPPORTED;
        }

        // we know this is HTTP/2, so no need to check protocol and version
        Headers httpHeaders = headers.httpHeaders();

        if (httpHeaders.contains(HeaderNames.CONTENT_TYPE)) {
            String contentType = httpHeaders.get(HeaderNames.CONTENT_TYPE).get();

            if (contentType.startsWith("application/grpc")) {
                GrpcRouting routing = router.routing(GrpcRouting.class, GrpcRouting.empty());

                GrpcRouteHandler<?, ?> route = routing.findRoute(prologue);

                if (route == null) {
                    return new SubProtocolResult(true,
                                                 new GrpcProtocolHandlerNotFound(streamWriter, streamId, currentStreamState));
                }
                return new SubProtocolResult(true,
                                             new GrpcProtocolHandler<>(prologue,
                                                                     headers,
                                                                     streamWriter,
                                                                     streamId,
                                                                     serverSettings,
                                                                     clientSettings,
                                                                     flowControl,
                                                                     currentStreamState,
                                                                     route));
            }
        }
        return NOT_SUPPORTED;
    }
}
