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

package io.helidon.webserver;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow.Publisher;
import java.util.function.Function;
import java.util.function.Predicate;

import io.helidon.common.GenericType;
import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.common.reactive.Single;
import io.helidon.media.common.MessageBodyContext;
import io.helidon.media.common.MessageBodyFilter;
import io.helidon.media.common.MessageBodyStreamWriter;
import io.helidon.media.common.MessageBodyWriter;
import io.helidon.media.common.MessageBodyWriterContext;
import io.helidon.tracing.config.SpanTracingConfig;
import io.helidon.tracing.config.TracingConfigUtil;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;

/**
 * The basic implementation of {@link ServerResponse}.
 */
abstract class Response implements ServerResponse {

    private static final String TRACING_CONTENT_WRITE = "content-write";

    private final WebServer webServer;
    private final BareResponse bareResponse;
    private final HashResponseHeaders headers;

    private final CompletionStage<ServerResponse> completionStage;
    private final MessageBodyWriterContext writerContext;
    private final MessageBodyEventListener eventListener;

    // Content related
    private final SendLockSupport sendLockSupport;

    /**
     * Creates new instance.
     *
     * @param webServer a web server.
     * @param bareResponse an implementation of the response SPI.
     */
    Response(WebServer webServer, BareResponse bareResponse, List<MediaType> acceptedTypes) {
        this.webServer = webServer;
        this.bareResponse = bareResponse;
        this.headers = new HashResponseHeaders(bareResponse);
        this.completionStage = bareResponse.whenCompleted().thenApply(a -> this);
        this.sendLockSupport = new SendLockSupport();
        this.eventListener = new MessageBodyEventListener();
        this.writerContext = MessageBodyWriterContext.create(webServer.writerContext(), eventListener, headers, acceptedTypes);
    }

    /**
     * Creates clone of existing instance.
     *
     * @param response a response to clone.
     */
    Response(Response response) {
        this.webServer = response.webServer;
        this.bareResponse = response.bareResponse;
        this.headers = response.headers;
        this.completionStage = response.completionStage;
        this.sendLockSupport = response.sendLockSupport;
        this.writerContext = response.writerContext;
        this.eventListener = response.eventListener;
    }

    /**
     * Returns a span context related to the current request.
     * <p>
     * {@code SpanContext} is a tracing component from
     * <a href="http://opentracing.io">opentracing.io</a> standard.
     * </p>
     *
     * @return the related span context or empty if not enabled
     */
    abstract Optional<SpanContext> spanContext();

    @Override
    public WebServer webServer() {
        return webServer;
    }

    @Override
    public Http.ResponseStatus status() {
        Http.ResponseStatus status = headers.httpStatus();
        return (null == status) ? Http.Status.OK_200 : status;
    }

    @Override
    public Response status(Http.ResponseStatus status) {
        Objects.requireNonNull(status, "Parameter 'status' was null!");
        headers.httpStatus(status);
        return this;
    }

    @Override
    public ResponseHeaders headers() {
        return headers;
    }

    @Override
    public MessageBodyWriterContext writerContext() {
        return writerContext;
    }

    private Span createWriteSpan(GenericType<?> type) {
        Optional<SpanContext> parentSpan = spanContext();
        if (!parentSpan.isPresent()) {
            // we only trace write span if there is a parent
            // (parent is either webserver HTTP Request span, or inherited span
            // from request
            return null;
        }

        SpanTracingConfig spanConfig = TracingConfigUtil.spanConfig(
                NettyWebServer.TRACING_COMPONENT, TRACING_CONTENT_WRITE);

        if (spanConfig.enabled()) {
            String spanName = spanConfig.newName().orElse(TRACING_CONTENT_WRITE);
            Tracer.SpanBuilder spanBuilder = WebTracingConfig.tracer(webServer())
                    .buildSpan(spanName)
                    .asChildOf(parentSpan.get());
            if (type != null) {
                spanBuilder.withTag("response.type", type.getTypeName());
            }
            return spanBuilder.start();
        }
        return null;
    }

    @Override
    public Void send(Throwable content) {
        if (headers.httpStatus() == null) {
            if (content instanceof HttpException) {
                status(((HttpException) content).status());
            } else {
                status(Http.Status.INTERNAL_SERVER_ERROR_500);
            }
        }
        send((Object) content);
        return null;
    }

    @Override
    public <T> CompletionStage<ServerResponse> send(T content) {
        try {
            sendLockSupport.execute(() -> {
                Publisher<DataChunk> sendPublisher = writerContext.marshall(
                        Single.just(content), GenericType.create(content));
                sendLockSupport.contentSend = true;
                sendPublisher.subscribe(bareResponse);
            }, content == null);
            return whenSent();
        } catch (RuntimeException | Error e) {
            eventListener.finish();
            throw e;
        }
    }

    @Override
    public CompletionStage<ServerResponse> send(Publisher<DataChunk> content) {
        try {
            Publisher<DataChunk> sendPublisher = writerContext.applyFilters(content);
            sendLockSupport.execute(() -> {
                sendLockSupport.contentSend = true;
                sendPublisher.subscribe(bareResponse);
            }, content == null);
            return whenSent();
        } catch (RuntimeException | Error e) {
            eventListener.finish();
            throw e;
        }
    }

    @Override
    public CompletionStage<ServerResponse> send() {
        return send((Publisher<DataChunk>) null);
    }

    @Override
    public <T> CompletionStage<ServerResponse> send(Publisher<T> content, Class<T> itemClass) {
        try {
            sendLockSupport.execute(() -> {
                GenericType<T> type = GenericType.create(itemClass);
                Publisher<DataChunk> sendPublisher = writerContext.marshallStream(content, type, null);
                sendLockSupport.contentSend = true;
                sendPublisher.subscribe(bareResponse);
            }, content == null);
            return whenSent();
        } catch (RuntimeException | Error e) {
            eventListener.finish();
            throw e;
        }
    }

    @Override
    public Response registerWriter(MessageBodyWriter<?> writer) {
        writerContext.registerWriter(writer);
        return this;
    }

    @Override
    public Response registerWriter(MessageBodyStreamWriter<?> writer) {
        writerContext.registerWriter(writer);
        return this;
    }

    @Override
    public Response registerFilter(MessageBodyFilter filter) {
        writerContext.registerFilter(filter);
        return this;
    }

    @Override
    public Response registerFilter(Function<Publisher<DataChunk>, Publisher<DataChunk>> function) {
        writerContext.registerFilter(function);
        return this;
    }

    @Override
    public <T> Response registerWriter(Class<T> type, Function<T, Publisher<DataChunk>> function) {
        writerContext.registerWriter(type, function);
        return this;
    }

    @Override
    public <T> Response registerWriter(Predicate<?> accept, Function<T, Publisher<DataChunk>> function) {
        writerContext.registerWriter(accept, function);
        return this;
    }

    @Override
    public <T> Response registerWriter(Class<T> type, MediaType contentType,
            Function<? extends T, Publisher<DataChunk>> function) {

        writerContext.registerWriter(type, contentType, function);
        return this;
    }

    @Override
    public <T> Response registerWriter(Predicate<?> accept, MediaType contentType,
            Function<T, Publisher<DataChunk>> function) {

        writerContext.registerWriter(accept, contentType, function);
        return this;
    }

    @Override
    public CompletionStage<ServerResponse> whenSent() {
        return completionStage;
    }

    @Override
    public long requestId() {
        return bareResponse.requestId();
    }

    private final class MessageBodyEventListener implements MessageBodyContext.EventListener {

        private Span span;
        private volatile boolean sent;

        private synchronized void sendHeadersIfNeeded() {
            if (headers != null && !sent) {
                sent = true;
                headers.send();
            }
        }

        void finish() {
            if (span != null) {
                span.finish();
            }
        }

        @Override
        public void onEvent(MessageBodyContext.Event event) {
            switch (event.eventType()) {
                case BEFORE_ONSUBSCRIBE:
                    GenericType<?> type = event.entityType().orElse(null);
                    span = createWriteSpan(type);
                    break;
                case BEFORE_ONNEXT:
                    sendHeadersIfNeeded();
                    break;
                case AFTER_ONERROR:
                    if (span != null) {
                        span.finish();
                    }
                    break;
                case BEFORE_ONCOMPLETE:
                    sendHeadersIfNeeded();
                    break;
                case AFTER_ONCOMPLETE:
                    finish();
                    break;
                default:
                    // do nothing
            }
        }
    }

    private static class SendLockSupport {

        private boolean contentSend = false;

        private synchronized void execute(Runnable runnable, boolean silentSendStatus) {
            // test effective close
            if (contentSend) {
                if (silentSendStatus) {
                    return;
                } else {
                    throw new IllegalStateException("Response is already sent!");
                }
            }
            runnable.run();
        }
    }
}
