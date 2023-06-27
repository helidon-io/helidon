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

package io.helidon.nima.webclient.http1;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.function.Supplier;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.LazyValue;
import io.helidon.common.config.Config;
import io.helidon.common.media.type.ParserMode;
import io.helidon.common.socket.SocketOptions;
import io.helidon.nima.common.tls.Tls;
import io.helidon.nima.http.media.MediaContext;
import io.helidon.nima.http.media.MediaContextConfig;
import io.helidon.nima.http.media.MediaSupport;
import io.helidon.nima.webclient.DefaultDnsResolverProvider;
import io.helidon.nima.webclient.DnsAddressLookup;
import io.helidon.nima.webclient.HttpClient;
import io.helidon.nima.webclient.WebClient;
import io.helidon.nima.webclient.spi.DnsResolver;
import io.helidon.nima.webclient.spi.DnsResolverProvider;
import io.helidon.nima.webclient.spi.WebClientService;
import io.helidon.nima.webclient.spi.WebClientServiceProvider;

/**
 * HTTP/1.1 client.
 */
public interface Http1Client extends HttpClient<Http1ClientRequest, Http1ClientResponse> {
    /**
     * A new fluent API builder to customize instances.
     *
     * @return a new builder
     */
    static Http1ClientBuilder builder() {
        return new Http1ClientBuilder();
    }

    /**
     * Create a new instance.
     *
     * @return client
     */
    static Http1Client create() {
        return builder().build();
    }

    /**
     * Create a new instance based on {@link Config}.
     *
     * @param config client config
     * @return client
     */
    static Http1Client create(Config config) {
        return builder().config(config).build();
    }

    /**
     * Builder for {@link io.helidon.nima.webclient.http1.Http1Client}.
     */
    class Http1ClientBuilder extends WebClient.Builder<Http1ClientBuilder, Http1Client> {
        private static final List<DnsResolverProvider> DNS_RESOLVER_PROVIDERS = HelidonServiceLoader
                .builder(ServiceLoader.load(DnsResolverProvider.class))
                .build()
                .asList();

        private static final LazyValue<DnsResolver> DEFAULT_DNS_RESOLVER = LazyValue.create(() -> {
            return DNS_RESOLVER_PROVIDERS.stream()
                    .findFirst()
                    .orElseGet(DefaultDnsResolverProvider::new) // this should never happen, as it is a service as well
                    .createDnsResolver();
        });

        private static final SocketOptions EMPTY_OPTIONS = SocketOptions.builder().build();

        private final Http1ClientConfig.Builder configBuilder = Http1ClientConfig.builder()
                .mediaContext(MediaContext.create())
                .dnsResolver(DEFAULT_DNS_RESOLVER.get())
                .dnsAddressLookup(DnsAddressLookup.defaultLookup())
                .socketOptions(EMPTY_OPTIONS);

        private MediaContextConfig.Builder mediaContextBuilder;

        private Http1ClientBuilder() {
        }

        @Override
        public Http1Client doBuild() {
            configBuilder.defaultHeaders(defaultHeaders());
            configBuilder.socketOptions(super.channelOptions());

            if (mediaContextBuilder != null) {
                configBuilder.mediaContext(mediaContextBuilder.fallback(configBuilder.mediaContext())
                                                   .build());
            }

            return new Http1ClientImpl(configBuilder.build());
        }

        @Override
        public Http1ClientBuilder baseUri(URI baseUri) {
            super.baseUri(baseUri);
            configBuilder.baseUri(baseUri);
            return this;
        }

        @Override
        public Http1ClientBuilder tls(Tls tls) {
            super.tls(tls);
            configBuilder.tls(tls);
            return this;
        }

        @Override
        public Http1ClientBuilder channelOptions(SocketOptions channelOptions) {
            super.channelOptions(channelOptions);
            configBuilder.socketOptions(channelOptions);
            return this;
        }

        @Override
        public Http1ClientBuilder dnsResolver(DnsResolver dnsResolver) {
            super.dnsResolver(dnsResolver);
            configBuilder.dnsResolver(dnsResolver);
            return this;
        }

        @Override
        public Http1ClientBuilder dnsAddressLookup(DnsAddressLookup dnsAddressLookup) {
            super.dnsAddressLookup(dnsAddressLookup);
            configBuilder.dnsAddressLookup(dnsAddressLookup);
            return this;
        }

        @Override
        public Http1ClientBuilder followRedirect(boolean followRedirect) {
            super.followRedirect(followRedirect);
            configBuilder.followRedirects(followRedirect);
            return this;
        }

        @Override
        public Http1ClientBuilder maxRedirects(int maxRedirects) {
            super.maxRedirects(maxRedirects);
            configBuilder.maxRedirects(maxRedirects);
            return this;
        }

        /**
         * Configure the maximum allowed header size of the response.
         *
         * @param maxHeaderSize maximum header size
         * @return updated builder
         */
        public Http1ClientBuilder maxHeaderSize(int maxHeaderSize) {
            configBuilder.maxHeaderSize(maxHeaderSize);
            return this;
        }

        /**
         * Configure the maximum allowed length of the status line from the response.
         *
         * @param maxStatusLineLength maximum status line length
         * @return updated builder
         */
        public Http1ClientBuilder maxStatusLineLength(int maxStatusLineLength) {
            configBuilder.maxStatusLineLength(maxStatusLineLength);
            return this;
        }

        /**
         * Sets whether Expect-100-Continue header is sent to verify server availability for a chunked transfer.
         * <p>
         *     Defaults to {@code true}.
         * </p>
         *
         * @param sendExpect100Continue whether Expect:100-Continue header should be sent on chunked transfers
         * @return updated builder
         */
        public Http1ClientBuilder sendExpect100Continue(boolean sendExpect100Continue) {
            configBuilder.sendExpectContinue(sendExpect100Continue);
            return this;
        }

        /**
         * Sets whether the header format is validated or not.
         * <p>
         *     Defaults to {@code true}.
         * </p>
         *
         * @param validateHeaders whether header validation should be enabled
         * @return updated builder
         */
        public Http1ClientBuilder validateHeaders(boolean validateHeaders) {
            configBuilder.validateHeaders(validateHeaders);
            return this;
        }

        /**
         * Configure the default {@link MediaContext}.
         *
         * @param mediaContext media context for this client
         * @return updated builder
         */
        public Http1ClientBuilder mediaContext(MediaContext mediaContext) {
            Objects.requireNonNull(mediaContext);
            configBuilder.mediaContext(mediaContext);
            return this;
        }

        /**
         * Add an explicit media support to the list.
         * By default, all discovered media supports will be available to the server. Use this method only when
         * the media support is not discoverable by service loader, or when using explicit
         * {@link #mediaContext(io.helidon.nima.http.media.MediaContext)}.
         *
         * @param mediaSupport media support to add
         * @return updated builder
         */
        public Http1ClientBuilder addMediaSupport(MediaSupport mediaSupport) {
            Objects.requireNonNull(mediaSupport);
            if (mediaContextBuilder == null) {
                mediaContextBuilder = MediaContext.builder()
                        .mediaSupportsDiscoverServices(false);
            }
            mediaContextBuilder.addMediaSupport(mediaSupport);
            return this;
        }

        /**
         * Configure the maximum allowed size of the connection queue.
         *
         * @param connectionQueueSize maximum connection queue size
         * @return updated builder
         */
        public Http1ClientBuilder connectionQueueSize(int connectionQueueSize) {
            configBuilder.connectionQueueSize(connectionQueueSize);
            return this;
        }

        /**
         * Register new instance of {@link io.helidon.nima.webclient.spi.WebClientService}.
         *
         * @param service client service instance
         * @return updated builder instance
         */
        public Http1ClientBuilder addService(WebClientService service) {
            configBuilder.addService(service);
            return this;
        }

        /**
         * Register new instance of {@link io.helidon.nima.webclient.spi.WebClientService}.
         *
         * @param service client service instance
         * @return updated builder instance
         */
        public Http1ClientBuilder addService(Supplier<? extends WebClientService> service) {
            configBuilder.addService(service.get());
            return this;
        }

        /**
         * Sets if Java Service loader should be used to load all {@link WebClientServiceProvider}.
         *
         * @param useServiceLoader whether to use the Java Service loader
         * @return updated builder instance
         */
        public Http1ClientBuilder useSystemServiceLoader(boolean useServiceLoader) {
            configBuilder.servicesUseServiceLoader(useServiceLoader);
            return this;
        }

        /**
         * Can be set to {@code true} to force the use of relative URIs in all requests,
         * regardless of the presence or absence of proxies or no-proxy lists.
         *
         * @param relativeUris relative URIs flag
         * @return updated builder instance
         */
        public Http1ClientBuilder relativeUris(boolean relativeUris) {
            configBuilder.relativeUris(relativeUris);
            return this;
        }

        /**
         * Configure media type parsing mode for HTTP {@code Content-Type} header.
         *
         * @param mode media type parsing mode
         * @return updated builder instance
         */
        public Http1ClientBuilder mediaTypeParserMode(ParserMode mode) {
            configBuilder.mediaTypeParserMode(mode);
            return this;
        }

    }

}
