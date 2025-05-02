/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataWriter;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HttpPrologue;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2Settings;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.http1.spi.Http1Upgrader;
import io.helidon.webserver.http2.spi.Http2SubProtocolSelector;
import io.helidon.webserver.spi.ServerConnection;

/**
 * HTTP/1.1 to HTTP/2 connection upgrade.
 */
public class Http2Upgrader implements Http1Upgrader {
    private static final byte[] SWITCHING_PROTOCOLS_BYTES = (
            "HTTP/1.1 101 Switching Protocols\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Upgrade: h2c\r\n\r\n")
            .getBytes(StandardCharsets.UTF_8);
    private static final HeaderName HTTP2_SETTINGS_HEADER_NAME = HeaderNames.create("HTTP2-Settings");

    private final Http2Config config;
    private final List<Http2SubProtocolSelector> subProtocolProviders;

    /**
     * Creates an instance of HTTP/1.1 to HTTP/2 protocol upgrade.
     */
    Http2Upgrader(Http2Config config, List<Http2SubProtocolSelector> subProtocolProviders) {
        this.config = config;
        this.subProtocolProviders = subProtocolProviders;
    }

    /**
     * Create a new HTTP2 upgrader.
     *
     * @param config HTTP/2 protocol configuration
     * @return a new upgrader
     */
    public static Http2Upgrader create(Http2Config config) {
        return new Http2Upgrader(config, List.of());
    }

    @Override
    public String supportedProtocol() {
        return "h2c";
    }

    @Override
    public ServerConnection upgrade(ConnectionContext ctx,
                                    HttpPrologue prologue,
                                    WritableHeaders<?> headers) {
        Http2Connection connection = new Http2Connection(ctx, config, subProtocolProviders);
        if (headers.contains(HTTP2_SETTINGS_HEADER_NAME)) {
            connection.clientSettings(token68ToHttp2Settings(headers.get(HTTP2_SETTINGS_HEADER_NAME).valueBytes()));
        } else {
            throw new RuntimeException("Bad request -> not " + HTTP2_SETTINGS_HEADER_NAME + " header");
        }
        Http2Headers http2Headers = Http2Headers.create(headers);
        http2Headers.path(prologue.uriPath().rawPath());
        http2Headers.method(prologue.method());
        headers.remove(HeaderNames.HOST,
                       it -> http2Headers.authority(it.get()));
        http2Headers.scheme("http");

        HttpPrologue newPrologue = HttpPrologue.create(Http2Connection.FULL_PROTOCOL,
                                                       prologue.protocol(),
                                                       Http2Connection.PROTOCOL_VERSION,
                                                       prologue.method(),
                                                       prologue.uriPath(),
                                                       prologue.query(),
                                                       prologue.fragment());

        connection.upgradeConnectionData(newPrologue, http2Headers);
        connection.expectPreface();
        DataWriter dataWriter = ctx.dataWriter();
        dataWriter.writeNow(BufferData.create(SWITCHING_PROTOCOLS_BYTES));
        return connection;
    }

    /**
     * <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-3.2.1">RFC7540 3.2.1.</a>
     * <pre>{@code
     * HTTP2-Settings    = token68
     * token68           = 1*( ALPHA / DIGIT / "-" / "." / "_" / "~" / "+" / "/" ) *"="
     * }</pre>
     *
     * @param bytes Base64URL encoded bytes
     * @return HTTP/2 settings
     */
    private static Http2Settings token68ToHttp2Settings(byte[] bytes) {
        return Http2Settings.create(BufferData.create(Base64.getUrlDecoder().decode(bytes)));
    }

}
