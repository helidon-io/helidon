/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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
package io.helidon.webserver.http.spi;

import java.io.OutputStream;
import java.util.Objects;
import java.util.Optional;

import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

/**
 * A context for {@link io.helidon.webserver.http.spi.SinkProvider}s supplied
 * at creation time.
 */
public interface SinkProviderContext {

    /**
     * Obtains the server response associated with this context.
     *
     * @return the server response
     */
    ServerResponse serverResponse();

    /**
     * Obtains the server request associated with this context.
     *
     * @return the server response
     */
    default ServerRequest serverRequest() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * Obtains access to the connection context.
     *
     * @return the connection context
     */
    ConnectionContext connectionContext();

    /**
     * Entity output stream supplied by protocols that cannot expose the connection data writer directly to a sink provider.
     * <p>
     * An empty result indicates that the sink provider must use the connection data writer. When present, the protocol
     * supplies the fully decorated response entity stream, including response filters and content encoding, and the sink
     * provider is responsible for closing it. The sink provider must write unencoded entity bytes and must not apply these
     * transformations again.
     * <p>
     * A protocol that supplies an entity stream invokes {@code responsePreparation} exactly once, after
     * {@link io.helidon.webserver.http.ServerResponse#beforeSend(Runnable)} listeners and before constructing response filters
     * or content encoding. If response preparation fails, the protocol must not construct or return an entity stream. An
     * implementation that returns an empty result does not invoke {@code responsePreparation}.
     * <p>
     * The sink provider must close the stream before invoking
     * {@link io.helidon.webserver.http.spi.SinkProviderContext#closeRunnable()} so stream wrappers can write their final bytes
     * before the protocol response is committed.
     *
     * @param responsePreparation final response preparation
     * @return entity output stream, or an empty optional if the protocol does not provide one
     */
    default Optional<OutputStream> entityOutputStream(Runnable responsePreparation) {
        Objects.requireNonNull(responsePreparation);
        return Optional.empty();
    }

    /**
     * Runnable to execute to close the response.
     *
     * @return the close runnable
     */
    Runnable closeRunnable();
}
