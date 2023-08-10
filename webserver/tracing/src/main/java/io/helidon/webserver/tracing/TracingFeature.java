/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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

package io.helidon.webserver.tracing;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.common.Weighted;
import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.common.uri.UriInfo;
import io.helidon.config.Config;
import io.helidon.http.Http;
import io.helidon.http.HttpPrologue;
import io.helidon.tracing.HeaderProvider;
import io.helidon.tracing.Scope;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanContext;
import io.helidon.tracing.Tag;
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

/**
 * Tracing configuration for webserver.
 * Tracing configuration has two components - an overall (application wide) {@link io.helidon.tracing.config.TracingConfig}
 * and a path specific {@link PathTracingConfig}.
 */
public class TracingFeature implements HttpFeature, Weighted {
    private static final double WEIGHT = 900;

    private final boolean enabled;
    private final Tracer tracer;
    private final TracingConfig envConfig;
    private final List<PathTracingConfig> pathConfigs;
    private final double weight;

    /**
     * No side effects.
     */
    private TracingFeature(Builder builder) {
        this.enabled = builder.enabled;
        this.tracer = builder.tracer;
        this.envConfig = builder.tracedConfig;
        this.pathConfigs = List.copyOf(builder.pathTracingConfigs);
        this.weight = builder.weight;
    }

    /**
     * Create a tracing configuration that is enabled for all paths and spans (that are enabled by default).
     *
     * @param tracer tracer to use for tracing spans created by this feature
     * @return tracing configuration to register with
     *         {@link
     *         io.helidon.webserver.http.HttpRouting.Builder#register(java.util.function.Supplier[])}
     */
    public static TracingFeature create(Tracer tracer) {
        return create(tracer, TracingConfig.ENABLED);
    }

    /**
     * Create a new tracing support base on {@link io.helidon.tracing.config.TracingConfig}.
     *
     * @param tracer tracer to use for tracing spans created by this feature
     * @param configuration traced system configuration
     * @return a new tracing support to register with web server routing
     */
    public static TracingFeature create(Tracer tracer, TracingConfig configuration) {
        return builder().tracer(tracer).envConfig(configuration).build();
    }

    /**
     * Create a new tracing support base on {@link io.helidon.config.Config}.
     *
     * @param tracer tracer to use for tracing spans created by this feature
     * @param config to base this support on
     * @return a new tracing support to register with web server routing
     */
    public static TracingFeature create(Tracer tracer, Config config) {
        return builder().tracer(tracer).config(config).build();
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
    public void setup(HttpRouting.Builder routing) {
        if (enabled) {
            // and now register the tracing of requests
            routing.addFilter(new TracingFilter(tracer, envConfig, pathConfigs));
        }
    }

    @Override
    public double weight() {
        return weight;
    }

    /**
     * A fluent API builder for {@link TracingFeature}.
     */
    public static class Builder implements io.helidon.common.Builder<Builder, TracingFeature> {
        private final List<PathTracingConfig> pathTracingConfigs = new LinkedList<>();

        private double weight = WEIGHT;
        private TracingConfig tracedConfig = TracingConfig.ENABLED;
        private Tracer tracer;
        private boolean enabled = true;

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
                                  .path("/metrics/*")
                                  .tracingConfig(TracingConfig.DISABLED)
                                  .build());
            addPathConfig(PathTracingConfig.builder()
                                  .path("/health/*")
                                  .tracingConfig(TracingConfig.DISABLED)
                                  .build());
            addPathConfig(PathTracingConfig.builder()
                                  .path("/openapi/*")
                                  .tracingConfig(TracingConfig.DISABLED)
                                  .build());
        }

        @Override
        public TracingFeature build() {
            if (tracer == null) {
                throw new IllegalArgumentException("Tracing feature must be configured with a tracer");
            }
            return new TracingFeature(this);
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
            enabled(tracedConfig.enabled());

            config.get("weight").asDouble().ifPresent(this::weight);

            return this;
        }

        /**
         * Override default weight of this feature.
         * Changing weight may cause tracing to be executed at a different time (such as after security, or even after
         * all routes). Please understand feature weights before changing this order.
         *
         * @param weight new weight of tracing feature
         * @return updated builder
         */
        public Builder weight(double weight) {
            this.weight = weight;
            return this;
        }

        /**
         * Explicitly enable/disable tracing feature.
         *
         * @param enabled if set to {@code false}, this feature will be disabled and tracing filter will never be registered
         * @return updated builder
         */
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        /**
         * Tracer to use to extract inbound span context.
         *
         * @param tracer tracer to use
         * @return updated builder
         */
        public Builder tracer(Tracer tracer) {
            this.tracer = tracer;
            return this;
        }

        private void addPaths(List<Config> configs) {
            configs.stream()
                    .map(PathTracingConfig::create)
                    .forEach(this::addPathConfig);
        }
    }

    private static class TracingFilter implements Filter {
        private static final String TRACING_SPAN_HTTP_REQUEST = "HTTP Request";
        private final Tracer tracer;
        private final TracingConfig envConfig;
        private final List<PathTracingConfig> pathConfigs;

        TracingFilter(Tracer tracer, TracingConfig envConfig, List<PathTracingConfig> pathConfigs) {
            this.tracer = tracer;
            this.envConfig = envConfig;
            this.pathConfigs = pathConfigs;
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
            /*
            Create web server span
             */
            HttpPrologue prologue = req.prologue();

            String spanName = spanConfig.newName().orElse(TRACING_SPAN_HTTP_REQUEST);
            if (spanName.indexOf('%') > -1) {
                spanName = String.format(spanName, prologue.method().text(), req.path().rawPath(), req.query().rawValue());
            }
            // tracing is enabled, so we replace the parent span with web server parent span
            Span span = tracer.spanBuilder(spanName)
                    .kind(Span.Kind.SERVER)
                    .update(it -> inboundSpanContext.ifPresent(it::parent))
                    .start();

            context.register(span.context());
            context.register(TracingConfig.class, span.context());

            try (Scope ignored = span.activate()) {
                span.tag(Tag.COMPONENT.create("helidon-webserver"));
                span.tag(Tag.HTTP_METHOD.create(prologue.method().text()));
                UriInfo uriInfo = req.requestedUri();
                span.tag(Tag.HTTP_URL.create(uriInfo.scheme() + "://" + uriInfo.authority() + uriInfo.path().path()));
                span.tag(Tag.HTTP_VERSION.create(prologue.protocolVersion()));

                Contexts.runInContext(context, chain::proceed);

                Http.Status status = res.status();
                span.tag(Tag.HTTP_STATUS.create(status.code()));

                if (status.code() >= 400) {
                    span.status(Span.Status.ERROR);
                    span.addEvent("error", Map.of("message", "Response HTTP status: " + status,
                                                  "error.kind", status.code() < 500 ? "ClientError" : "ServerError"));
                } else {
                    span.status(Span.Status.OK);
                }

                span.end();
            } catch (Exception e) {
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

    private static class HeaderProviderImpl implements HeaderProvider {
        private final ServerRequest request;

        private HeaderProviderImpl(ServerRequest request) {
            this.request = request;
        }

        @Override
        public Iterable<String> keys() {
            List<String> result = new LinkedList<>();
            for (Http.Header header : request.headers()) {
                result.add(header.headerName().lowerCase());
            }
            return result;
        }

        @Override
        public Optional<String> get(String key) {
            return request.headers().first(Http.HeaderNames.create(key));
        }

        @Override
        public Iterable<String> getAll(String key) {
            return request.headers().all(Http.HeaderNames.create(key), List::of);
        }

        @Override
        public boolean contains(String key) {
            return request.headers().contains(Http.HeaderNames.create(key));
        }
    }
}
