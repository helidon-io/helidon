/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver;

import java.util.ArrayList;
import java.util.List;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.ReferenceCountUtil;

/**
 * Response compression with cleanup for a response aborted before its headers are written.
 */
final class ResponseContentCompressor extends HttpContentCompressor {

    /**
     * Consume the pending request's encoding preferences without writing a response to the connection.
     * The caller must be on the event loop, after the preceding response completes and before this response's headers.
     *
     * @param ctx compressor context
     * @throws Exception if compressor state cannot be advanced
     */
    void discardPendingResponse(ChannelHandlerContext ctx) throws Exception {
        var response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NO_CONTENT);
        List<Object> discarded = new ArrayList<>(1);
        try {
            // A complete bodyless response consumes exactly one request and leaves the compressor awaiting headers.
            super.encode(ctx, response, discarded);
        } finally {
            response.release();
            discarded.forEach(ReferenceCountUtil::release);
        }
    }
}
