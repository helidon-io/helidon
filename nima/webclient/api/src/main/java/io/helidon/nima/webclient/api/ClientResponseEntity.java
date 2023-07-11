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

package io.helidon.nima.webclient.api;

import java.io.InputStream;
import java.util.function.Function;

import io.helidon.common.GenericType;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.http.ClientRequestHeaders;
import io.helidon.common.http.ClientResponseHeaders;
import io.helidon.nima.http.encoding.ContentDecoder;
import io.helidon.nima.http.media.MediaContext;
import io.helidon.nima.http.media.ReadableEntity;
import io.helidon.nima.http.media.ReadableEntityBase;

/**
 * Client response entity.
 */
public final class ClientResponseEntity extends ReadableEntityBase implements ReadableEntity {
    private final ClientRequestHeaders requestHeaders;
    private final ClientResponseHeaders responseHeaders;
    private final MediaContext mediaContext;

    private ClientResponseEntity(Function<InputStream, InputStream> decoder,
                                 Function<Integer, BufferData> readEntityFunction,
                                 Runnable entityProcessedRunnable,
                                 ClientRequestHeaders requestHeaders,
                                 ClientResponseHeaders responseHeaders,
                                 MediaContext mediaContext) {
        super(decoder, readEntityFunction, entityProcessedRunnable);

        this.requestHeaders = requestHeaders;
        this.responseHeaders = responseHeaders;
        this.mediaContext = mediaContext;
    }

    /**
     * Create a new client response entity.
     *
     * @param decoder                 content decoder
     * @param readEntityFunction      function to read bytes from entity based on suggested buffer length
     * @param entityProcessedRunnable runnable to run when entity processing finishes
     * @param requestHeaders          request headers
     * @param responseHeaders         response headers
     * @param mediaContext            media context to read into specific types
     * @return client response entity
     */
    public static ClientResponseEntity create(ContentDecoder decoder,
                                              Function<Integer, BufferData> readEntityFunction,
                                              Runnable entityProcessedRunnable,
                                              ClientRequestHeaders requestHeaders,
                                              ClientResponseHeaders responseHeaders,
                                              MediaContext mediaContext) {
        return new ClientResponseEntity(decoder,
                                        readEntityFunction,
                                        entityProcessedRunnable,
                                        requestHeaders,
                                        responseHeaders,
                                        mediaContext);
    }

    @Override
    public ReadableEntity copy(Runnable entityProcessedRunnable) {
        return new ClientResponseEntity(contentDecoder(),
                                        readEntityFunction(), () -> {
            entityProcessedRunnable.run();
            entityProcessedRunnable().run();
        },
                                        requestHeaders,
                                        responseHeaders,
                                        mediaContext);
    }

    @Override
    protected <T> T entityAs(GenericType<T> type) {
        return mediaContext.reader(type, requestHeaders, responseHeaders)
                .read(type, inputStream(), requestHeaders, responseHeaders);
    }
}

