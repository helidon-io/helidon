/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.webserver.sse;

import java.util.function.BiConsumer;

import io.helidon.common.GenericType;
import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.sse.SseEvent;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.http.spi.Sink;
import io.helidon.webserver.http.spi.SinkProvider;
import io.helidon.webserver.http.spi.SinkProviderContext;

/**
 * Sink provider for SSE type.
 *
 * @see io.helidon.webserver.http.spi.SinkProvider
 */
public class SseSinkProvider implements SinkProvider<SseEvent> {

    @Override
    public boolean supports(GenericType<? extends Sink<?>> type, ServerRequest request) {
        return SseSink.TYPE.equals(type) && request.headers().isAccepted(MediaTypes.TEXT_EVENT_STREAM);
    }

    /**
     * Creates a Sink for SSE events.
     *
     * @param context the context
     * @return newly created sink
     * @param <X> type of sink
     */
    @Override
    @SuppressWarnings("unchecked")
    public <X extends Sink<SseEvent>> X create(SinkProviderContext context) {
        return (X) new DataWriterSseSink(context);
    }

    /**
     * Creates a Sink for SSE events.
     *
     * @param response the HTTP response
     * @param eventConsumer an event consumer
     * @param closeRunnable a runnable to call on close
     * @param <X> type of sink
     * @return newly created sink
     * @deprecated replaced by {@link #create(SinkProviderContext)}
     */
    @Override
    @Deprecated(since = "4.1.2", forRemoval = true)
    public <X extends Sink<SseEvent>> X create(ServerResponse response,
                                               BiConsumer<Object, MediaType> eventConsumer,
                                               Runnable closeRunnable) {
        return (X) new OutputStreamSseSink(response, eventConsumer, closeRunnable);
    }
}
