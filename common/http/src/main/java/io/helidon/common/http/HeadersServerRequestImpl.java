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

package io.helidon.common.http;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.common.http.Http.HeaderName;
import io.helidon.common.parameters.Parameters;

class HeadersServerRequestImpl implements HeadersServerRequest {
    private final Headers headers;
    private List<HttpMediaType> cachedAccepted;
    private Parameters cacheCookies;
    private Optional<HttpMediaType> cacheContentType;

    HeadersServerRequestImpl(Headers headers) {
        this.headers = headers;
    }

    @Override
    public List<String> all(HeaderName name, Supplier<List<String>> defaultSupplier) {
        return headers.all(name, defaultSupplier);
    }

    @Override
    public boolean contains(HeaderName name) {
        return headers.contains(name);
    }

    @Override
    public boolean contains(Http.HeaderValue headerWithValue) {
        return headers.contains(headerWithValue);
    }

    @Override
    public Http.HeaderValue get(HeaderName name) {
        return headers.get(name);
    }

    @SuppressWarnings("OptionalAssignedToNull")
    @Override
    public Optional<HttpMediaType> contentType() {
        if (cacheContentType == null) {
            cacheContentType = HeadersServerRequest.super.contentType();
        }
        return cacheContentType;
    }

    @Override
    public int size() {
        return headers.size();
    }

    @Override
    public List<HttpMediaType> acceptedTypes() {
        if (cachedAccepted != null) {
            return cachedAccepted;
        }
        List<HttpMediaType> acceptedTypes;

        List<String> acceptValues = all(Http.Header.ACCEPT, List::of);
        if (acceptValues.size() == 1 && HUC_ACCEPT_DEFAULT.value().equals(acceptValues.get(0))) {
            acceptedTypes = HUC_ACCEPT_DEFAULT_TYPES;
        } else {
            acceptedTypes = acceptValues.stream()
                    .flatMap(h -> HeaderHelper.tokenize(',', "\"", false, h).stream())
                    .map(String::trim)
                    .map(HttpMediaType::create)
                    .sorted()
                    .toList();
        }
        cachedAccepted = acceptedTypes;

        return cachedAccepted;
    }

    @Override
    public Iterator<Http.HeaderValue> iterator() {
        return headers.iterator();
    }

    @Override
    public String toString() {
        return headers.toString();
    }

    @Override
    public Parameters cookies() {
        if (cacheCookies == null) {
            cacheCookies = HeadersServerRequest.super.cookies();
        }
        return cacheCookies;
    }
}
