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
 */
public class SseSinkProvider implements SinkProvider<SseEvent> {

    @Override
    public boolean supports(GenericType<? extends Sink<?>> type, ServerRequest request) {
        return SseSink.TYPE.equals(type) && request.headers().isAccepted(MediaTypes.TEXT_EVENT_STREAM);
    }


    @Override
    @SuppressWarnings("unchecked")
    public <X extends Sink<SseEvent>> X create(SinkProviderContext context) {
        return (X) new SseSink(context);
    }

    @Override
    public <X extends Sink<SseEvent>> X create(ServerResponse response,
                                               BiConsumer<Object, MediaType> eventConsumer,
                                               Runnable closeRunnable) {
        throw new UnsupportedOperationException("Deprecated, use other create method in class");
    }

}
