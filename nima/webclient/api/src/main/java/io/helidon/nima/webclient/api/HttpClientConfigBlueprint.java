/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.webclient.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import io.helidon.builder.api.Prototype;
import io.helidon.common.http.ClientRequestHeaders;
import io.helidon.common.http.Http;
import io.helidon.common.http.WritableHeaders;
import io.helidon.common.media.type.ParserMode;
import io.helidon.common.socket.SocketOptions;
import io.helidon.common.uri.UriFragment;
import io.helidon.common.uri.UriQuery;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.nima.http.encoding.ContentEncodingContext;
import io.helidon.nima.http.media.MediaContext;
import io.helidon.nima.http.media.MediaSupport;
import io.helidon.nima.webclient.spi.DnsResolver;
import io.helidon.nima.webclient.spi.WebClientService;
import io.helidon.nima.webclient.spi.WebClientServiceProvider;

/**
 * This can be used by any HTTP client version, and does not act as a factory, for easy extensibility.
 */
@Configured
@Prototype.Blueprint(decorator = HttpClientConfigSupport.HttpBuilderDecorator.class)
@Prototype.CustomMethods(HttpClientConfigSupport.HttpCustomMethods.class)
interface HttpClientConfigBlueprint extends HttpConfigBaseBlueprint {
    /**
     * Base uri used by the client in all requests.
     *
     * @return base uri of the client requests
     */
    @ConfiguredOption
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
     *     <li>{@link #connectTimeout()} eout(java.time.Duration)} (Duration)}</li>
     * </ul>
     *
     * @return socket options
     */
    @ConfiguredOption
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
    @ConfiguredOption(key = "default-headers", builderMethod = false)
    Map<String, String> defaultHeadersMap();

    /**
     * Default headers to be used in every request.
     *
     * @return default headers
     */
    @Prototype.Singular
    Set<Http.HeaderValue> headers();

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
    @ConfiguredOption("STRICT")
    ParserMode mediaTypeParserMode();

    /**
     * Configure the listener specific {@link io.helidon.nima.http.encoding.ContentEncodingContext}.
     * This method discards all previously registered ContentEncodingContext.
     * If no content encoding context is registered, default encoding context is used.
     *
     * @return content encoding context
     */
    @ConfiguredOption
    ContentEncodingContext contentEncoding();

    /**
     * Configure the listener specific {@link io.helidon.nima.http.media.MediaContext}.
     * This method discards all previously registered MediaContext.
     * If no media context is registered, default media context is used.
     *
     * @return media context
     */
    @ConfiguredOption("create()")
    MediaContext mediaContext();

    /**
     * Media supports (manually added). If both {@link #mediaContext()} and this is configured,
     * there will be a new context created from return of this method, with fallback of {@link #mediaContext()}.
     *
     * @return list of explicitly added media supports
     */
    @Prototype.Singular
    List<MediaSupport> mediaSupports();

    /**
     * Web client services.
     *
     * @return services to use with this web client
     */
    @Prototype.Singular
    @ConfiguredOption(provider = true, providerType = WebClientServiceProvider.class)
    List<WebClientService> services();

    /**
     * Can be set to {@code true} to force the use of relative URIs in all requests,
     * regardless of the presence or absence of proxies or no-proxy lists.
     *
     * @return relative URIs flag
     */
    // TODO Set the default value to false when proxy is implemented and see Http1CallChainBase.prologue for other changes
    @ConfiguredOption("true")
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
    @ConfiguredOption("true")
    boolean sendExpectContinue();

    /**
     * Maximal size of the connection cache.
     * For most HTTP protocols, we may cache connections to various endpoints for keep alive (or stream reuse in case of HTTP/2).
     * This option limits the size. Setting this number lower than the "usual" number of target services will cause connections
     * to be closed and reopened frequently.
     */
    @ConfiguredOption("256")
    int connectionCacheSize();

    /**
     * Web client cookie manager.
     *
     * @return cookie manager to use
     */
    @ConfiguredOption
    Optional<WebClientCookieManager> cookieManager();
}
