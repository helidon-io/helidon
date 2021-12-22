/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
package io.helidon.tracing.jersey.client;

import java.util.Map;

import io.opentracing.Span;
import io.opentracing.tag.Tags;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientResponseContext;
import org.glassfish.jersey.client.spi.PostInvocationInterceptor;

import static io.helidon.tracing.jersey.client.ClientTracingFilter.SPAN_PROPERTY_NAME;

/**
 * A post-invocation client interceptor. If an exception (e.g. a connection timeout)
 * is thrown while executing a client request, this interceptor will ensure
 * that an active tracing span is properly finished --given that client response
 * filters will not be executed if an exception is thrown.
 */
public class ClientTracingInterceptor implements PostInvocationInterceptor {

    @Override
    public void afterRequest(ClientRequestContext requestContext, ClientResponseContext responseContext) {
        // no-op
    }

    /**
     * Upon encountering a client exception, and if there's an active span created by a
     * tracing filter, finish the span.
     *
     * @param requestContext the request context
     * @param exceptionContext the exception context
     */
    @Override
    public void onException(ClientRequestContext requestContext, ExceptionContext exceptionContext) {
        Object property = requestContext.getProperty(SPAN_PROPERTY_NAME);
        if (property instanceof Span span) {
            Tags.ERROR.set(span, true);
            span.log(Map.of("event",
                    "error",
                    "message",
                    "Exception executing client request: " + exceptionContext.getThrowables().pop(),
                    "error.kind", "ClientError"));
            span.finish();
            requestContext.removeProperty(SPAN_PROPERTY_NAME);
        }
    }
}
