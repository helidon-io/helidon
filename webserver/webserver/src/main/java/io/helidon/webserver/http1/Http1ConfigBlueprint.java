/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

package io.helidon.webserver.http1;

import java.util.List;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.http.RequestedUriDiscoveryContext;
import io.helidon.webserver.spi.ProtocolConfig;

/**
 * HTTP/1.1 server configuration.
 */
@Prototype.Blueprint(decorator = Http1BuilderDecorator.class)
@Prototype.Configured
@Prototype.Provides(ProtocolConfig.class)
interface Http1ConfigBlueprint extends ProtocolConfig {
    /**
     * Name of this configuration, in most cases the same as {@link #type()}.
     *
     * @return name of this configuration
     */
    @Override
    String name();

    /**
     * Maximal size of received HTTP prologue (GET /path HTTP/1.1).
     *
     * @return maximal size in bytes
     */
    @Option.Configured
    @Option.DefaultInt(4096)
    int maxPrologueLength();

    /**
     * Maximal size of received headers in bytes.
     *
     * @return maximal header size
     */
    @Option.Configured
    @Option.DefaultInt(16384)
    int maxHeadersSize();

    /**
     * Whether to validate headers.
     * If set to false, any value is accepted, otherwise validates headers + known headers
     * are validated by format
     * (content length is always validated as it is part of protocol processing (other headers may be validated if
     * features use them)).
     * <p>
     *     Defaults to {@code true}.
     * </p>
     *
     * @return whether to validate headers
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean validateRequestHeaders();

    /**
     * Request host header validation.
     * When host header is invalid, we return {@link io.helidon.http.Status#BAD_REQUEST_400}.
     * <p>
     * The validation is done according to RFC-3986 (see {@link io.helidon.http.HostValidator}). This is a requirement of
     * the HTTP specification.
     * <p>
     * This option allows you to disable the "full-blown" validation ("simple" validation is still in - the port must be
     * parseable to integer).
     *
     * @return whether to do a full validation of {@code Host} header according to the specification
     * @deprecated this switch exists for temporary backward compatible behavior, and will be removed in a future Helidon
     *              version
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    @Deprecated(forRemoval = true, since = "4.1.3")
    boolean validateRequestHostHeader();

    /**
     * Whether to validate headers.
     * If set to false, any value is accepted, otherwise validates headers + known headers
     * are validated by format
     * (content length is always validated as it is part of protocol processing (other headers may be validated if
     * features use them)).
     * <p>
     *     Defaults to {@code false} as user has control on the header creation.
     * </p>
     *
     * @return whether to validate headers
     */
    @Option.Configured
    @Option.DefaultBoolean(false)
    boolean validateResponseHeaders();

    /**
     * If set to false, any path is accepted (even containing illegal characters).
     *
     * @return whether to validate path
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean validatePath();

    /**
     * Logging of received packets. Uses trace and debug levels on logger of
     * {@link Http1LoggingConnectionListener} with suffix of {@code .recv`}.
     *
     * @return {@code true} if logging should be enabled for received packets, {@code false} if no logging should be done
     */
    @Option.Configured("recv-log")
    @Option.DefaultBoolean(true)
    boolean receiveLog();

    /**
     * Logging of sent packets. Uses trace and debug levels on logger of
     * {@link Http1LoggingConnectionListener} with suffix of {@code .send`}.
     *
     * @return {@code true} if logging should be enabled for sent packets, {@code false} if no logging should be done
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean sendLog();

    /**
     * When true WebServer answers to expect continue with 100 continue immediately,
     * not waiting for user to actually request the data.
     *
     * @return if {@code true} answer with 100 continue immediately after expect continue
     */
    @Option.Configured
    @Option.DefaultBoolean(false)
    boolean continueImmediately();

    /**
     * Requested URI discovery settings.
     *
     * @return settings for computing the requested URI
     */
    @Option.Configured
    RequestedUriDiscoveryContext requestedUriDiscovery();

    /**
     * Connection send event listeners for HTTP/1.1.
     *
     * @return send event listeners
     */
    @Option.Singular
    List<Http1ConnectionListener> sendListeners();

    /**
     * Connection receive event listeners for HTTP/1.1.
     *
     * @return receive event listeners
     */
    @Option.Singular
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

    /**
     * Protocol configuration type.
     *
     * @return type of this configuration
     */
    default String type() {
        return Http1ConnectionProvider.CONFIG_NAME;
    }
}
