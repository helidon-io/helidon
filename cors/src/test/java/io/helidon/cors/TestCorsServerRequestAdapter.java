/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.cors;

import java.util.List;
import java.util.Optional;

import io.helidon.common.uri.UriInfo;
import io.helidon.http.HeaderName;
import io.helidon.http.Headers;

record TestCorsServerRequestAdapter(String path, UriInfo requestedUri, String method, Headers headers) implements CorsRequestAdapter<TestCorsServerRequestAdapter> {

    @Override
    public Optional<String> firstHeader(HeaderName key) {
        return headers.first(key);
    }

    @Override
    public boolean headerContainsKey(HeaderName key) {
        return headers.contains(key);
    }

    @Override
    public List<String> allHeaders(HeaderName key) {
        return headers.values(key);
    }

    @Override
    public void next() {
    }

    @Override
    public TestCorsServerRequestAdapter request() {
        return null;
    }
}
