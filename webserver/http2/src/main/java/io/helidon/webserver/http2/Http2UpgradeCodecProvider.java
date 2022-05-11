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

import java.util.Optional;

import io.helidon.webserver.Router;
import io.helidon.webserver.spi.UpgradeCodecProvider;

import io.netty.channel.ChannelHandler;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerUpgradeHandler;
import io.netty.handler.codec.http2.CleartextHttp2ServerUpgradeHandler;
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.netty.handler.codec.http2.Http2ServerUpgradeCodec;
import io.netty.handler.ssl.ApplicationProtocolNames;

/**
 * Service providing HTTP/2 upgrade codec for Helidon webserver.
 */
public class Http2UpgradeCodecProvider implements UpgradeCodecProvider {

    /**
     * Creates a new {@link Http2UpgradeCodecProvider}.
     * @deprecated Only intended for service loader, do not instantiate
     */
    @Deprecated
    public Http2UpgradeCodecProvider() {
    }

    @Override
    public CharSequence clearTextProtocol() {
        return Http2CodecUtil.HTTP_UPGRADE_PROTOCOL_NAME;
    }

    @Override
    public Optional<String> tlsProtocol() {
        return Optional.of(ApplicationProtocolNames.HTTP_2);
    }

    @Override
    public Optional<ChannelHandler> priorKnowledgeDecoder(HttpServerCodec httpServerCodec,
                                                          HttpServerUpgradeHandler wrappedUpgradeHandler,
                                                          int maxContentLength) {
        // Handler for prior knowledge http2
        HelidonConnectionHandler helidonHandler = new HelidonConnectionHandler.HelidonHttp2ConnectionHandlerBuilder()
                .maxContentLength(maxContentLength).build();

        return Optional.of(new CleartextHttp2ServerUpgradeHandler(httpServerCodec, wrappedUpgradeHandler, helidonHandler));
    }

    @Override
    public HttpServerUpgradeHandler.UpgradeCodec upgradeCodec(HttpServerCodec httpServerCodec,
                                 Router router,
                                 int maxContentLength) {
        HelidonConnectionHandler helidonHandler = new HelidonConnectionHandler.HelidonHttp2ConnectionHandlerBuilder()
                .maxContentLength(maxContentLength).build();

        return new Http2ServerUpgradeCodec(helidonHandler);
    }
}
