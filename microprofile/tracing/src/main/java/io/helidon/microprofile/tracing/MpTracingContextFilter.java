/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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
package io.helidon.microprofile.tracing;

import java.util.Optional;

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Provider;
import javax.ws.rs.ConstrainedTo;
import javax.ws.rs.RuntimeType;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.core.Context;

import io.helidon.common.context.Contexts;
import io.helidon.tracing.jersey.client.internal.TracingContext;
import io.helidon.webserver.ServerRequest;

import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * Automatically registered filter that stores
 * required information in thread local, to allow outbound clients to get
 * all context.
 *
 * @see TracingContext
 */
@ConstrainedTo(RuntimeType.SERVER)
@PreMatching
@Priority(Integer.MIN_VALUE)
@ApplicationScoped
public class MpTracingContextFilter implements ContainerRequestFilter {
    @Context
    private Provider<ServerRequest> request;

    private final Config config = ConfigProvider.getConfig();

    @Override
    public void filter(ContainerRequestContext requestContext) {
        ServerRequest serverRequest = this.request.get();

        Tracer tracer = serverRequest.tracer();
        Optional<SpanContext> parentSpan = serverRequest.spanContext();

        boolean clientEnabled = config.getOptionalValue("tracing.client.enabled", Boolean.class).orElse(true);
        TracingContext tracingContext = TracingContext.create(tracer, serverRequest.headers().toMap(), clientEnabled);
        parentSpan.ifPresent(tracingContext::parentSpan);

        Contexts.context().ifPresent(ctx -> ctx.register(tracingContext));
    }
}
