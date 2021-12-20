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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientResponseContext;
import org.glassfish.jersey.client.spi.PostInvocationInterceptor;
import org.junit.jupiter.api.Test;

import static io.helidon.tracing.jersey.client.ClientTracingAutoDiscoverable.CLIENT_TRACING_PRIORITY;
import static io.helidon.tracing.jersey.client.ClientTracingFilter.SPAN_PROPERTY_NAME;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class ClientTracingInterceptorTest {

    private static AtomicBoolean isSpanFinished = new AtomicBoolean();

    @Test
    public void test() {
        Client client = ClientBuilder.newBuilder()
                .connectTimeout(1, TimeUnit.MILLISECONDS)
                .readTimeout(1, TimeUnit.MILLISECONDS)
                .register(AfterClientTracingInterceptor.class, CLIENT_TRACING_PRIORITY + 1)
                .build();

        CompletableFuture<?> future = client.target("https://example.org")
                .request()
                .rx()
                .get()
                .toCompletableFuture();

        try {
           future.join();
        } catch (Throwable t) {
            assertThat(isSpanFinished.get(), is(true));
        }
    }

    /**
     * This interceptor executes after {@link ClientTracingInterceptor} and ensures the span
     * created in {@link ClientTracingFilter} is finished and no longer in the context.
     */
    static class AfterClientTracingInterceptor implements PostInvocationInterceptor {

        @Override
        public void afterRequest(ClientRequestContext requestContext, ClientResponseContext responseContext) {
            // no-op
        }

        @Override
        public void onException(ClientRequestContext requestContext, ExceptionContext exceptionContext) {
            Object span = requestContext.getProperty(SPAN_PROPERTY_NAME);
            isSpanFinished.set(span == null);
        }
    }
}
