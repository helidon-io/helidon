/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.webclient.api;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.config.Config;
import io.helidon.common.media.type.ParserMode;
import io.helidon.common.socket.SocketOptions;
import io.helidon.common.uri.UriFragment;
import io.helidon.common.uri.UriQuery;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.Header;
import io.helidon.http.WritableHeaders;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.http.media.MediaContext;
import io.helidon.http.media.MediaSupport;
import io.helidon.webclient.spi.DnsResolver;
import io.helidon.webclient.spi.WebClientService;
import io.helidon.webclient.spi.WebClientServiceProvider;

/**
 * This can be used by any HTTP client version, and does not act as a factory, for easy extensibility.
 */
@Prototype.Configured
@Prototype.Blueprint(decorator = HttpClientConfigSupport.HttpBuilderDecorator.class)
@Prototype.CustomMethods(HttpClientConfigSupport.HttpCustomMethods.class)
interface HttpClientConfigBlueprint extends HttpConfigBaseBlueprint {
    /**
     * Config method to get {@link io.helidon.webclient.api.ClientUri}.
     *
     * @param config configuration instance
     * @return client URI for the config node
     */
    @Prototype.FactoryMethod
    static ClientUri createBaseUri(Config config) {
        return config.as(URI.class).map(ClientUri::create).orElseThrow();
    }

    /**
     * Base uri used by the client in all requests.
     *
     * @return base uri of the client requests
     */
    @Option.Configured
    Optional<ClientUri> baseUri();

    /**
     * Base query used by the client in all requests.
     *
     * @return base query of the client requests
     */
    Optional<UriQuery> baseQuery();

    /**
     * Base fragment used by the client in all requests (unless overwritten on
     * per-request basis).
     *
     * @return fragment to use
     */
    Optional<UriFragment> baseFragment();

    /**
     * Socket options for connections opened by this client.
     * If there is a value explicitly configured on this type and on the socket options,
     * the one configured on this type's builder will win:
     * <ul>
     *     <li>{@link #readTimeout()}</li>
     *     <li>{@link #connectTimeout()}</li>
     * </ul>
     *
     * @return socket options
     */
    @Option.Configured
    SocketOptions socketOptions();

    /**
     * DNS resolver to be used by this client.
     *
     * @return dns resolver
     */
    DnsResolver dnsResolver();

    /**
     * DNS address lookup preferences to be used by this client.
     * Default value is determined by capabilities of the system.
     *
     * @return dns address lookup strategy
     */
    DnsAddressLookup dnsAddressLookup();

    /**
     * Default headers to be used in every request from configuration.
     *
     * @return default headers
     */
    @Option.Configured("default-headers")
    @Option.Access("")
    Map<String, String> defaultHeadersMap();

    /**
     * Default headers to be used in every request.
     *
     * @return default headers
     */
    @Option.Singular
    Set<Header> headers();

    /**
     * Default headers as a headers object. Creates a new instance for each call, so the returned value
     * can be safely mutated.
     *
     * @return default headers
     */
    default ClientRequestHeaders defaultRequestHeaders() {
        WritableHeaders<?> headers = WritableHeaders.create();
        headers().forEach(headers::set);
        return ClientRequestHeaders.create(headers);
    }

    /**
     * Configure media type parsing mode for HTTP {@code Content-Type} header.
     *
     * @return media type parsing mode
     */
    @Option.Configured
    @Option.Default("STRICT")
    ParserMode mediaTypeParserMode();

    /**
     * Configure the listener specific {@link io.helidon.http.encoding.ContentEncodingContext}.
     * This method discards all previously registered ContentEncodingContext.
     * If no content encoding context is registered, default encoding context is used.
     *
     * @return content encoding context
     */
    @Option.Configured
    ContentEncodingContext contentEncoding();

    /**
     * Configure the listener specific {@link io.helidon.http.media.MediaContext}.
     * This method discards all previously registered MediaContext.
     * If no media context is registered, default media context is used.
     *
     * @return media context
     */
    @Option.Configured
    @Option.Default("create()")
    MediaContext mediaContext();

    /**
     * Media supports (manually added). If both {@link #mediaContext()} and this is configured,
     * there will be a new context created from return of this method, with fallback of {@link #mediaContext()}.
     *
     * @return list of explicitly added media supports
     */
    @Option.Singular
    List<MediaSupport> mediaSupports();

    /**
     * WebClient services.
     *
     * @return services to use with this web client
     */
    @Option.Singular
    @Option.Configured
    @Option.Provider(WebClientServiceProvider.class)
    List<WebClientService> services();

    /**
     * Can be set to {@code true} to force the use of relative URIs in all requests,
     * regardless of the presence or absence of proxies or no-proxy lists.
     *
     * @return relative URIs flag
     */
    @Option.Configured
    @Option.DefaultBoolean(false)
    boolean relativeUris();

    /**
     * Client executor service.
     *
     * @return executor service to use when needed (such as for HTTP/2)
     */
    ExecutorService executor();

    /**
     * Whether Expect-100-Continue header is sent to verify server availability before sending an entity.
     * <p>
     * Defaults to {@code true}.
     * </p>
     *
     * @return whether Expect:100-Continue header should be sent on streamed transfers
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean sendExpectContinue();

    /**
     * Maximal size of the connection cache.
     * For most HTTP protocols, we may cache connections to various endpoints for keep alive (or stream reuse in case of HTTP/2).
     * This option limits the size. Setting this number lower than the "usual" number of target services will cause connections
     * to be closed and reopened frequently.
     */
    @Option.Configured
    @Option.DefaultInt(256)
    int connectionCacheSize();

    /**
     * WebClient cookie manager.
     *
     * @return cookie manager to use
     */
    @Option.Configured
    Optional<WebClientCookieManager> cookieManager();

    /**
     * Socket 100-Continue read timeout. Default is 1 second.
     * This read timeout is used when 100-Continue is sent by the client, before it sends an entity.
     *
     * @return read 100-Continue timeout duration
     */
    @Option.Configured
    @Option.Default("PT1S")
    Duration readContinueTimeout();

    /**
     * Whether to share connection cache between all the WebClient instances in JVM.
     *
     * @return true if connection cache is shared
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean shareConnectionCache();

    /**
     * If the entity is expected to be smaller that this number of bytes, it would be buffered in memory to optimize performance.
     * If bigger, streaming will be used.
     * <p>
     * Note that for some entity types we cannot use streaming, as they are already fully in memory (String, byte[]), for such
     * cases, this option is ignored. Default is 128Kb.
     *
     * @return maximal number of bytes to buffer in memory for supported writers
     */
    @Option.Configured
    @Option.DefaultInt(131072)
    int maxInMemoryEntity();
}
