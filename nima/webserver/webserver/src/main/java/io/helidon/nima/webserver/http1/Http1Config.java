/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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
package io.helidon.nima.webserver.http1;

import java.util.List;

import io.helidon.builder.Builder;
import io.helidon.builder.Singular;
import io.helidon.common.http.RequestedUriDiscoveryContext;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.pico.builder.config.ConfigBean;

/**
 * HTTP/1.1 server configuration.
 */
@Builder(interceptor = Http1BuilderInterceptor.class)
@ConfigBean(key = "server.connection-providers.http_1_1")
public interface Http1Config {
    /**
     * Maximal size of received HTTP prologue (GET /path HTTP/1.1).
     *
     * @return maximal size in bytes
     */
    @ConfiguredOption("2048")
    int maxPrologueLength();

    /**
     * Maximal size of received headers in bytes.
     *
     * @return maximal header size
     */
    @ConfiguredOption("16384")
    int maxHeadersSize();

    /**
     * Whether to validate headers.
     * If set to false, any value is accepted, otherwise validates headers + known headers
     * are validated by format
     * (content length is always validated as it is part of protocol processing (other headers may be validated if
     * features use them)).
     *
     * @return whether to validate headers
     */
    @ConfiguredOption("true")
    boolean validateHeaders();

    /**
     * If set to false, any path is accepted (even containing illegal characters).
     *
     * @return whether to validate path
     */
    @ConfiguredOption("true")
    boolean validatePath();

    /**
     * Logging of received packets. Uses trace and debug levels on logger of
     * {@link io.helidon.nima.webserver.http1.Http1LoggingConnectionListener} with suffix of {@code .recv`}.
     *
     * @return {@code true} if logging should be enabled for received packets, {@code false} if no logging should be done
     */
    @ConfiguredOption(key = "recv-log", value = "true")
    boolean receiveLog();

    /**
     * Logging of sent packets. Uses trace and debug levels on logger of
     * {@link io.helidon.nima.webserver.http1.Http1LoggingConnectionListener} with suffix of {@code .send`}.
     *
     * @return {@code true} if logging should be enabled for sent packets, {@code false} if no logging should be done
     */
    @ConfiguredOption(key = "send-log", value = "true")
    boolean sendLog();

    /**
     * When true WebServer answers to expect continue with 100 continue immediately,
     * not waiting for user to actually request the data.
     *
     * @return if {@code true} answer with 100 continue immediately after expect continue
     */
    @ConfiguredOption("false")
    boolean continueImmediately();

    /**
     * Requested URI discovery settings.
     *
     * @return settings for computing the requested URI
     */
    @ConfiguredOption(key = "requested-uri-discovery")
    RequestedUriDiscoveryContext requestedUriDiscovery();

    /**
     * Connection send event listeners for HTTP/1.1.
     *
     * @return send event listeners
     */
    @Singular
    List<Http1ConnectionListener> sendListeners();

    /**
     * Connection receive event listeners for HTTP/1.1.
     *
     * @return receive event listeners
     */
    @Singular
    List<Http1ConnectionListener> receiveListeners();

    /**
     * A single send listener, this value is computed.
     *
     * @return send listener
     */
    Http1ConnectionListener compositeSendListener();

    /**
     * A single receive listener, this value is computed.
     *
     * @return receive listener
     */
    Http1ConnectionListener compositeReceiveListener();
}
