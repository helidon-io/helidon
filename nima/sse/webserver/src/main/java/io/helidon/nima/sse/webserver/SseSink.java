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
import io.helidon.nima.webserver.http.spi.Sink;
import io.helidon.nima.webserver.http1.Http1ServerResponse;

public class SseSink extends SseResponse implements Sink<SseEvent> {
    public static final GenericType<SseSink> TYPE = GenericType.create(SseSink.class);

    private final Consumer<Object> eventConsumer;
    private final Runnable closeRunnable;

    SseSink(Http1ServerResponse serverResponse, Consumer<Object> eventConsumer, Runnable closeRunnable) {
        super(serverResponse);
        this.eventConsumer = eventConsumer;
        this.closeRunnable = closeRunnable;
    }

    @Override
    public SseSink emit(SseEvent event) {
        eventConsumer.accept(event);
        send(event);
        return this;
    }

    @Override
    public void close() {
        closeRunnable.run();
    }
}
