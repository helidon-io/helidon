package io.helidon.nima.webclient.api;

import io.helidon.common.http.ClientResponseHeaders;
import io.helidon.common.http.Http;

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
        if (thrown == null) {
            return entity;
        }
        throw new IllegalStateException("Failed to read response entity", thrown);
    }

    @Override
    public void close() {
        response.close();
    }
}
