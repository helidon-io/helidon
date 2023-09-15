/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.webserver.http2;

import java.io.InputStream;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import io.helidon.common.GenericType;
import io.helidon.common.buffers.BufferData;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.media.MediaContext;
import io.helidon.http.media.ReadableEntity;
import io.helidon.http.media.ReadableEntityBase;

/**
 * Server request entity.
 */
public final class Http2ServerRequestEntity extends ReadableEntityBase implements ReadableEntity {
    private final ServerRequestHeaders requestHeaders;
    private final MediaContext mediaContext;
    private final UnaryOperator<InputStream> decoder;
    private final UnaryOperator<InputStream> streamFilter;

    private Http2ServerRequestEntity(UnaryOperator<InputStream> streamFilter,
                                     UnaryOperator<InputStream> decoder,
                                     Function<Integer, BufferData> readEntityFunction,
                                     Runnable entityProcessedRunnable,
                                     ServerRequestHeaders requestHeaders,
                                     MediaContext mediaContext) {
        super(readEntityFunction, entityProcessedRunnable);

        this.streamFilter = streamFilter;
        this.decoder = decoder;
        this.requestHeaders = requestHeaders;
        this.mediaContext = mediaContext;
    }

    /**
     * Create a new entity.
     *
     * @param streamFilter            stream filter to apply to the stream, provided by user
     * @param decoder                 content decoder
     * @param readEntityFunction      function to read buffer from entity (int is an estimated number of bytes needed, buffer
     *                                will contain at least 1 byte)
     * @param entityProcessedRunnable runnable to run once the entity is fully read
     * @param requestHeaders          request headers
     * @param mediaContext            media context to map to correct types
     * @return a new entity
     */
    public static Http2ServerRequestEntity create(UnaryOperator<InputStream> streamFilter,
                                                  UnaryOperator<InputStream> decoder,
                                                  Function<Integer, BufferData> readEntityFunction,
                                                  Runnable entityProcessedRunnable,
                                                  ServerRequestHeaders requestHeaders,
                                                  MediaContext mediaContext) {
        return new Http2ServerRequestEntity(streamFilter,
                                            decoder,
                                            readEntityFunction,
                                            entityProcessedRunnable,
                                            requestHeaders,
                                            mediaContext);
    }

    @Override
    public ReadableEntity copy(Runnable entityProcessedRunnable) {
        return new Http2ServerRequestEntity(streamFilter,
                                            decoder,
                                            readEntityFunction(), () -> {
            entityProcessedRunnable.run();
            entityProcessedRunnable().run();
        },
                                            requestHeaders,
                                            mediaContext);
    }

    @Override
    public InputStream inputStream() {
        return streamFilter.apply(decoder.apply(super.inputStream()));
    }

    @Override
    protected <T> T entityAs(GenericType<T> type) {
        return mediaContext.reader(type, requestHeaders)
                .read(type, inputStream(), requestHeaders);
    }
}
