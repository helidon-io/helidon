/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

package io.helidon.webserver.observe.tracing;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.Weighted;
import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.config.Config;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.service.registry.Services;
import io.helidon.tracing.HeaderProvider;
import io.helidon.tracing.Scope;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanContext;
import io.helidon.tracing.Tracer;
import io.helidon.tracing.config.SpanTracingConfig;
import io.helidon.tracing.config.TracingConfig;
import io.helidon.webserver.http.Filter;
import io.helidon.webserver.http.FilterChain;
import io.helidon.webserver.http.HttpFeature;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.RoutingRequest;
import io.helidon.webserver.http.RoutingResponse;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.observe.spi.Observer;
import io.helidon.webserver.observe.tracing.spi.TracingSemanticConventionsProvider;
import io.helidon.webserver.spi.ServerFeature;

import static io.helidon.webserver.WebServer.DEFAULT_SOCKET_NAME;
import static io.helidon.webserver.observe.tracing.HelidonTracingSemanticConventionsProvider.HelidonTracingSemanticConventions.TRACING_SPAN_HTTP_REQUEST;

/**
 * Observer that registers tracing endpoint, and collects all tracing checks.
 */
@RuntimeType.PrototypedBy(TracingObserverConfig.class)
public class TracingObserver implements Observer, RuntimeType.Api<TracingObserverConfig> {
    private static final String CONTENT_READ_SPAN_NAME = "content-read";
    private static final String CONTENT_WRITE_SPAN_NAME = "content-write";
    private final TracingObserverConfig config;

    private TracingObserver(TracingObserverConfig config) {
        this.config = config;
    }

    /**
     * Create a new builder to configure Tracing observer.
     *
     * @return a new builder
     */
    public static TracingObserverConfig.Builder builder() {
        return TracingObserverConfig.builder();
    }

    /**
     * Create a tracing observer that is enabled for all paths and spans (that are enabled by default).
     *
     * @param tracer tracer to use for tracing spans created by this feature
     * @return tracing observer to register with {@link io.helidon.webserver.observe.ObserveFeature}}
     */
    public static TracingObserver create(Tracer tracer) {
        return builder().tracer(tracer).build();
    }

    /**
     * Create a new tracing observer based on {@link io.helidon.config.Config}.
     *
     * @param tracer tracer to use for tracing spans created by this feature
     * @param config to base this observer on
     * @return a new tracing observer to register with {@link io.helidon.webserver.observe.ObserveFeature}
     */
    public static TracingObserver create(Tracer tracer, Config config) {
        return builder()
                .tracer(tracer)
                .config(config)
                .build();
    }

    /**
     * Create a new Tracing observer using the provided configuration.
     *
     * @param config configuration
     * @return a new observer
     */
    public static TracingObserver create(TracingObserverConfig config) {
        return new TracingObserver(config);
    }

    /**
     * Create a new Tracing observer customizing its configuration.
     *
     * @param consumer configuration consumer
     * @return a new observer
     */
    public static TracingObserver create(Consumer<TracingObserverConfig.Builder> consumer) {
        return builder()
                .update(consumer)
                .build();
    }

    @Override
    public void register(ServerFeature.ServerFeatureContext featureContext,
                         List<HttpRouting.Builder> observeEndpointRouting,
                         UnaryOperator<String> endpointFunction) {

        if (config.enabled()) {
            Set<String> sockets = new HashSet<>(config.sockets());
            if (sockets.isEmpty()) {
                sockets.addAll(featureContext.sockets());
                sockets.add(DEFAULT_SOCKET_NAME);
            }

            for (String socket : sockets) {
                featureContext.socket(socket)
                        .httpRouting()
                        .addFeature(new TracingFeature(config, DEFAULT_SOCKET_NAME.equals(socket) ? "" : socket));
            }
        }
    }

    @Override
    public String type() {
        return "tracing";
    }

    @Override
    public TracingObserverConfig prototype() {
        return config;
    }

    private static class TracingFilter implements Filter {
        private final Tracer tracer;
        private final TracingConfig envConfig;
        private final List<PathTracingConfig> pathConfigs;
        private final String socketTag;
        private final TracingSemanticConventionsProvider tracingSemanticConventionsProvider;

        TracingFilter(Tracer tracer, TracingConfig envConfig, List<PathTracingConfig> pathConfigs, String socketTag) {
            this.tracer = tracer;
            this.envConfig = envConfig;
            this.pathConfigs = pathConfigs;
            this.socketTag = socketTag;
            this.tracingSemanticConventionsProvider = Services.get(TracingSemanticConventionsProvider.class);

        }

        @Override
        public void filter(FilterChain chain, RoutingRequest req, RoutingResponse res) {
            // context of the request - we register configuration and parent spans to it
            Context context = req.context();

            TracingConfig resolved = configureTracingConfig(req, context);

            /*
            Extract inbound span context, this will act as a parent of the new webserver span
             */
            Optional<SpanContext> inboundSpanContext = tracer.extract(new HeaderProviderImpl(req));

            /*
            Find configuration of the web server span (can customize name, disable etc.)
             */
            SpanTracingConfig spanConfig = resolved.spanConfig("web-server", TRACING_SPAN_HTTP_REQUEST);
            if (!spanConfig.enabled()) {
                // nope, do not start this span, but still register parent span context for components further down
                if (inboundSpanContext.isPresent()) {
                    context.register(inboundSpanContext.get());
                    context.register(TracingConfig.class, inboundSpanContext.get());
                }
                Contexts.runInContext(context, chain::proceed);
                return;
            }

            TracingSemanticConventions semconv = tracingSemanticConventionsProvider.create(spanConfig, socketTag, req, res);

            /*
            Create web server span
             */

            // tracing is enabled, so we replace the parent span with web server parent span
            Span span = tracer.spanBuilder(semconv.spanName())
                    .update(semconv::update)
                    .update(it -> inboundSpanContext.ifPresent(it::parent))
                    .start();

            context.register(span.context());
            context.register(span);
            context.register(tracer);
            context.register(TracingConfig.class, span.context());

            /*
            Register an input stream filter to handle content read span.
             */
            if (spanConfig.logEnabled(CONTENT_READ_SPAN_NAME, true)) {
                // Invoked when the input stream is read. Our implementation does tracing, then delegates to the real stream.
                req.streamFilter(is -> new TracingStreamInputDelegate(tracer, span, is));

            }

            /*
            Register an output stream filter to correctly handle content write span
             */
            if (spanConfig.logEnabled(CONTENT_WRITE_SPAN_NAME, true)) {
                res.streamFilter(os -> {
                    // this is invoked when the user requests output stream, we just replace it with our own delegate
                    return new TracingStreamOutputDelegate(tracer, span, os);
                });
            }

            try (Scope ignored = span.activate()) {

                Contexts.runInContext(context, chain::proceed);

                semconv.update(span);
                span.end();
            } catch (Exception e) {
                semconv.update(span, e);
                span.end(e);
                throw e;
            }
        }

        private TracingConfig configureTracingConfig(RoutingRequest req, Context context) {
            TracingConfig discovered = null;

            for (PathTracingConfig pathConfig : pathConfigs) {
                if (pathConfig.matches(req.prologue().method(), req.prologue().uriPath())) {
                    if (discovered == null) {
                        discovered = pathConfig.tracedConfig();
                    } else {
                        discovered = TracingConfig.merge(discovered, pathConfig.tracedConfig());
                    }
                }
            }

            if (discovered == null) {
                context.register(envConfig);
                return envConfig;
            } else {
                context.register(discovered);
                return discovered;
            }
        }
    }

    private static final class TracingStreamDelegate {

        private final Tracer tracer;
        private final Span requestSpan;
        private final String logName;
        private boolean started;
        private boolean stopped;
        private Span span;
        private Throwable thrown;



        private TracingStreamDelegate(Tracer tracer, Span requestSpan, String logName) {
            this.tracer = tracer;
            this.requestSpan = requestSpan;
            this.logName = logName;
        }

        void thrown(Throwable t) {
            thrown = t;
        }

        void start() {
            if (!started) {
                started = true;
                span(tracer.spanBuilder(logName)
                             .parent(requestSpan.context())
                             .start());
            }
        }

        void stop() {
            if (started && !stopped) {
                stopped = true;
                if (thrown == null) {
                    span.end();
                } else {
                    span.end(thrown);
                }
            }
        }
        void span(Span span) {
            this.span = span;
        }
    }

    private static final class TracingStreamInputDelegate extends InputStream {
        private final TracingStreamDelegate tracingStream;
        private final InputStream delegate;

        private TracingStreamInputDelegate(Tracer tracer, Span requestSpan, InputStream delegate) {
            this.tracingStream = new TracingStreamDelegate(tracer, requestSpan, CONTENT_READ_SPAN_NAME);
            this.delegate = delegate;
        }

        @Override
        public int available() throws IOException {
            try {
                return delegate.available();
            } catch (IOException e) {
                tracingStream.thrown(e);
                throw e;
            } finally {
                tracingStream.stop();
            }
        }

        @Override
        public void close() throws IOException {
            try {
                delegate.close();
            } catch (IOException e) {
                tracingStream.thrown(e);
                throw e;
            } finally {
                tracingStream.stop();
            }
        }

        @Override
        public void mark(int readlimit) {
            delegate.mark(readlimit);
        }

        @Override
        public boolean markSupported() {
            return delegate.markSupported();
        }

        @Override
        public int read() throws IOException {
            tracingStream.start();
            try {
                return delegate.read();
            } catch (IOException e) {
                tracingStream.thrown(e);
                throw e;
            }
        }

        @Override
        public int read(byte[] b) throws IOException {
            tracingStream.start();
            try {
                return delegate.read(b);
            } catch (IOException e) {
                tracingStream.thrown(e);
                throw e;
            }
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            tracingStream.start();
            try {
                return delegate.read(b, off, len);
            } catch (IOException e) {
                tracingStream.thrown(e);
                throw e;
            }
        }

        @Override
        public byte[] readAllBytes() throws IOException {
            tracingStream.start();
            try {
                return delegate.readAllBytes();
            } catch (IOException e) {
                tracingStream.thrown(e);
                throw e;
            }
        }

        @Override
        public byte[] readNBytes(int len) throws IOException {
            tracingStream.start();
            try {
                return delegate.readNBytes(len);
            } catch (IOException e) {
                tracingStream.thrown(e);
                throw e;
            }
        }

        @Override
        public int readNBytes(byte[] b, int off, int len) throws IOException {
            tracingStream.start();
            try {
                return delegate.readNBytes(b, off, len);
            } catch (IOException e) {
                tracingStream.thrown(e);
                throw e;
            }
        }

        @Override
        public void reset() throws IOException {
            tracingStream.start();
            try {
                delegate.reset();
            } catch (IOException e) {
                tracingStream.thrown(e);
                throw e;
            }
        }

        @Override
        public long skip(long n) throws IOException {
            tracingStream.start();
            try {
                return delegate.skip(n);
            } catch (IOException e) {
                tracingStream.thrown(e);
                throw e;
            }
        }

        @Override
        public void skipNBytes(long n) throws IOException {
            tracingStream.start();
            try {
                delegate.skipNBytes(n);
            } catch (IOException e) {
                tracingStream.thrown(e);
                throw e;
            }
        }

        @Override
        public long transferTo(OutputStream out) throws IOException {
            tracingStream.start();
            try {
                return delegate.transferTo(out);
            } catch (IOException e) {
                tracingStream.thrown(e);
                throw e;
            }
        }
    }

    private static final class TracingStreamOutputDelegate extends OutputStream {
        private final TracingStreamDelegate tracingStream;
        private final OutputStream delegate;

        private TracingStreamOutputDelegate(Tracer tracer, Span requestSpan, OutputStream delegate) {
            this.tracingStream = new TracingStreamDelegate(tracer, requestSpan, CONTENT_WRITE_SPAN_NAME);
            this.delegate = delegate;
        }

        @Override
        public void write(int b) throws IOException {
            tracingStream.start();
            try {
                this.delegate.write(b);
            } catch (IOException e) {
                tracingStream.thrown(e);
                throw e;
            }
        }

        @Override
        public void write(byte[] b) throws IOException {
            tracingStream.start();
            try {
                this.delegate.write(b);
            } catch (IOException e) {
                tracingStream.thrown(e);
                throw e;
            }
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            tracingStream.start();
            try {
                this.delegate.write(b, off, len);
            } catch (IOException e) {
                tracingStream.thrown(e);
                throw e;
            }
        }

        @Override
        public void flush() throws IOException {
            try {
                this.delegate.flush();
            } catch (IOException e) {
                tracingStream.thrown(e);
                throw e;
            }
        }

        @Override
        public void close() throws IOException {
            try {
                this.delegate.close();
            } catch (IOException e) {
                tracingStream.thrown(e);
                throw e;
            } finally {
                tracingStream.stop();
            }
        }


    }

    private static class HeaderProviderImpl implements HeaderProvider {
        private final ServerRequest request;

        private HeaderProviderImpl(ServerRequest request) {
            this.request = request;
        }

        @Override
        public Iterable<String> keys() {
            List<String> result = new LinkedList<>();
            for (Header header : request.headers()) {
                result.add(header.headerName().lowerCase());
            }
            return result;
        }

        @Override
        public Optional<String> get(String key) {
            return request.headers().first(HeaderNames.create(key));
        }

        @Override
        public Iterable<String> getAll(String key) {
            return request.headers().all(HeaderNames.create(key), List::of);
        }

        @Override
        public boolean contains(String key) {
            return request.headers().contains(HeaderNames.create(key));
        }
    }

    private static class TracingFeature implements HttpFeature, Weighted {
        private final TracingObserverConfig config;
        private final String socketTag;

        TracingFeature(TracingObserverConfig config, String socketTag) {
            this.config = config;
            this.socketTag = socketTag;
        }

        @Override
        public double weight() {
            return config.weight();
        }

        @Override
        public void setup(HttpRouting.Builder routing) {
            routing.addFilter(new TracingFilter(config.tracer(),
                                                config.envConfig(),
                                                config.pathConfigs(),
                                                socketTag));
        }
    }
}
