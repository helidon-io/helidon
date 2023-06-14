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

package io.helidon.nima.webclient;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

import io.helidon.common.http.ClientRequestHeaders;
import io.helidon.common.http.Http;
import io.helidon.common.http.WritableHeaders;
import io.helidon.common.media.type.ParserMode;
import io.helidon.common.socket.SocketOptions;
import io.helidon.nima.common.tls.Tls;
import io.helidon.nima.webclient.http1.Http1;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.spi.DnsResolver;
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
        private DnsResolver dnsResolver;
        private DnsAddressLookup dnsAddressLookup;
        private boolean followRedirect;
        private int maxRedirect;
        private WritableHeaders<?> defaultHeaders = WritableHeaders.create();
        private ParserMode mediaTypeParserMode = ParserMode.STRICT;

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
            return identity();
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
            return identity();
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
            return identity();
        }

        /**
         * Socket options for connections opened by this client.
         *
         * @param channelOptions options
         * @return updated builder
         */
        public B channelOptions(SocketOptions channelOptions) {
            this.channelOptions = channelOptions;
            return identity();
        }

        /**
         * DNS resolver to be used by this client.
         *
         * @param dnsResolver dns resolver
         * @return updated builder
         */
        public B dnsResolver(DnsResolver dnsResolver) {
            this.dnsResolver = dnsResolver;
            return identity();
        }

        /**
         * DNS address lookup preferences to be used by this client.
         *
         * @param dnsAddressLookup dns address lookup strategy
         * @return updated builder
         */
        public B dnsAddressLookup(DnsAddressLookup dnsAddressLookup) {
            this.dnsAddressLookup = dnsAddressLookup;
            return identity();
        }

        /**
         * Whether to follow redirects.
         *
         * @param followRedirect whether to follow redirects
         * @return updated builder
         */
        public B followRedirect(boolean followRedirect) {
            this.followRedirect = followRedirect;
            return identity();
        }

        /**
         * Max number of followed redirects.
         * This is ignored if followRedirect option is false.
         *
         * @param maxRedirect max number of followed redirects
         * @return updated builder
         */
        public B maxRedirects(int maxRedirect) {
            this.maxRedirect = maxRedirect;
            return identity();
        }

        /**
         * Configure a custom header to be sent. Some headers cannot be modified.
         *
         * @param header header to add
         * @return updated builder instance
         */
        public B header(Http.HeaderValue header) {
            Objects.requireNonNull(header);
            this.defaultHeaders.set(header);
            return identity();
        }

        /**
         * Set header with multiple values. Some headers cannot be modified.
         *
         * @param name header name
         * @param values header values
         * @return updated builder instance
         */
        public B header(Http.HeaderName name, List<String> values) {
            Objects.requireNonNull(name);
            this.defaultHeaders.set(name, values);
            return identity();
        }

        /**
         * Update headers.
         *
         * @param headersConsumer consumer of client headers
         * @return updated builder instance
         */
        public B headers(Function<ClientRequestHeaders, WritableHeaders<?>> headersConsumer) {
            this.defaultHeaders = headersConsumer.apply(ClientRequestHeaders.create(defaultHeaders));
            return identity();
        }

        /**
         * Remove header with the selected name from the default headers.
         *
         * @param name header name
         * @return updated builder instance
         */
        protected B removeHeader(Http.HeaderName name) {
            Objects.requireNonNull(name);
            this.defaultHeaders.remove(name);
            return identity();
        }

        /**
         * Configure media type parsing mode for HTTP {@code Content-Type} header.
         *
         * @param mode media type parsing mode
         * @return updated builder instance
         */
        public B mediaTypeParserMode(ParserMode mode) {
            this.mediaTypeParserMode = mode;
            return identity();
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

        DnsResolver dnsResolver() {
            return dnsResolver;
        }

        DnsAddressLookup dnsAddressLookup() {
            return dnsAddressLookup;
        }

        boolean followRedirect() {
            return followRedirect;
        }

        int maxRedirect() {
            return maxRedirect;
        }

        protected WritableHeaders<?> defaultHeaders() {
            return defaultHeaders;
        }

        protected ParserMode mediaTypeParserMode() {
            return this.mediaTypeParserMode;
        }

    }
}
