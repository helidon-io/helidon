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

package io.helidon.nima.webclient;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.CookieStore;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.helidon.common.http.ClientRequestHeaders;
import io.helidon.common.http.ClientResponseHeaders;
import io.helidon.common.http.Http;

/**
 * Helidon Web Client cookie manager.
 */
public class WebClientCookieManager extends CookieManager {

    private static final String COOKIE = Http.Header.COOKIE.defaultCase();
    private static final String SET_COOKIE = Http.Header.SET_COOKIE.defaultCase();
    private static final String SET_COOKIE2 = Http.Header.SET_COOKIE2.defaultCase();

    private final boolean acceptCookies;
    private final List<String> defaultCookies;

    private WebClientCookieManager(CookiePolicy cookiePolicy,
                                   CookieStore cookieStore,
                                   Map<String, String> defaultCookies,
                                   boolean acceptCookies) {
        super(cookieStore, cookiePolicy);
        this.defaultCookies = defaultCookies.entrySet()
                .stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.toList());
        this.acceptCookies = acceptCookies;
    }

    /**
     * Create a cookie manager.
     *
     * @param cookiePolicy policy to accept cookies
     * @param cookieStore storage for cookies
     * @param defaultCookies default cookies to send
     * @param acceptCookies flag to handler acceptance of cookies
     * @return new cookie manager
     */
    public static WebClientCookieManager create(CookiePolicy cookiePolicy,
                                         CookieStore cookieStore,
                                         Map<String, String> defaultCookies,
                                         boolean acceptCookies) {
        return new WebClientCookieManager(cookiePolicy, cookieStore, defaultCookies, acceptCookies);
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
     * See {@link #get}.
     *
     * @param uri the uri
     * @param requestHeaders client request headers
     * @return requret headers
     * @throws IOException if an I/O error occurs
     */
    public ClientRequestHeaders get(UriHelper uri, ClientRequestHeaders requestHeaders) throws IOException {
        if (acceptCookies) {
            Map<String, List<String>> cookieMap = super.get(uri.toUri(), Collections.emptyMap());
            List<String> cookies = cookieMap.get(COOKIE);
            cookies.addAll(defaultCookies);
            if (!cookies.isEmpty()) {
                requestHeaders.add(Http.Header.COOKIE, cookies.toArray(new String[0]));
            }
        } else if (!defaultCookies.isEmpty()) {
            requestHeaders.add(Http.Header.COOKIE, defaultCookies.toArray(new String[0]));
        }
        return requestHeaders;
    }

    /**
     * See {@link #put}.
     *
     * @param uri the uri
     * @param headers client response headers
     * @throws IOException if an I/O error occurs
     */
    public void put(UriHelper uri, ClientResponseHeaders headers) throws IOException {
        if (acceptCookies) {
            Map<String, List<String>> cookies = null;
            for (Http.HeaderValue header : headers) {
                String name = header.name();
                if (SET_COOKIE.equalsIgnoreCase(name) || SET_COOKIE2.equalsIgnoreCase(name)) {
                    if (cookies == null) {
                        cookies = new HashMap<>();
                    }
                    cookies.put(name, header.allValues());
                }
            }
            if (cookies != null) {
                super.put(uri.toUri(), cookies);
            }
        }
    }
}
