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

package io.helidon.nima.webclient;

import java.net.URI;
import java.util.function.Supplier;

import io.helidon.common.socket.SocketOptions;
import io.helidon.nima.common.tls.Tls;
import io.helidon.nima.webclient.http1.Http1;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.spi.Protocol;

/**
 * HTTP client.
 */
public interface WebClient {

    /**
     * Create a new builder of the {@link Http1Client}.
     *
     * @return new HTTP1 client builder
     */
    static Http1Client.Http1ClientBuilder builder() {
        return builder(Http1.PROTOCOL);
    }

    /**
     * Create a new client builder based on the provided protocol.
     *
     * @param protocol protocol of the client
     * @param <T>      protocol client builder type
     * @return new client protocol builder instance
     */
    static <T> T builder(Protocol<T> protocol) {
        return protocol.provider().protocolBuilder();
    }

    /**
     * Fluent API builder for {@link WebClient}.
     *
     * @param <B> type of builder (subclass of this class)
     * @param <C> type of web client
     */
    abstract class Builder<B extends Builder<B, C>, C extends WebClient> implements io.helidon.common.Builder<B, C> {

        private URI baseUri;
        private Tls tls;
        private SocketOptions channelOptions;

        /**
         * Common builder base for all the client builder.
         */
        protected Builder() {
        }

        /**
         * Base uri used by the client in all requests.
         *
         * @param baseUri base uri of the client requests
         * @return updated builder
         */
        public B baseUri(String baseUri) {
            return baseUri(URI.create(baseUri));
        }

        /**
         * Base uri used by the client in all requests.
         *
         * @param baseUri base uri of the client requests
         * @return updated builder
         */
        public B baseUri(URI baseUri) {
            this.baseUri = baseUri;
            return (B) this;
        }

        /**
         * TLS configuration for any TLS request from this client.
         * TLS can also be configured per request.
         * TLS is used when the protocol is set to {@code https}.
         *
         * @param tls TLS configuration to use
         * @return updated builder
         */
        public B tls(Tls tls) {
            this.tls = tls;
            return (B) this;
        }

        /**
         * TLS configuration for any TLS request from this client.
         * TLS can also be configured per request.
         * TLS is used when the protocol is set to {@code https}.
         *
         * @param tls TLS configuration to use
         * @return updated builder
         */
        public B tls(Supplier<Tls> tls) {
            this.tls = tls.get();
            return (B) this;
        }

        /**
         * Socket options for connections opened by this client.
         *
         * @param channelOptions options
         * @return updated builder
         */
        public B channelOptions(SocketOptions channelOptions) {
            this.channelOptions = channelOptions;
            return (B) this;
        }

        /**
         * Channel options.
         *
         * @return socket options
         */
        SocketOptions channelOptions() {
            return channelOptions;
        }

        /**
         * Configured TLS.
         *
         * @return TLS if configured, null otherwise
         */
        Tls tls() {
            return tls;
        }

        /**
         * Base request uri.
         *
         * @return client request base uri
         */
        URI baseUri() {
            return baseUri;
        }
    }
}
