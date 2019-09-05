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

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringTokenizer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.function.Predicate;

import io.helidon.common.CollectionsHelper;
import io.helidon.common.http.ContextualRegistry;
import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.common.http.Parameters;
import io.helidon.common.http.Reader;
import io.helidon.common.reactive.Flow;
import io.helidon.media.common.ContentReaders;
import io.helidon.tracing.config.SpanTracingConfig;
import io.helidon.tracing.config.TracingConfigUtil;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;

/**
 * The basic abstract implementation of {@link ServerRequest}.
 */
abstract class Request implements ServerRequest {
    private static final String TRACING_CONTENT_READ_NAME = "content-read";

    /**
     * The default charset to use in case that no charset or no mime-type is defined in the content type header.
     */
    static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    private final BareRequest bareRequest;
    private final WebServer webServer;
    private final ContextualRegistry context;
    private final Parameters queryParams;
    private final RequestHeaders headers;
    private final Content content;

    /**
     * Creates new instance.
     *
     * @param req bare request from HTTP SPI implementation.
     * @param webServer relevant server.
     */
    Request(BareRequest req, WebServer webServer) {
        this.bareRequest = req;
        this.webServer = webServer;
        this.context = ContextualRegistry.create(webServer.context());
        this.queryParams = UriComponent.decodeQuery(req.uri().getRawQuery(), true);
        this.headers = new HashRequestHeaders(bareRequest.headers());
        this.content = new Content();
    }

    /**
     * Creates clone of existing instance.
     *
     * @param request a request to clone.
     */
    Request(Request request) {
        this.bareRequest = request.bareRequest;
        this.webServer = request.webServer;
        this.context = request.context;
        this.queryParams = request.queryParams;
        this.headers = request.headers;
        this.content = new Content(request.content);
    }

    /**
     * Obtain the charset from the request.
     *
     * @param request the request to extract the charset from
     * @return the charset or {@link #DEFAULT_CHARSET} if none found
     */
    static Charset contentCharset(ServerRequest request) {
        return request.headers()
                      .contentType()
                      .flatMap(MediaType::charset)
                      .map(Charset::forName)
                      .orElse(DEFAULT_CHARSET);
    }

    @Override
    public WebServer webServer() {
        return webServer;
    }

    @Override
    public ContextualRegistry context() {
        return context;
    }

    @Override
    public Http.RequestMethod method() {
        return bareRequest.method();
    }

    @Override
    public Http.Version version() {
        return bareRequest.version();
    }

    @Override
    public URI uri() {
        return bareRequest.uri();
    }

    @Override
    public String query() {
        return bareRequest.uri().getRawQuery();
    }

    @Override
    public Parameters queryParams() {
        return queryParams;
    }

    @Override
    public String fragment() {
        return bareRequest.uri().getFragment();
    }

    @Override
    public String localAddress() {
        return bareRequest.localAddress();
    }

    @Override
    public int localPort() {
        return bareRequest.localPort();
    }

    @Override
    public String remoteAddress() {
        return bareRequest.remoteAddress();
    }

    @Override
    public int remotePort() {
        return bareRequest.remotePort();
    }

    @Override
    public boolean isSecure() {
        return bareRequest.isSecure();
    }

    @Override
    public RequestHeaders headers() {
        return headers;
    }

    @Override
    public Content content() {
        return this.content;
    }

    @Override
    public long requestId() {
        return bareRequest.requestId();
    }

    private static CompletableFuture failedFuture(Throwable t) {
        CompletableFuture result = new CompletableFuture<>();
        result.completeExceptionally(t);
        return result;
    }

    private static class InternalReader<T> implements Reader<T> {

        private final Predicate<Class<?>> predicate;
        private final Reader<T> reader;

        InternalReader(Predicate<Class<?>> predicate, Reader<T> reader) {
            this.predicate = predicate;
            this.reader = reader;
        }

        public boolean accept(Class<?> o) {
            return o != null && predicate != null && predicate.test(o);
        }

        @Override
        public CompletionStage<? extends T> apply(Flow.Publisher<DataChunk> publisher, Class<? super T> clazz) {
            return reader.apply(publisher, clazz);
        }
    }

    class Content implements io.helidon.common.http.Content {

        private final Flow.Publisher<DataChunk> originalPublisher;
        private final Deque<InternalReader<?>> readers;
        private final List<Function<Flow.Publisher<DataChunk>, Flow.Publisher<DataChunk>>> filters;
        private final ReadWriteLock readersLock;
        private final ReadWriteLock filtersLock;

        private Content() {
            this.originalPublisher = bareRequest.bodyPublisher();
            this.readers = appendDefaultReaders(new LinkedList<>());
            this.filters = new ArrayList<>();
            this.readersLock = new ReentrantReadWriteLock();
            this.filtersLock = new ReentrantReadWriteLock();
        }

        private Content(Content orig) {
            this.originalPublisher = orig.originalPublisher;
            this.readers = orig.readers;
            this.filters = orig.filters;
            this.readersLock = orig.readersLock;
            this.filtersLock = orig.filtersLock;
        }

        private Deque<InternalReader<?>> appendDefaultReaders(final Deque<InternalReader<?>> readers) {
            readers.addLast(reader(String.class, stringContentReader()));
            readers.addLast(reader(byte[].class, ContentReaders.byteArrayReader()));
            readers.addLast(reader(InputStream.class, ContentReaders.inputStreamReader()));
            return readers;
        }

        @Override
        public void registerFilter(Function<Flow.Publisher<DataChunk>, Flow.Publisher<DataChunk>> function) {

            Objects.requireNonNull(function, "Parameter 'function' is null!");
            try {
                filtersLock.writeLock().lock();
                filters.add(function);
            } finally {
                filtersLock.writeLock().unlock();
            }
        }

        @Override
        public <T> void registerReader(Class<T> type, Reader<T> reader) {
            register(reader(type, reader));
        }

        @Override
        public <T> void registerReader(Predicate<Class<?>> predicate, Reader<T> reader) {
            register(new InternalReader<>(predicate, reader));
        }

        public <T> void register(InternalReader<T> reader) {
            try {
                readersLock.writeLock().lock();
                readers.addFirst(reader);
            } finally {
                readersLock.writeLock().unlock();
            }
        }

        private <T> InternalReader<T> reader(Class<T> clazz, Reader<T> reader) {
            return new InternalReader<>(aClass -> clazz.isAssignableFrom(aClass), reader);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> CompletionStage<T> as(final Class<T> type) {
            Span readSpan = createReadSpan(type);
            CompletionStage<T> result;
            try {
                readersLock.readLock().lock();
                result = (CompletionStage<T>) readerFor(type).apply(chainPublishers(), type);
            } catch (IllegalArgumentException e) {
                result = failedFuture(e);
            } catch (Exception e) {
                result = failedFuture(new IllegalArgumentException("Transformation failed!", e));
            } finally {
                readersLock.readLock().unlock();
            }
            // Close span
            if (null != readSpan) {
                result.thenRun(readSpan::finish)
                        .exceptionally(t -> {
                            finishSpanWithError(readSpan, t);
                            return null;
                        });
            }
            return result;
        }

        private void finishSpanWithError(Span readSpan, Throwable t) {
            if (null == readSpan) {
                // tracing of reads disabled
                return;
            }
            Tags.ERROR.set(readSpan, Boolean.TRUE);
            readSpan.log(CollectionsHelper.mapOf("event", "error",
                                                 "error.kind", "Exception",
                                                 "error.object", t,
                                                 "message", t.toString()));
            readSpan.finish();
        }

        private <T> Span createReadSpan(Class<T> type) {
            // only create this span if we have a parent span
            SpanContext parentSpan = spanContext();
            if (null == parentSpan) {
                return null;
            }

            SpanTracingConfig spanConfig = TracingConfigUtil
                    .spanConfig(NettyWebServer.TRACING_COMPONENT,
                                TRACING_CONTENT_READ_NAME,
                                context());

            String spanName = spanConfig.newName().orElse(TRACING_CONTENT_READ_NAME);

            if (spanConfig.enabled()) {
                // only create a real span if enabled
                Tracer.SpanBuilder spanBuilder = tracer().buildSpan(spanName);
                spanBuilder.asChildOf(parentSpan);

                if (type != null) {
                    spanBuilder.withTag("requested.type", type.getName());
                }
                return spanBuilder.start();
            } else {
                return null;
            }
        }

        @SuppressWarnings("unchecked")
        private <T> Reader<T> readerFor(final Class<T> type) {
            return (Reader<T>) readers.stream()
                                      .filter(reader -> reader.accept(type))
                                      .findFirst()
                                      .orElseThrow(() -> new IllegalArgumentException("No reader found for class: " + type));
        }

        private Reader<String> stringContentReader() {
            try {
                Charset charset = contentCharset(Request.this);
                return ContentReaders.stringReader(charset);
            } catch (final UnsupportedCharsetException e) {
                return (publisher, clazz) -> {
                    throw e;
                };
            }
        }

        @Override
        public void subscribe(Flow.Subscriber<? super DataChunk> subscriber) {
            try {
                Span readSpan = createReadSpan(Flow.Publisher.class);
                chainPublishers().subscribe(new Flow.Subscriber<DataChunk>() {
                    @Override
                    public void onSubscribe(Flow.Subscription subscription) {
                        subscriber.onSubscribe(subscription);
                    }

                    @Override
                    public void onNext(DataChunk item) {
                        subscriber.onNext(item);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        try {
                            subscriber.onError(throwable);
                        } finally {
                            finishSpanWithError(readSpan, throwable);
                        }
                    }

                    @Override
                    public void onComplete() {
                        try {
                            subscriber.onComplete();
                        } finally {
                            if (null != readSpan) {
                                // tracing disabled
                                readSpan.finish();
                            }
                        }
                    }
                });
            } catch (Exception e) {
                subscriber.onError(new IllegalArgumentException("Unexpected exception occurred during publishers chaining", e));
            }
        }

        private Flow.Publisher<DataChunk> chainPublishers() {
            Flow.Publisher<DataChunk> lastPublisher = originalPublisher;
            try {
                filtersLock.readLock().lock();
                for (Function<Flow.Publisher<DataChunk>, Flow.Publisher<DataChunk>> filter : filters) {
                    lastPublisher = filter.apply(lastPublisher);
                }
            } finally {
                filtersLock.readLock().unlock();
            }
            return lastPublisher;
        }
    }

    /**
     * {@link ServerRequest.Path} implementation.
     */
    static class Path implements ServerRequest.Path {

        private final String path;
        private final String rawPath;
        private final Map<String, String> params;
        private final Path absolutePath;
        private List<String> segments;

        /**
         * Creates new instance.
         *
         * @param path actual relative URI path.
         * @param rawPath actual relative URI path without any decoding.
         * @param params resolved path parameters.
         * @param absolutePath absolute path.
         */
        Path(String path, String rawPath, Map<String, String> params, Path absolutePath) {
            this.path = path;
            this.rawPath = rawPath;
            this.params = params == null ? Collections.emptyMap() : params;
            this.absolutePath = absolutePath;
        }

        @Override
        public String param(String name) {
            return params.get(name);
        }

        @Override
        public List<String> segments() {
            List<String> result = segments;
            if (result == null) { // No synchronisation needed, worth case is multiple splitting.
                StringTokenizer stok = new StringTokenizer(path, "/");
                result = new ArrayList<>();
                while (stok.hasMoreTokens()) {
                    result.add(stok.nextToken());
                }
                this.segments = result;
            }
            return result;
        }

        @Override
        public String toString() {
            return path;
        }

        @Override
        public String toRawString() {
            return rawPath;
        }

        @Override
        public Path absolute() {
            return absolutePath == null ? this : absolutePath;
        }

        static Path create(Path contextual, String path, Map<String, String> params) {
            return create(contextual, path, path, params);
        }

        static Path create(Path contextual, String path, String rawPath, Map<String, String> params) {
            if (contextual == null) {
                return new Path(path, rawPath, params, null);
            } else {
                return contextual.createSubpath(path, rawPath, params);
            }
        }

        Path createSubpath(String path, String rawPath, Map<String, String> params) {
            if (params == null) {
                params = Collections.emptyMap();
            }
            if (absolutePath == null) {
                HashMap<String, String> map = new HashMap<>(this.params.size() + params.size());
                map.putAll(this.params);
                map.putAll(params);
                return new Path(path, rawPath, params, new Path(this.path, this.rawPath, map, null));
            } else {
                HashMap<String, String> map = new HashMap<>(this.params.size() + params.size() + absolutePath.params.size());
                map.putAll(absolutePath.params);
                map.putAll(this.params);
                map.putAll(params);
                return new Path(path, rawPath, params, new Path(absolutePath.path, absolutePath.rawPath, map, null));
            }
        }
    }
}
