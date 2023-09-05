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

package io.helidon.http;

import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;

class ClientResponseTrailersImpl implements ClientResponseTrailers {
    private static final Headers EMPTY_TRAILERS = WritableHeaders.create();
    private final Headers trailers;

    ClientResponseTrailersImpl(Headers trailers) {
        this.trailers = trailers;
    }

    ClientResponseTrailersImpl() {
        this.trailers = EMPTY_TRAILERS;
    }

    @Override
    public List<String> all(HeaderName name, Supplier<List<String>> defaultSupplier) {
        return trailers.all(name, defaultSupplier);
    }

    @Override
    public boolean contains(HeaderName name) {
        return trailers.contains(name);
    }

    @Override
    public boolean contains(Header value) {
        return trailers.contains(value);
    }

    @Override
    public Header get(HeaderName name) {
        return trailers.get(name);
    }

    @Override
    public int size() {
        return trailers.size();
    }

    @Override
    public List<HttpMediaType> acceptedTypes() {
        return trailers.acceptedTypes();
    }

    @Override
    public Iterator<Header> iterator() {
        return trailers.iterator();
    }
}
