/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
package io.helidon.webclient;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.CookieStore;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.helidon.common.http.Http;

/**
 * Helidon Web Client cookie manager.
 */
class WebClientCookieManager extends CookieManager {

    private final boolean acceptCookies;
    private final Map<String, String> defaultCookies;

    private WebClientCookieManager(CookiePolicy cookiePolicy,
                                   CookieStore cookieStore,
                                   Map<String, String> defaultCookies,
                                   boolean acceptCookies) {
        super(cookieStore, cookiePolicy);
        this.defaultCookies = Collections.unmodifiableMap(defaultCookies);
        this.acceptCookies = acceptCookies;
    }

    static WebClientCookieManager create(CookiePolicy cookiePolicy,
                                         CookieStore cookieStore,
                                         Map<String, String> defaultCookies,
                                         boolean acceptCookies) {
        return new WebClientCookieManager(cookiePolicy, cookieStore, defaultCookies, acceptCookies);
    }

    @Override
    public Map<String, List<String>> get(URI uri, Map<String, List<String>> requestHeaders) throws IOException {
        Map<String, List<String>> toReturn = new HashMap<>();
        addAllDefaultHeaders(toReturn);
        if (acceptCookies) {
            Map<String, List<String>> cookies = super.get(uri, requestHeaders);
            cookies.get(Http.Header.COOKIE).forEach(s -> toReturn.get(Http.Header.COOKIE).add(s));
        }
        return Collections.unmodifiableMap(toReturn);
    }

    private void addAllDefaultHeaders(Map<String, List<String>> toReturn) {
        List<String> defaultCookieList = new ArrayList<>();
        defaultCookies.forEach((key, value) -> defaultCookieList.add(key + "=" + value));
        toReturn.put(Http.Header.COOKIE, defaultCookieList);
    }

    @Override
    public void put(URI uri, Map<String, List<String>> responseHeaders) throws IOException {
        if (acceptCookies) {
            super.put(uri, responseHeaders);
        }
    }

    Map<String, String> defaultCookies() {
        return defaultCookies;
    }
}
