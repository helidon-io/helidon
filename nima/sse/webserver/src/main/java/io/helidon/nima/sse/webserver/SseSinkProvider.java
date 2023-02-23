/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.sse.webserver;

import java.util.function.BiConsumer;

import io.helidon.common.GenericType;
import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.nima.sse.SseEvent;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;
import io.helidon.nima.webserver.http.spi.Sink;
import io.helidon.nima.webserver.http.spi.SinkProvider;

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
    public <X extends Sink<SseEvent>> X create(ServerResponse response, BiConsumer<Object, MediaType> eventConsumer,
                                        Runnable closeRunnable) {
        return (X) new SseSink(response, eventConsumer, closeRunnable);
    }
}
