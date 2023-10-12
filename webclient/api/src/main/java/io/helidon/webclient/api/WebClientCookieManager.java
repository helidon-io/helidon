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

package io.helidon.webclient.api;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.CookieManager;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.HeaderNames;

/**
 * Helidon WebClient cookie manager.
 */
@RuntimeType.PrototypedBy(WebClientCookieManagerConfig.class)
public class WebClientCookieManager extends CookieManager implements RuntimeType.Api<WebClientCookieManagerConfig> {

    private static final String COOKIE = HeaderNames.COOKIE.defaultCase();
    private static final String SET_COOKIE = HeaderNames.SET_COOKIE.defaultCase();
    private static final String SET_COOKIE2 = HeaderNames.SET_COOKIE2.defaultCase();

    private final boolean acceptCookies;
    private final List<String> defaultCookies;
    private final WebClientCookieManagerConfig prototype;

    private WebClientCookieManager(WebClientCookieManagerConfig config) {
        super(config.cookieStore().orElse(null), config.cookiePolicy());

        this.prototype = config;
        this.defaultCookies = config.defaultCookies()
                .entrySet()
                .stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .toList();
        this.acceptCookies = config.automaticStoreEnabled();
    }

    /**
     * Create a cookie manager from its configuration.
     *
     * @param config configuration of the manager
     * @return a new manager
     */
    public static WebClientCookieManager create(WebClientCookieManagerConfig config) {
        return new WebClientCookieManager(config);
    }

    /**
     * Create a cookie manager updating its configuration.
     *
     * @param configConsumer consumer to update manager configuration
     * @return a new manager
     */
    public static WebClientCookieManager create(Consumer<WebClientCookieManagerConfig.Builder> configConsumer) {
        return builder()
                .update(configConsumer)
                .build();
    }

    /**
     * A new builder to create a customized cookie manager.
     *
     * @return cookie manager
     */
    public static WebClientCookieManagerConfig.Builder builder() {
        return WebClientCookieManagerConfig.builder();
    }

    @Override
    public WebClientCookieManagerConfig prototype() {
        return prototype;
    }

    @Override
    public Map<String, List<String>> get(URI uri, Map<String, List<String>> requestHeaders) throws IOException {
        throw new UnsupportedOperationException("Not implemented, use get(URI, ClientRequestHeaders)");
    }

    @Override
    public void put(URI uri, Map<String, List<String>> responseHeaders) throws IOException {
        throw new UnsupportedOperationException("Not implemented, use put(URI, ClientResponseHeaders)");
    }

    /**
     * Add stored cookies to request headers.
     * <p>
     * See {@link #get}.
     *
     * @param uri            the uri
     * @param requestHeaders client request headers
     */
    public void request(ClientUri uri, ClientRequestHeaders requestHeaders) {
        try {
            if (acceptCookies) {
                Map<String, List<String>> cookieMap = super.get(uri.toUri(), Map.of());
                List<String> cookies = cookieMap.get(COOKIE);
                cookies.addAll(defaultCookies);
                if (!cookies.isEmpty()) {
                    requestHeaders.add(HeaderNames.COOKIE, cookies.toArray(new String[0]));
                }
            } else if (!defaultCookies.isEmpty()) {
                requestHeaders.add(HeaderNames.COOKIE, defaultCookies.toArray(new String[0]));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Store cookies from response headers.
     * <p>
     * See {@link #put}.
     *
     * @param uri     the uri
     * @param headers client response headers
     */
    public void response(ClientUri uri, ClientResponseHeaders headers) {
        try {
            if (acceptCookies) {
                Map<String, List<String>> cookies = null;

                if (headers.contains(HeaderNames.SET_COOKIE)) {
                    cookies = new HashMap<>();
                    cookies.put(SET_COOKIE, headers.get(HeaderNames.SET_COOKIE).allValues());
                }

                if (headers.contains(HeaderNames.SET_COOKIE2)) {
                    cookies = cookies == null ? new HashMap<>() : cookies;
                    cookies.put(SET_COOKIE2, headers.get(HeaderNames.SET_COOKIE2).allValues());
                }

                if (cookies != null) {
                    super.put(uri.toUri(), cookies);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
