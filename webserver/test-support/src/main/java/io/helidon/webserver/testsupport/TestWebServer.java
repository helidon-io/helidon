/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

package io.helidon.webserver.testsupport;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.helidon.common.http.ContextualRegistry;
import io.helidon.media.common.MediaContext;
import io.helidon.media.common.MessageBodyReaderContext;
import io.helidon.media.common.MessageBodyWriterContext;
import io.helidon.webserver.ServerConfiguration;
import io.helidon.webserver.WebServer;

/**
 * Kind of WebServer mock for tests.
 */
class TestWebServer implements WebServer {

    private static final MediaContext DEFAULT_MEDIA_SUPPORT = MediaContext.create();

    private final CompletableFuture<WebServer> startFuture = new CompletableFuture<>();
    private final CompletableFuture<WebServer> shutdownFuture = new CompletableFuture<>();
    private final ContextualRegistry context = ContextualRegistry.create();
    private final ServerConfiguration configuration = ServerConfiguration.builder().build();
    private final MediaContext mediaContext;

    TestWebServer() {
        this.mediaContext = DEFAULT_MEDIA_SUPPORT;
    }

    TestWebServer(MediaContext mediaContext) {
        if (mediaContext == null) {
            this.mediaContext = DEFAULT_MEDIA_SUPPORT;
        } else {
            this.mediaContext = mediaContext;
        }
    }

    @Override
    public ServerConfiguration configuration() {
        return configuration;
    }

    @Override
    public CompletionStage<WebServer> start() {
        if (shutdownFuture.isDone()) {
            throw new IllegalStateException("Cannot start over!");
        }
        startFuture.complete(this);
        return startFuture;
    }

    @Override
    public CompletionStage<WebServer> shutdown() {
        shutdownFuture.complete(this);
        return shutdownFuture;
    }

    @Override
    public CompletionStage<WebServer> whenShutdown() {
        return shutdownFuture;
    }

    @Override
    public boolean isRunning() {
        return !shutdownFuture.isDone();
    }

    @Override
    public ContextualRegistry context() {
        return context;
    }

    @Override
    public MessageBodyReaderContext readerContext() {
        return mediaContext.readerContext();
    }

    @Override
    public MessageBodyWriterContext writerContext() {
        return mediaContext.writerContext();
    }

    @Override
    public int port(String name) {
        return 0;
    }
}
