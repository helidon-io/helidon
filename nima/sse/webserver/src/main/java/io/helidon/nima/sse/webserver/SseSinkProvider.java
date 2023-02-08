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

import java.util.function.Consumer;

import io.helidon.common.GenericType;
import io.helidon.nima.webserver.http.ServerResponse;
import io.helidon.nima.webserver.http.spi.Sink;
import io.helidon.nima.webserver.http.spi.SinkProvider;
import io.helidon.nima.webserver.http1.Http1ServerResponse;

/**
 * Sink provider for SSE type.
 */
public class SseSinkProvider implements SinkProvider<SseEvent, SseSink> {

    @Override
    public boolean supports(GenericType<? extends Sink<SseEvent>> type) {
        return type == SseSink.TYPE;
    }

    @Override
    public SseSink create(ServerResponse response, Consumer<Object> eventConsumer, Runnable closeRunnable) {
        if (response instanceof Http1ServerResponse res) {
            return new SseSink(res, eventConsumer, closeRunnable);
        }
        throw new IllegalArgumentException("SseSink can only be created from an HTTP/1 response");
    }
}
