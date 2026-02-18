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

import java.util.function.Function;

import io.helidon.common.GenericType;
import io.helidon.common.buffers.BufferData;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.media.MediaContext;
import io.helidon.http.media.ReadableEntity;
import io.helidon.http.media.ReadableEntityBase;

/**
 * Client response entity.
 */
public final class ClientResponseEntity extends ReadableEntityBase implements ReadableEntity {
    private final ClientRequestHeaders requestHeaders;
    private final ClientResponseHeaders responseHeaders;
    private final MediaContext mediaContext;

    private ClientResponseEntity(Function<Integer, BufferData> readEntityFunction,
                                 Runnable entityProcessedRunnable,
                                 ClientRequestHeaders requestHeaders,
                                 ClientResponseHeaders responseHeaders,
                                 MediaContext mediaContext,
                                 long maxBufferedEntitySize) {
        super(readEntityFunction, entityProcessedRunnable, maxBufferedEntitySize);

        this.requestHeaders = requestHeaders;
        this.responseHeaders = responseHeaders;
        this.mediaContext = mediaContext;
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
                                        maxBufferedEntitySize);
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
                                        maxBufferedEntitySize());
    }

    @Override
    protected <T> T entityAs(GenericType<T> type) {
        return mediaContext.reader(type, requestHeaders, responseHeaders)
                .read(type, inputStream(), requestHeaders, responseHeaders);
    }
}

