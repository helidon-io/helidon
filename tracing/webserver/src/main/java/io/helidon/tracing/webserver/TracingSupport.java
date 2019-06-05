/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.tracing.webserver;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import io.helidon.common.CollectionsHelper;
import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.common.http.Http;
import io.helidon.config.Config;
import io.helidon.tracing.config.TracedComponent;
import io.helidon.tracing.config.TracedConfig;
import io.helidon.tracing.config.TracedConfigUtil;
import io.helidon.tracing.config.TracedSpan;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMapExtractAdapter;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;

/**
 * This {@link io.helidon.webserver.Service} adds support for path specific configuration of tracing.
 * @see io.helidon.tracing.config.TracedConfig
 */
public final class TracingSupport implements Service {
    private static final String TRACING_SPAN_HTTP_REQUEST = "HTTP Request";
    private static final String TRACING_COMPONENT_NAME = "web-server";

    private final TracedConfig configuration;
    private final List<TracedPath> paths;

    private TracingSupport(Builder builder) {
        this.configuration = builder.tracedConfig;
        this.paths = new LinkedList<>(builder.tracedPaths);
    }

    /**
     * Create a new tracing support base on {@link io.helidon.tracing.config.TracedConfig}.
     *
     * @param configuration traced system configuration
     * @return a new tracing support to register with web server routing
     */
    public static TracingSupport create(TracedConfig configuration) {
        return builder().tracedConfig(configuration).build();
    }

    /**
     * Create a new tracing support base on {@link io.helidon.config.Config}.
     *
     * @param config to base this support on
     * @return a new tracing support to register with web server routing
     */
    public static TracingSupport create(Config config) {
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

    @Override
    public void update(Routing.Rules rules) {
        TracedConfig root = wrap(configuration);

        // register a marker into context to let RequestRouting that TracingSupport is present
        Contexts.context().ifPresent(ctx -> ctx.register(ServerRequest.class, root));

        // root configuration goes first
        rules.any((req, res) -> update(req, root));

        // now iterate through the paths configured
        for (TracedPath path : paths) {
            List<Http.RequestMethod> methods = path.methods()
                    .stream()
                    .map(Http.RequestMethod::create)
                    .collect(Collectors.toList());

            // wrapping is to force people to use "TracingContainer" to obtain it from context, so we can override these
            TracedConfig wrappedPath = wrap(path.tracedConfig());
            if (methods.isEmpty()) {
                rules.any(path.path(), (req, res) -> update(req, wrappedPath));
            } else {
                rules.anyOf(methods, path.path(), (req, res) -> update(req, wrappedPath));
            }
        }

        rules.any((req, res) -> {
            //if request span enabled, start it and register with context
            registerRequestSpan(req, res);
            req.next();
        });
    }

    private void registerRequestSpan(ServerRequest request, ServerResponse response) {
        // must run in context
        Context context = Contexts.context().orElseThrow(() -> new IllegalStateException("Context must be available"));
        Tracer tracer = context.get(Tracer.class).orElseGet(GlobalTracer::get);

        TracedSpan spanConfig = TracedConfigUtil.spanConfig(TRACING_COMPONENT_NAME, TRACING_SPAN_HTTP_REQUEST);

        String spanName = spanConfig.newName().orElse(TRACING_SPAN_HTTP_REQUEST);

        // even if we disable tracing for web server, we still need to configure parent span from inbound headers
        Map<String, String> headersMap = request.headers()
                .toMap()
                .entrySet()
                .stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .collect(Collectors.toMap(Map.Entry::getKey,
                                          entry -> entry.getValue().get(0)));
        SpanContext inboundSpanContext = tracer.extract(Format.Builtin.HTTP_HEADERS, new TextMapExtractAdapter(headersMap));
        if (null != inboundSpanContext) {
            // register as parent span
            context.register(inboundSpanContext);
        }

        if (!spanConfig.enabled().orElse(true)) {
            return;
        }

        // tracing is enabled, so we replace the parent span with web server parent span
        Tracer.SpanBuilder spanBuilder = tracer.buildSpan(spanName)
                .withTag(Tags.COMPONENT.getKey(), "helidon-webserver")
                .withTag(Tags.HTTP_METHOD.getKey(), request.method().name())
                .withTag(Tags.HTTP_URL.getKey(), request.uri().toString());

        if (inboundSpanContext != null) {
            spanBuilder.asChildOf(inboundSpanContext);
        }

        // cannot use startActive, as it conflicts with the thread model we use
        Span span = spanBuilder.start();

        // TODO remove the next single line for version 2.0 - we should only expose SpanContext
        context.register(span);
        context.register(span.context());

        response.whenSent()
                .thenRun(() -> {
                    Http.ResponseStatus httpStatus = response.status();
                    if (httpStatus != null) {
                        int statusCode = httpStatus.code();
                        Tags.HTTP_STATUS.set(span, statusCode);
                        if (statusCode >= 400) {
                            Tags.ERROR.set(span, true);
                            span.log(CollectionsHelper.mapOf("event", "error",
                                                             "message", "Response HTTP status: " + statusCode,
                                                             "error.kind", statusCode < 500 ? "ClientError" : "ServerError"));
                        }
                    }
                    span.finish();
                })
                .exceptionally(t -> {
                    Tags.ERROR.set(span, true);
                    span.log(CollectionsHelper.mapOf("event", "error",
                                                     "error.object", t));
                    span.finish();
                    return null;
                });

    }

    private void update(ServerRequest req, TracedConfig wrapped) {
        Optional<TracedConfig> existing = req.context().get(TracedConfig.class);
        if (existing.isPresent()) {
            req.context().register(TracedConfig.merge(existing.get(), wrapped));
        } else {
            req.context().register(wrapped);
        }
        req.next();
    }

    private TracedConfig wrap(TracedConfig container) {
        return new TracedConfig() {
            @Override
            public Optional<TracedComponent> component(String componentName) {
                return container.component(componentName);
            }

            @Override
            public Optional<Boolean> enabled() {
                return container.enabled();
            }

            @Override
            public Optional<TracedSpan> spanConfig(String component, String spanName) {
                return container.spanConfig(component, spanName);
            }
        };
    }

    /**
     * Fluent API builder for {@link io.helidon.tracing.webserver.TracingSupport}.
     */
    public static final class Builder implements io.helidon.common.Builder<TracingSupport> {
        private final List<TracedPath> tracedPaths = new LinkedList<>();
        private TracedConfig tracedConfig = TracedConfig.ENABLED;

        @Override
        public TracingSupport build() {
            return new TracingSupport(this);
        }

        /**
         * Use the provided configuration as a default for any request.
         *
         * @param configuration default web server tracing configuration
         * @return updated builder instance
         */
        public Builder tracedConfig(TracedConfig configuration) {
            this.tracedConfig = configuration;
            return this;
        }

        /**
         * Add a path specific configuration of tracing.
         *
         * @param pathConfiguration configuration of tracing for a specific path
         * @return updated builder instance
         */
        public Builder addPath(TracedPath pathConfiguration) {
            this.tracedPaths.add(pathConfiguration);
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
            tracedConfig(TracedConfig.create(config));
            // and then the paths
            Config allPaths = config.get("paths");
            allPaths.asNodeList().ifPresent(this::addPaths);
            return this;
        }

        private void addPaths(List<Config> configs) {
            configs.stream()
                    .map(TracedPath::create)
                    .forEach(this::addPath);
        }

    }
}
