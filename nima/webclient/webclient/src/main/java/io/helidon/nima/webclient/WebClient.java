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
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import io.helidon.common.config.Config;
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
        private final SocketOptions.Builder channelOptionsBuilder = SocketOptions.builder();
        private DnsResolver dnsResolver;
        private DnsAddressLookup dnsAddressLookup;
        private boolean followRedirect;
        private int maxRedirect;
        private WritableHeaders<?> defaultHeaders = WritableHeaders.create();
        private ParserMode mediaTypeParserMode = ParserMode.STRICT;
        private Map<String, String> properties = new HashMap<>();
        private Config config;

        /**
         * Common builder base for all the client builder.
         */
        protected Builder() {
        }

        /**
         * Actual {@link #build()} implementation for {@link WebClient} subclasses.
         *
         * @return new client
         */
        protected abstract C doBuild();

        @Override
        public C build() {
            if (channelOptions == null) {
                channelOptions = channelOptionsBuilder.build();
            }
            return doBuild();
        }

        /**
         * Config of this client.
         *
         * @param config client config
         * @return updated builder instance
         */
        public B config(Config config) {
            this.config = config;
            // set options from config
            config.get("uri").asString().ifPresent(baseUri -> baseUri(URI.create(baseUri)));
            config.get("connect-timeout-millis").asLong().ifPresent(timeout -> connectTimeout(Duration.ofMillis(timeout)));
            config.get("read-timeout-millis").asLong().ifPresent(timeout -> readTimeout(Duration.ofMillis(timeout)));
            config.get("follow-redirects").asBoolean().ifPresent(this::followRedirect);
            config.get("max-redirects").asInt().ifPresent(this::maxRedirects);
            config.get("keep-alive").asBoolean().ifPresent(this::keepAlive);
            config.get("headers").asList(Http.HeaderValue.class)
                    .ifPresent(list -> list.forEach(headerValue -> this.header(headerValue)));
            config.get("tls")
                    .map(tlsConfig -> Tls.create(tlsConfig))
                    .ifPresent(this::tls);
            return identity();
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
         * Note that using this method will trump the default {@link SocketOptions.Builder}.
         * Thus, all methods that operate on the default {@link SocketOptions.Builder} are ineffective:
         * <ul>
         *     <li>{@link #channelOptions(Consumer)} </li>
         *     <li>{@link #readTimeout(Duration)}</li>
         *     <li>{@link #connectTimeout(Duration)} (Duration)}</li>
         *     <li>{@link #keepAlive(boolean)}</li>
         * </ul>
         *
         * @param channelOptions options
         * @return updated builder
         */
        public B channelOptions(SocketOptions channelOptions) {
            this.channelOptions = channelOptions;
            return identity();
        }

        /**
         * Configure the socket options for connections opened by this client.
         *
         * @param consumer {@link SocketOptions.Builder} consumer
         * @return updated builder
         */
        public B channelOptions(Consumer<SocketOptions.Builder> consumer) {
            consumer.accept(channelOptionsBuilder);
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
         * Connect timeout.
         * This method operates on the default socket options builder and provides a shortcut for
         * {@link SocketOptions.Builder#connectTimeout(Duration)}.
         *
         * @param connectTimeout connect timeout
         * @return updated builder
         */
        public B connectTimeout(Duration connectTimeout) {
            channelOptionsBuilder.connectTimeout(connectTimeout);
            return identity();
        }

        /**
         * Sets the socket read timeout.
         * This method operates on the default socket options builder and provides a shortcut for
         * {@link SocketOptions.Builder#readTimeout(Duration)}.
         *
         * @param readTimeout read timeout
         * @return updated builder
         */
        public B readTimeout(Duration readTimeout) {
            channelOptionsBuilder.readTimeout(readTimeout);
            return identity();
        }

        /**
         * Configure socket keep alive.
         * This method operates on the default socket options builder and provides a shortcut for
         * {@link SocketOptions.Builder#socketKeepAlive(boolean)}.
         *
         * @param keepAlive keep alive
         * @return updated builder
         * @see java.net.StandardSocketOptions#SO_KEEPALIVE
         */
        public B keepAlive(boolean keepAlive) {
            channelOptionsBuilder.socketKeepAlive(keepAlive);
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
         * @param name   header name
         * @param values header values
         * @return updated builder instance
         */
        public B header(Http.HeaderName name, List<String> values) {
            Objects.requireNonNull(name);
            this.defaultHeaders.set(name, values);
            return identity();
        }

        /**
         * Set a header with value. Some headers cannot be modified.
         *
         * @param name header name
         * @param values header values
         * @return updated builder instance
         */
        public B header(Http.HeaderName name, String... values) {
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
         * Properties configured by user when creating this client.
         *
         * @param properties that were configured (mutable)
         * @return updated builder instance
         */
        public B properties(Map<String, String> properties) {
            Objects.requireNonNull(properties);
            this.properties = properties;
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
        protected SocketOptions channelOptions() {
            return channelOptions;
        }

        /**
         * Default headers to be used in every request.
         *
         * @return default headers
         */
        protected WritableHeaders<?> defaultHeaders() {
            return defaultHeaders;
        }

        /**
         * Media type parsing mode for HTTP {@code Content-Type} header.
         *
         * @return media type parsing mode
         */
        protected ParserMode mediaTypeParserMode() {
            return this.mediaTypeParserMode;
        }

        protected Tls tls() {
            return tls;
        }

        URI baseUri() {
            return baseUri;
        }

        Config config() {
            return config;
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

        Map<String, String> properties() {
            return properties;
        }
    }
}
