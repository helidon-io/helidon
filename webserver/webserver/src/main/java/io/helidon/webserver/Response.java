/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.function.Function;
import java.util.function.Predicate;

import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.common.reactive.Single;
import io.helidon.media.common.ContentWriters;
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

    // Content related
    private final SendLockSupport sendLockSupport;
    private final ArrayList<Writer<?>> writers;
    private final ArrayList<Function<Flow.Publisher<DataChunk>, Flow.Publisher<DataChunk>>> filters;

    /**
     * Creates new instance.
     *
     * @param webServer a web server.
     * @param bareResponse an implementation of the response SPI.
     */
    Response(WebServer webServer, BareResponse bareResponse) {
        this.webServer = webServer;
        this.bareResponse = bareResponse;
        this.headers = new HashResponseHeaders(bareResponse);
        this.completionStage = bareResponse.whenCompleted().thenApply(a -> this);
        this.sendLockSupport = new SendLockSupport();
        this.writers = new ArrayList<>();
        this.filters = new ArrayList<>();
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
        this.writers = response.writers;
        this.filters = response.filters;
    }

    /**
     * Returns a span context related to the current request.
     * <p>
     * {@code SpanContext} is a tracing component from <a href="http://opentracing.io">opentracing.io</a> standard.
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
        return headers.httpStatus();
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

    private <T> Span createWriteSpan(T obj) {
        Optional<SpanContext> parentSpan = spanContext();
        if (!parentSpan.isPresent()) {
            // we only trace write span if there is a parent (parent is either webserver HTTP Request span, or inherited span
            // from request
            return null;
        }

        SpanTracingConfig spanConfig = TracingConfigUtil.spanConfig(NettyWebServer.TRACING_COMPONENT, TRACING_CONTENT_WRITE);

        if (spanConfig.enabled()) {
            String spanName = spanConfig.newName().orElse(TRACING_CONTENT_WRITE);
            Tracer.SpanBuilder spanBuilder = WebTracingConfig.tracer(webServer()).buildSpan(spanName)
                .asChildOf(parentSpan.get());

            if (obj != null) {
                spanBuilder.withTag("response.type", obj.getClass().getName());
            }
            return spanBuilder.start();
        }

        return null;
    }

    @Override
    public <T> CompletionStage<ServerResponse> send(T content) {
        Span writeSpan = createWriteSpan(content);
        try {
            sendLockSupport.execute(() -> {
                Flow.Publisher<DataChunk> publisher = createPublisherUsingWriter(content);
                if (publisher == null) {
                    throw new IllegalArgumentException("Cannot write! No registered writer for '"
                                                               + content.getClass().toString() + "'.");
                }
                Flow.Publisher<DataChunk> p = applyFilters(publisher, writeSpan);
                sendLockSupport.contentSend = true;
                p.subscribe(bareResponse);
            }, content == null);
            return whenSent();
        } catch (RuntimeException | Error e) {
            if (null != writeSpan) {
                writeSpan.finish();
            }
            throw e;
        }
    }

    @Override
    public CompletionStage<ServerResponse> send(Flow.Publisher<DataChunk> content) {
        Span writeSpan = createWriteSpan(content);
        try {
            Flow.Publisher<DataChunk> publisher = (content == null)
                    ? Single.empty() : content;
            sendLockSupport.execute(() -> {
                Flow.Publisher<DataChunk> p = applyFilters(publisher, writeSpan);
                sendLockSupport.contentSend = true;
                p.subscribe(bareResponse);
            }, content == null);
            return whenSent();
        } catch (RuntimeException | Error e) {
            if (null != writeSpan) {
                writeSpan.finish();
            }
            throw e;
        }
    }

    @Override
    public CompletionStage<ServerResponse> send() {
        return send(null);
    }

    @SuppressWarnings("unchecked")
    <T> Flow.Publisher<DataChunk> createPublisherUsingWriter(T content) {
        if (content == null) {
            return Single.empty();
        }

        // Try to get a publisher from registered writers
        synchronized (sendLockSupport) {
            for (int i = writers.size() - 1; i >= 0; i--) {
                Writer<T> writer = (Writer<T>) writers.get(i);
                if (writer.accept(content)) {
                    return writer.function.apply(content);
                }
            }
        }

        return createDefaultPublisher(content);
    }

    private <T> Flow.Publisher<DataChunk> createDefaultPublisher(T content) {
        final Class<?> type = content.getClass();
        if (File.class.isAssignableFrom(type)) {
            return toPublisher(((File) content).toPath());
        } else if (Path.class.isAssignableFrom(type)) {
            return toPublisher((Path) content);
        } else if (ReadableByteChannel.class.isAssignableFrom(type)) {
            return ContentWriters.byteChannelWriter().apply((ReadableByteChannel) content);
        } else if (CharSequence.class.isAssignableFrom(type)) {
            return toPublisher((CharSequence) content);
        } else if (byte[].class.isAssignableFrom(type)) {
            return ContentWriters.byteArrayWriter(true).apply((byte[]) content);
        }
        return null;
    }

    private Flow.Publisher<DataChunk> toPublisher(CharSequence s) {
        MediaType mediaType = headers.contentType().orElse(MediaType.TEXT_PLAIN);
        String charset = mediaType.charset().orElse(StandardCharsets.UTF_8.name());
        headers.contentType(mediaType.withCharset(charset));
        return ContentWriters.charSequenceWriter(Charset.forName(charset)).apply(s);
    }

    private Flow.Publisher<DataChunk> toPublisher(Path path) {
        // Set response length - if possible
        try {
            // Is it existing and readable file
            if (!Files.exists(path)) {
                throw new IllegalArgumentException("File path argument doesn't exist!");
            }
            if (!Files.isRegularFile(path)) {
                throw new IllegalArgumentException("File path argument isn't a file!");
            }
            if (!Files.isReadable(path)) {
                throw new IllegalArgumentException("File path argument isn't readable!");
            }
            // Try to write length
            try {
                headers.contentLength(Files.size(path));
            } catch (Exception e) {
                // Cannot get length or write length, not a big deal
            }
            // And write
            FileChannel fc = FileChannel.open(path, StandardOpenOption.READ);
            return ContentWriters.byteChannelWriter().apply(fc);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot read a file!", e);
        }
    }

    @Override
    public <T> Response registerWriter(Class<T> type, Function<T, Flow.Publisher<DataChunk>> function) {
        return registerWriter(type, null, function);
    }

    @Override
    public <T> Response registerWriter(Class<T> type,
                                       MediaType contentType,
                                       Function<? extends T, Flow.Publisher<DataChunk>> function) {
        sendLockSupport.execute(() -> writers.add(new Writer<>(type, contentType, function)), false);
        return this;
    }

    @Override
    public <T> Response registerWriter(Predicate<?> accept, Function<T, Flow.Publisher<DataChunk>> function) {
        return registerWriter(accept, null, function);
    }

    @Override
    public <T> Response registerWriter(Predicate<?> accept,
                                       MediaType contentType,
                                       Function<T, Flow.Publisher<DataChunk>> function) {
        sendLockSupport.execute(() -> writers.add(new Writer<>(accept, contentType, function)), false);
        return this;
    }

    @Override
    public Response registerFilter(Function<Flow.Publisher<DataChunk>, Flow.Publisher<DataChunk>> function) {
        Objects.requireNonNull(function, "Parameter 'function' is null!");
        sendLockSupport.execute(() -> filters.add(function), false);
        return this;
    }

    Flow.Publisher<DataChunk> applyFilters(Flow.Publisher<DataChunk> publisher, Span span) {
        Objects.requireNonNull(publisher, "Parameter 'publisher' is null!");
        for (Function<Flow.Publisher<DataChunk>, Flow.Publisher<DataChunk>> filter : filters) {
            Flow.Publisher<DataChunk> p = filter.apply(publisher);
            if (p != null) {
                publisher = p;
            }
        }
        return new SendHeadersFirstPublisher<>(headers, span, publisher);
    }

    @Override
    public CompletionStage<ServerResponse> whenSent() {
        return completionStage;
    }

    class Writer<T> {
        private final Predicate<Object> acceptPredicate;
        private final MediaType requestedContentType;
        private final Function<T, Flow.Publisher<DataChunk>> function;

        Writer(Predicate acceptPredicate, MediaType contentType, Function<T, Flow.Publisher<DataChunk>> function) {
            Objects.requireNonNull(function, "Parameter function is null!");
            this.acceptPredicate = acceptPredicate == null ? o -> true : acceptPredicate;
            this.requestedContentType = contentType;
            this.function = function;
        }

        Writer(Class<?> acceptType, MediaType contentType, Function<T, Flow.Publisher<DataChunk>> function) {
            this(acceptType == null ? null : (Predicate) o -> acceptType.isAssignableFrom(o.getClass()),
                 contentType,
                 function);
        }

        boolean accept(Object o) {
            if (o == null || !acceptPredicate.test(o)) {
                return false;
            }

            // Test content type compatibility
            return requestedContentType == null
                    || headers().contentType()
                                .or(() -> { // if no contentType is yet registered, try to write requested
                                    try {
                                        headers.contentType(requestedContentType);
                                        return Optional.of(requestedContentType);
                                    } catch (Exception e) {
                                        return Optional.empty();
                                    }
                                })
                                .filter(requestedContentType) // MediaType is a predicate of compatible media type
                                .isPresent();
        }
    }

    @Override
    public long requestId() {
        return bareResponse.requestId();
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
