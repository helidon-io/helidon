/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Optional;

import io.helidon.common.GenericType;
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

    ClientResponseTypedImpl(HttpClientResponse response, GenericType<T> entityType) {
        this.response = response;

        // Read the entity immediately, as this type is not going to be autocloseable, so we need to read the whole thing
        T entity;
        RuntimeException thrown;
        try {
            entity = entity(response, entityType);
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

    @SuppressWarnings("unchecked")
    private static <T> T entity(HttpClientResponse response, GenericType<T> entityType) {
        GenericType<?> optionalEntityType = optionalEntityType(entityType);
        if (optionalEntityType != null) {
            if (noEntity(response)) {
                return (T) Optional.empty();
            }
            return (T) response.entity().asOptional(optionalEntityType);
        }
        return response.entity().as(entityType);
    }

    private static boolean noEntity(HttpClientResponse response) {
        Status status = response.status();
        if (status.family() == Status.Family.INFORMATIONAL) {
            return true;
        }

        int statusCode = status.code();
        if (statusCode == Status.NO_CONTENT_204.code()
                || statusCode == Status.RESET_CONTENT_205.code()
                || statusCode == Status.NOT_MODIFIED_304.code()
                || statusCode == Status.NOT_FOUND_404.code()) {
            return true;
        }

        return response.headers().contentLength().orElse(-1) == 0;
    }

    private static GenericType<?> optionalEntityType(GenericType<?> entityType) {
        if (!Optional.class.equals(entityType.rawType())) {
            return null;
        }
        Type genericType = entityType.type();
        if (!(genericType instanceof ParameterizedType parameterizedType)) {
            return null;
        }
        Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
        if (actualTypeArguments.length != 1) {
            return null;
        }
        return GenericType.create(actualTypeArguments[0]);
    }
}
