/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver.opentracing;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.ConstrainedTo;
import javax.ws.rs.RuntimeType;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.container.PreMatching;

import io.helidon.webserver.ServerRequest;

import io.opentracing.Span;
import io.opentracing.Tracer;

/**
 * The OpentracingContainerFilter provides means to record a tracing info at the Jersey request handling
 * layer on the server side.
 * <p>
 * It requires Jersey is running on top of {@link io.helidon.webserver.WebServer}.
 * <p>
 * The tracing info is recorded as an operation named {@code jersey-server-handle}.
 * <p>
 * Usage:
 * <pre><code>
 * public class Application extends ResourceConfig {
 *     public Application() {
 *         register(OpentracingContainerFilter.class);
 *     }
 * }
 * </code></pre>
 */
@ConstrainedTo(RuntimeType.SERVER)
@PreMatching
@Priority(Integer.MIN_VALUE)
public class OpentracingContainerFilter implements ContainerRequestFilter, ContainerResponseFilter {

    @Inject
    private Provider<ServerRequest> request;

    @Override
    public void filter(ContainerRequestContext request) {

        ServerRequest serverRequest = this.request.get();

        Tracer tracer = serverRequest.webServer().configuration().tracer();
        Span parentSpan = serverRequest.span();

        Span span = tracer.buildSpan("jersey-server-handle")
                          .asChildOf(parentSpan)
                          .start();

        request.setProperty(Span.class.getName(), span);
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        Span span = (Span) request.getProperty(Span.class.getName());
        if (span == null) {
            return; // unknown state
        }
        span.finish();
    }
}
