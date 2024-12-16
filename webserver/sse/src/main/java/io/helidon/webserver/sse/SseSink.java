/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

import io.helidon.common.GenericType;
import io.helidon.http.sse.SseEvent;
import io.helidon.webserver.http.spi.Sink;

/**
 * A sink for SSE events.
 */
public interface SseSink extends Sink<SseEvent> {

    /**
     * Type of SSE event sinks.
     */
    GenericType<SseSink> TYPE = GenericType.create(SseSink.class);

    /**
     * Emits an event using to the sink.
     *
     * @param event the event to emit
     * @return this sink
     */
    @Override
    SseSink emit(SseEvent event);

    /**
     * Close SSE sink.
     */
    @Override
    void close();
}
