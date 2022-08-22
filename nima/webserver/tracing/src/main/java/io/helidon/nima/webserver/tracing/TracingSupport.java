/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.nima.webserver.tracing;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import io.helidon.common.http.Http;
import io.helidon.common.http.HttpPrologue;
import io.helidon.nima.webserver.http.Filter;
import io.helidon.nima.webserver.http.FilterChain;
import io.helidon.nima.webserver.http.HttpRouting;
import io.helidon.nima.webserver.http.RoutingRequest;
import io.helidon.nima.webserver.http.RoutingResponse;
import io.helidon.tracing.HeaderProvider;
import io.helidon.tracing.Scope;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanContext;
import io.helidon.tracing.Tag;
import io.helidon.tracing.Tracer;

/**
 * Open Telemetry tracing support.
 */
public class TracingSupport {
    private final boolean enabled;
    private final Filter filter;

    private TracingSupport(Filter filter) {
        this.enabled = true;
        this.filter = filter;
    }

    private TracingSupport() {
        this.enabled = false;
        this.filter = null;
    }

    /**
     * Create new support from an tracer instance.
     *
     * @param tracer tracer
     * @return new tracing support
     */
    public static TracingSupport create(Tracer tracer) {
        if (tracer.enabled()) {
            return new TracingSupport(new TracingFilter(tracer));
        } else {
            return new TracingSupport();
        }
    }

    /**
     * Register tracing support filter with the HTTP routing.
     *
     * @param builder routing builder
     */
    public void register(HttpRouting.Builder builder) {
        if (enabled) {
            builder.addFilter(filter);
        }
    }

    private static class TracingFilter implements Filter {
        private final Tracer tracer;

        private TracingFilter(Tracer tracer) {
            this.tracer = tracer;
        }

        @Override
        public void filter(FilterChain chain, RoutingRequest req, RoutingResponse res) {
            Optional<SpanContext> context = tracer.extract(new NimaHeaderProvider(req));

            HttpPrologue prologue = req.prologue();

            Span span = tracer.spanBuilder(prologue.method().text()
                                                   + " " + req.path().rawPath()
                                                   + " " + prologue.protocol()
                                                   + "/" + prologue.protocolVersion())
                    .kind(Span.Kind.SERVER)
                    .update(it -> context.ifPresent(it::parent))
                    .start();

            try (Scope ignored = span.activate()) {
                span.tag(Tag.HTTP_METHOD.create(prologue.method().text()));
                span.tag(Tag.HTTP_URL.create(prologue.protocol() + "://" + req.authority() + "/" + prologue.uriPath().path()));
                span.tag(Tag.HTTP_VERSION.create(prologue.protocolVersion()));

                chain.proceed();
            } finally {
                Http.Status status = res.status();
                span.tag(Tag.HTTP_STATUS.create(status.code()));

                if (status.code() >= 400) {
                    span.status(Span.Status.ERROR);
                } else {
                    span.status(Span.Status.OK);
                }

                span.end();
            }
        }

        private static class NimaHeaderProvider implements HeaderProvider {
            private final RoutingRequest request;

            private NimaHeaderProvider(RoutingRequest request) {
                this.request = request;
            }

            @Override
            public Iterable<String> keys() {
                List<String> result = new LinkedList<>();
                for (Http.HeaderValue header : request.headers()) {
                    result.add(header.headerName().lowerCase());
                }
                return result;
            }

            @Override
            public Optional<String> get(String key) {
                return request.headers().first(Http.Header.create(key));
            }

            @Override
            public Iterable<String> getAll(String key) {
                return request.headers().all(Http.Header.create(key), List::of);
            }

            @Override
            public boolean contains(String key) {
                return request.headers().contains(Http.Header.create(key));
            }
        }
    }
}
