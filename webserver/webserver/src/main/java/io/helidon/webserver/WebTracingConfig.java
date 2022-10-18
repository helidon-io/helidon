/*
 * Copyright (c) 2019, 2022 Oracle and/or its affiliates.
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

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.common.http.Http;
import io.helidon.config.Config;
import io.helidon.tracing.HeaderProvider;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanContext;
import io.helidon.tracing.Tag;
import io.helidon.tracing.Tracer;
import io.helidon.tracing.config.SpanTracingConfig;
import io.helidon.tracing.config.TracingConfig;
import io.helidon.tracing.config.TracingConfigUtil;

/**
 * Tracing configuration for webserver.
 * Tracing configuration has two components - an overall (application wide) {@link io.helidon.tracing.config.TracingConfig}
 *  and a path specific {@link PathTracingConfig}.
 */
public abstract class WebTracingConfig {
    /**
     * Tracing configuration.
     * This is the configuration set up for the whole server. There can also be a path specific configuration available
     * through {@link #pathConfigs()}.
     *
     * @return tracing configuration for all components
     */
    abstract TracingConfig envConfig();

    /**
     * Path specific tracing configurations.
     *
     * @return tracing configuration per path (and HTTP methods)
     */
    abstract Iterable<PathTracingConfig> pathConfigs();

    /**
     * Create a tracing configuration that is enabled for all paths and spans (that are enabled by default).
     *
     * @return tracing configuration to register with {@link Routing.Builder#register(WebTracingConfig)}
     */
    public static WebTracingConfig create() {
        return create(TracingConfig.ENABLED);
    }

    /**
     * Create a new tracing support base on {@link io.helidon.tracing.config.TracingConfig}.
     *
     * @param configuration traced system configuration
     * @return a new tracing support to register with web server routing
     */
    public static WebTracingConfig create(TracingConfig configuration) {
        return builder().envConfig(configuration).build();
    }

    /**
     * Create a new tracing support base on {@link io.helidon.config.Config}.
     *
     * @param config to base this support on
     * @return a new tracing support to register with web server routing
     */
    public static WebTracingConfig create(Config config) {
        return builder().config(config).build();
    }

    /**
     * A fluent API builder to create tracing support.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    static Tracer tracer(WebServer webServer) {
        if (null != webServer) {
            ServerConfiguration configuration = webServer.configuration();
            if (null != configuration) {
                Tracer tracer = configuration.tracer();
                if (null != tracer) {
                    return tracer;
                }
            }
        }
        return Contexts.context().flatMap(ctx -> ctx.get(Tracer.class)).orElseGet(Tracer::global);
    }

    Service service() {
        return rules -> {
            pathConfigs().forEach(path -> {
                List<Http.RequestMethod> methods = path.methods()
                        .stream()
                        .map(Http.RequestMethod::create)
                        .collect(Collectors.toList());

                TracingConfig wrappedPath = path.tracedConfig();
                if (methods.isEmpty()) {
                    rules.any(path.path(), new TracingConfigHandler(wrappedPath));
                } else {
                    rules.anyOf(methods, path.path(), new TracingConfigHandler(wrappedPath));
                }
            });
            // and now register the tracing of requests
            rules.any(new RequestSpanHandler());
        };
    }

    /**
     * A fluent API builder for {@link WebTracingConfig}.
     */
    public static class Builder implements io.helidon.common.Builder<Builder, WebTracingConfig> {
        private final List<PathTracingConfig> pathTracingConfigs = new LinkedList<>();
        private TracingConfig tracedConfig = TracingConfig.ENABLED;

        /**
         * OpenTracing spec states that certain MP paths need to be disabled by default.
         * Note that if a user changes the default location of any of these using
         * web-context's, then they would need to provide these exclusions manually.
         *
         * The default path configs below are overridable via configuration. For example,
         * health could be enabled by setting {@code tracing.paths.0.path=/health} and
         * {@code tracing.paths.0.enabled=true}.
         */
        Builder() {
            addPathConfig(PathTracingConfig.builder()
                    .path("/metrics")
                    .tracingConfig(TracingConfig.DISABLED)
                    .build());
            // Simplified matching for base, vendor and application paths
            addPathConfig(PathTracingConfig.builder()
                    .path("/metrics/{+}")
                    .tracingConfig(TracingConfig.DISABLED)
                    .build());
            addPathConfig(PathTracingConfig.builder()
                    .path("/health")
                    .tracingConfig(TracingConfig.DISABLED)
                    .build());
            addPathConfig(PathTracingConfig.builder()
                    .path("/health/{+}")
                    .tracingConfig(TracingConfig.DISABLED)
                    .build());
            addPathConfig(PathTracingConfig.builder()
                    .path("/openapi")
                    .tracingConfig(TracingConfig.DISABLED)
                    .build());
        }

        @Override
        public WebTracingConfig build() {
            final TracingConfig envConfig = this.tracedConfig;
            final List<PathTracingConfig> pathConfigs = new LinkedList<>(this.pathTracingConfigs);

            return new WebTracingConfig() {
                @Override
                public TracingConfig envConfig() {
                    return envConfig;
                }

                @Override
                public Iterable<PathTracingConfig> pathConfigs() {
                    return pathConfigs;
                }
            };
        }

        /**
         * Add a path specific configuration of tracing.
         *
         * @param pathTracingConfig configuration of tracing for a specific path
         * @return updated builder instance
         */
        public Builder addPathConfig(PathTracingConfig pathTracingConfig) {
            this.pathTracingConfigs.add(pathTracingConfig);
            return this;
        }

        /**
         * Use the provided configuration as a default for any request.
         *
         * @param tracingConfig default web server tracing configuration
         * @return updated builder instance
         */
        public Builder envConfig(TracingConfig tracingConfig) {
            this.tracedConfig = tracingConfig;
            return this;
        }

        /**
         * Update builder from {@link io.helidon.config.Config}.
         *
         * @param config config to read default configuration and path specific configuration from
         * @return updated builder instance
         */
        public Builder config(Config config) {
            // read the overall configuration
            envConfig(TracingConfig.create(config));

            // and then the paths
            Config allPaths = config.get("paths");
            allPaths.asNodeList().ifPresent(this::addPaths);
            return this;
        }

        private void addPaths(List<Config> configs) {
            configs.stream()
                    .map(PathTracingConfig::create)
                    .forEach(this::addPathConfig);
        }
    }

    // this class exists so tracing of handler in webserver shows nice class name and not a lambda
    private static final class TracingConfigHandler implements Handler {
        private final TracingConfig pathSpecific;

        private TracingConfigHandler(TracingConfig pathSpecific) {
            this.pathSpecific = pathSpecific;
        }

        @Override
        public void accept(ServerRequest req, ServerResponse res) {
            Optional<TracingConfig> existing = req.context().get(TracingConfig.class);
            if (existing.isPresent()) {
                req.context().register(TracingConfig.merge(existing.get(), pathSpecific));
            } else {
                req.context().register(pathSpecific);
            }
            req.next();
        }
    }

    static final class RequestSpanHandler implements Handler {
        private static final String TRACING_SPAN_HTTP_REQUEST = "HTTP Request";
        private final AtomicBoolean checkedIfShouldTrace = new AtomicBoolean();
        private volatile boolean shouldTrace = true;

        RequestSpanHandler() {
        }

        @Override
        public void accept(ServerRequest req, ServerResponse res) {
            if (shouldTrace && checkedIfShouldTrace.compareAndSet(false, true)) {
                shouldTrace = req.tracer().enabled();
            }

            if (shouldTrace) {
                doAccept(req, res);
            }

            req.next();
        }

        private void doAccept(ServerRequest req, ServerResponse res) {
            Tracer tracer = req.tracer();

            // must run in context
            Context context = req.context();

            SpanTracingConfig spanConfig = TracingConfigUtil
                    .spanConfig(NettyWebServer.TRACING_COMPONENT, TRACING_SPAN_HTTP_REQUEST, context);

            SpanContext inboundSpanContext = tracer.extract(new TracingHeaderProvider(req.headers().toMap()))
                    .orElse(null);


            if (inboundSpanContext != null) {
                // register as parent span
                context.register(inboundSpanContext);
                context.register(ServerRequest.class, inboundSpanContext);
            }

            if (!spanConfig.enabled()) {
                return;
            }

            String spanName = spanConfig.newName().orElse(TRACING_SPAN_HTTP_REQUEST);
            if (spanName.indexOf('%') > -1) {
                spanName = String.format(spanName, req.method().name(), req.path(), req.query());
            }
            // tracing is enabled, so we replace the parent span with web server parent span
            Span.Builder<?> spanBuilder = tracer.spanBuilder(spanName)
                    .kind(Span.Kind.SERVER)
                    .tag(Tag.COMPONENT.create("helidon-webserver"))
                    .tag(Tag.HTTP_METHOD.create(req.method().name()))
                    .tag(Tag.HTTP_URL.create(req.uri().toString()))
                    .tag(Tag.HTTP_VERSION.create(req.version().value()));

            if (inboundSpanContext != null) {
                spanBuilder.parent(inboundSpanContext);
            }

            // cannot use startActive, as it conflicts with the thread model we use
            Span span = spanBuilder.start();

            context.register(span.context());
            context.register(ServerRequest.class, span.context());

            res.whenSent()
                    .thenRun(() -> {
                        Http.ResponseStatus httpStatus = res.status();
                        if (httpStatus != null) {
                            int statusCode = httpStatus.code();
                            span.tag(Tag.HTTP_STATUS.create(statusCode));

                            if (statusCode >= 400) {
                                span.status(Span.Status.ERROR);

                                span.addEvent("error", Map.of("message", "Response HTTP status: " + statusCode,
                                                                 "error.kind", statusCode < 500 ? "ClientError" : "ServerError"));
                            }
                        }
                        span.end();
                    })
                    .exceptionally(t -> {
                        span.end(t);
                        return null;
                    });
        }

        private static class TracingHeaderProvider implements HeaderProvider {

            private final Map<String, List<String>> headers;

            TracingHeaderProvider(Map<String, List<String>> headers) {
                this.headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                this.headers.putAll(headers);
            }

            @Override
            public Iterable<String> keys() {
                return headers.keySet();
            }

            @Override
            public Optional<String> get(String key) {
                List<String> strings = headers.get(key);
                if (strings == null) {
                    return Optional.empty();
                }
                return Optional.of(strings.get(0));
            }

            @Override
            public Iterable<String> getAll(String key) {
                List<String> strings = headers.get(key);
                if (strings == null) {
                    return List.of();
                }
                return strings;
            }

            @Override
            public boolean contains(String key) {
                return headers.containsKey(key);
            }
        }
    }
}
