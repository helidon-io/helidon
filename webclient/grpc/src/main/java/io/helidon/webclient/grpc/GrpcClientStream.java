/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.webclient.grpc;

import io.helidon.common.socket.SocketContext;
import io.helidon.http.http2.Http2Settings;
import io.helidon.webclient.http2.Http2ClientConfig;
import io.helidon.webclient.http2.Http2ClientConnection;
import io.helidon.webclient.http2.Http2ClientStream;
import io.helidon.webclient.http2.Http2StreamConfig;
import io.helidon.webclient.http2.LockingStreamIdSequence;

class GrpcClientStream extends Http2ClientStream {

    GrpcClientStream(Http2ClientConnection connection,
                      Http2Settings serverSettings,
                      SocketContext ctx,
                      Http2StreamConfig http2StreamConfig,
                      Http2ClientConfig http2ClientConfig,
                      LockingStreamIdSequence streamIdSeq) {
        super(connection, serverSettings, ctx, http2StreamConfig, http2ClientConfig, streamIdSeq);
    }
}
