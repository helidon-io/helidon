package io.helidon.nima.webclient.api;

import io.helidon.common.http.ClientResponseHeaders;
import io.helidon.common.http.Http;

class ClientResponseTypedImpl<T> implements ClientResponseTyped<T> {
    private final HttpClientResponse response;
    private final T entity;

    ClientResponseTypedImpl(HttpClientResponse response, T entity) {
        this.response = response;
        this.entity = entity;
    }

    @Override
    public Http.Status status() {
        return response.status();
    }

    @Override
    public ClientResponseHeaders headers() {
        return response.headers();
    }

    @Override
    public ClientUri lastEndpointUri() {
        return response.lastEndpointUri();
    }

    @Override
    public T entity() {
        return entity;
    }

    @Override
    public void close() {
        response.close();
    }
}
