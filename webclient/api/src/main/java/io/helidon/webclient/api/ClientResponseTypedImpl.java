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

import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.ClientResponseTrailers;
import io.helidon.http.Status;

class ClientResponseTypedImpl<T> implements ClientResponseTyped<T> {
    private final HttpClientResponse response;
    private final T entity;
    private final RuntimeException thrown;

    ClientResponseTypedImpl(HttpClientResponse response, Class<T> entityType) {
        this.response = response;

        // Read the entity immediately, as this type is not going to be autocloseable, so we need to read the whole thing
        T entity;
        RuntimeException thrown;
        try {
            entity = response.as(entityType);
            thrown = null;
        } catch (RuntimeException e) {
            thrown = e;
            entity = null;
        }
        this.thrown = thrown;
        this.entity = entity;
    }

    @Override
    public Status status() {
        return response.status();
    }

    @Override
    public ClientResponseHeaders headers() {
        return response.headers();
    }

    @Override
    public ClientResponseTrailers trailers() {
        return response.trailers();
    }

    @Override
    public ClientUri lastEndpointUri() {
        return response.lastEndpointUri();
    }

    @Override
    public T entity() {
        if (thrown == null) {
            return entity;
        }
        // re-throw the same exception, somebody may be interested in catching it
        throw thrown;
    }

    @Override
    public void close() {
        response.close();
    }
}
