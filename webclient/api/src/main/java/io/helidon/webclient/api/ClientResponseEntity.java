/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import io.helidon.common.Api;
import io.helidon.common.GenericType;
import io.helidon.common.buffers.BufferData;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.media.EntityReader;
import io.helidon.http.media.MediaContext;
import io.helidon.http.media.ReadableEntity;
import io.helidon.http.media.ReadableEntityBase;

/**
 * Client response entity.
 */
@Api.Internal
public final class ClientResponseEntity extends ReadableEntityBase implements ReadableEntity {
    private final ClientRequestHeaders requestHeaders;
    private final ClientResponseHeaders responseHeaders;
    private final MediaContext mediaContext;
    private final Optional<Consumer<Runnable>> closeResponse;

    private ClientResponseEntity(Function<Integer, BufferData> readEntityFunction,
                                 Runnable entityProcessedRunnable,
                                 ClientRequestHeaders requestHeaders,
                                 ClientResponseHeaders responseHeaders,
                                 MediaContext mediaContext,
                                 long maxBufferedEntitySize,
                                 Optional<Consumer<Runnable>> closeResponse) {
        super(readEntityFunction, entityProcessedRunnable, maxBufferedEntitySize);

        this.requestHeaders = requestHeaders;
        this.responseHeaders = responseHeaders;
        this.mediaContext = mediaContext;
        this.closeResponse = closeResponse;
    }

    /**
     * Create a new client response entity.
     *
     * @param readEntityFunction      function to read bytes from entity based on suggested buffer length
     * @param entityProcessedRunnable runnable to run when entity processing finishes
     * @param requestHeaders          request headers
     * @param responseHeaders         response headers
     * @param mediaContext            media context to read into specific types
     * @param maxBufferedEntitySize   maximum size of a buffered entity
     * @return client response entity
     */
    public static ClientResponseEntity create(Function<Integer, BufferData> readEntityFunction,
                                              Runnable entityProcessedRunnable,
                                              ClientRequestHeaders requestHeaders,
                                              ClientResponseHeaders responseHeaders,
                                              MediaContext mediaContext,
                                              long maxBufferedEntitySize) {
        return new ClientResponseEntity(readEntityFunction,
                                        entityProcessedRunnable,
                                        requestHeaders,
                                        responseHeaders,
                                        mediaContext,
                                        maxBufferedEntitySize,
                                        Optional.empty());
    }

    /**
     * Create a client response entity whose streaming readers can close the owning response.
     * The completion callback is invoked when the entity reaches EOF. The close callback is invoked when a streaming
     * reader closes its input, including when it stops before EOF, and must support repeated invocation. It receives
     * a runnable that closes the entity input and observes completion if it reaches EOF. Closing that input may request
     * more entity bytes, so the callback should invoke it only when further reads are safe.
     *
     * @param readEntityFunction function to read bytes from the entity based on suggested buffer length
     * @param entityProcessedRunnable runnable to run when entity processing finishes
     * @param closeResponse callback to close the owning response, receiving the entity input's close operation
     * @param requestHeaders request headers
     * @param responseHeaders response headers
     * @param mediaContext media context to read into specific types
     * @param maxBufferedEntitySize maximum size of a buffered entity
     * @return client response entity
     */
    public static ClientResponseEntity create(Function<Integer, BufferData> readEntityFunction,
                                              Runnable entityProcessedRunnable,
                                              Consumer<Runnable> closeResponse,
                                              ClientRequestHeaders requestHeaders,
                                              ClientResponseHeaders responseHeaders,
                                              MediaContext mediaContext,
                                              long maxBufferedEntitySize) {
        return new ClientResponseEntity(Objects.requireNonNull(readEntityFunction),
                                        Objects.requireNonNull(entityProcessedRunnable),
                                        Objects.requireNonNull(requestHeaders),
                                        Objects.requireNonNull(responseHeaders),
                                        Objects.requireNonNull(mediaContext),
                                        maxBufferedEntitySize,
                                        Optional.of(Objects.requireNonNull(closeResponse)));
    }

    /**
     * Close a streaming result using this entity's configured response cleanup operation.
     * The supplied operation closes the caller's input and observes its completion if it reaches EOF.
     * The response cleanup operation invokes it only when further reads are safe. When this entity was
     * created without a response cleanup operation, this method does nothing.
     *
     * @param closeStream operation that closes the caller's entity input
     */
    public void closeStreaming(Runnable closeStream) {
        Objects.requireNonNull(closeStream);
        closeResponse.ifPresent(operation -> operation.accept(closeStream));
    }

    @Override
    public ReadableEntity copy(Runnable entityProcessedRunnable) {
        return new ClientResponseEntity(readEntityFunction(),
                                        () -> {
                                            entityProcessedRunnable.run();
                                            entityProcessedRunnable().run();
                                        },
                                        requestHeaders,
                                        responseHeaders,
                                        mediaContext,
                                        maxBufferedEntitySize(),
                                        closeResponse);
    }

    @Override
    protected <T> T entityAs(GenericType<T> type) {
        EntityReader<T> reader = mediaContext.reader(type, requestHeaders, responseHeaders);
        InputStream stream = reader.streaming() && !bufferedEntityAvailable() && closeResponse.isPresent()
                ? new ResponseInputStream(inputStream(), closeResponse.get())
                : inputStream();
        return readerContext().read(reader, type, stream, requestHeaders, responseHeaders);
    }

    private static final class ResponseInputStream extends FilterInputStream {
        private final Consumer<Runnable> closeResponse;
        private boolean closed;

        private ResponseInputStream(InputStream delegate, Consumer<Runnable> closeResponse) {
            super(delegate);
            this.closeResponse = closeResponse;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                closeResponse.accept(() -> {
                    try {
                        in.close();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }
        }
    }
}
