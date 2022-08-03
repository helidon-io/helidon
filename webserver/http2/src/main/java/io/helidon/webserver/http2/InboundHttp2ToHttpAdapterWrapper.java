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

package io.helidon.webserver.http2;

import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http2.AbstractInboundHttp2ToHttpAdapterBuilder;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Stream;
import io.netty.handler.codec.http2.InboundHttp2ToHttpAdapter;
import io.netty.handler.codec.http2.InboundHttp2ToHttpAdapterBuilder;

class InboundHttp2ToHttpAdapterWrapper extends InboundHttp2ToHttpAdapter {

    protected InboundHttp2ToHttpAdapterWrapper(Http2Connection connection,
                                               int maxContentLength,
                                               boolean validateHttpHeaders,
                                               boolean propagateSettings) {
        super(connection, maxContentLength, validateHttpHeaders, propagateSettings);
    }

    @Override
    protected FullHttpMessage newMessage(Http2Stream stream,
                                         Http2Headers headers,
                                         boolean validateHttpHeaders,
                                         ByteBufAllocator alloc) throws Http2Exception {

        FullHttpMessage fullHttpMessage = super.newMessage(stream, headers, validateHttpHeaders, alloc);
        fullHttpMessage.setProtocolVersion(new HttpVersion("HTTP", 2, 0, true));
        return fullHttpMessage;
    }

    static InboundHttp2ToHttpAdapter create(Http2Connection connection,
                                     int maxContentLength,
                                     boolean validateHttpHeaders,
                                     boolean propagateSettings){
        return new Builder(connection, maxContentLength, validateHttpHeaders, propagateSettings).build();
    }

    private static class Builder extends AbstractInboundHttp2ToHttpAdapterBuilder<InboundHttp2ToHttpAdapter,
            InboundHttp2ToHttpAdapterBuilder> {

        protected Builder(Http2Connection connection,
                          int maxContentLength,
                          boolean validateHttpHeaders,
                          boolean propagateSettings) {
            super(connection);
            super.maxContentLength(maxContentLength);
            super.validateHttpHeaders(validateHttpHeaders);
            super.propagateSettings(propagateSettings);
        }

        @Override
        public InboundHttp2ToHttpAdapter build() {
            return super.build();
        }

        @Override
        protected InboundHttp2ToHttpAdapter build(Http2Connection connection,
                                                  int maxContentLength,
                                                  boolean validateHttpHeaders,
                                                  boolean propagateSettings) throws Exception {
            return new InboundHttp2ToHttpAdapterWrapper(connection, maxContentLength, validateHttpHeaders, propagateSettings);
        }
    }
}
