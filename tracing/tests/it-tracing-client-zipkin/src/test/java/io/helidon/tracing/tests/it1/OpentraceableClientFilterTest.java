/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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

package io.helidon.tracing.tests.it1;

import java.net.URI;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.tracing.Span;
import io.helidon.tracing.Tracer;
import io.helidon.tracing.TracerBuilder;
import io.helidon.tracing.jersey.client.ClientTracingFilter;

import brave.internal.codec.HexCodec;
import brave.opentracing.BraveSpanContext;
import brave.propagation.TraceContext;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.core.Configuration;
import jakarta.ws.rs.core.MultivaluedHashMap;
import org.hamcrest.collection.IsEmptyCollection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsMapContaining.hasEntry;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsCollectionContaining.hasItem;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The ZipkinClientTest.
 */
class OpentraceableClientFilterTest {

    private final Tracer tracer = TracerBuilder.create("test-service").registerGlobal(false).build();
    private final ClientTracingFilter filter = new ClientTracingFilter();
    private final MultivaluedHashMap<Object, Object> map = new MultivaluedHashMap<>();
    private final Configuration configurationMock = Mockito.mock(Configuration.class);
    private final ClientRequestContext requestContextMock;

    OpentraceableClientFilterTest() {
        this.requestContextMock = Mockito.mock(ClientRequestContext.class);
        Mockito.when(this.requestContextMock.getUri()).thenReturn(URI.create("http://localhost/foo/bar"));
        Mockito.when(this.requestContextMock.getMethod()).thenReturn("GET");
    }

    @BeforeEach
    void connectMocks() {
        Mockito.doReturn(configurationMock)
                .when(requestContextMock)
                .getConfiguration();

        Mockito.doReturn(map)
                .when(requestContextMock)
                .getHeaders();
    }

    @Test
    void testNewSpanCreated() {
        // this test leaves the span scope activated on current thread, so we run it in a different thread
        runInThreadAndContext(() -> {
            Mockito.doReturn(tracer)
                    .when(requestContextMock)
                    .getProperty(Mockito.anyString());

            filter.filter(requestContextMock);

            assertThat(map,
                    hasEntry(is("X-B3-TraceId"),
                            hasItem(instanceOf(String.class))));
            String traceId = (String) map.getFirst("X-B3-TraceId");
            assertThat(map, hasEntry(is("X-B3-SpanId"), hasItem(is(traceId))));
            return null;
        });

    }

    @Test
    void testChildSpanGetsCreated() {
        // this test leaves the span scope activated on current thread, so we run it in a different thread
        runInThreadAndContext(() -> {
            Mockito.doReturn(tracer)
                    .when(configurationMock)
                    .getProperty(ClientTracingFilter.TRACER_PROPERTY_NAME);

            Span span = tracer.spanBuilder("my-parent").start();

            Mockito.doReturn(span.context())
                    .when(requestContextMock)
                    .getProperty(ClientTracingFilter.CURRENT_SPAN_CONTEXT_PROPERTY_NAME);

            filter.filter(requestContextMock);

            TraceContext traceContext = ((BraveSpanContext) span.unwrap(io.opentracing.Span.class).context()).unwrap();
            assertThat(map,
                    hasEntry(is("X-B3-TraceId"), hasItem(is(traceContext.traceIdString()))));
            assertThat(map,
                    hasEntry(is("X-B3-SpanId"),
                            hasItem(instanceOf(String.class))));
            assertThat(map,
                    hasEntry(is("X-B3-ParentSpanId"),
                            hasItem(is(HexCodec.toLowerHex(traceContext.spanId())))));

            return null;
        });
    }

    @Test
    void testMissingTracer() {
        // this test leaves the span scope activated on current thread, so we run it in a different thread

        runInThreadAndContext(() -> {
            filter.filter(requestContextMock);

            assertThat(map.entrySet(), IsEmptyCollection.empty());
            return null;
        });
    }

    private void runInThreadAndContext(Callable<Void> c) {
        CompletableFuture<Void> cf = new CompletableFuture<>();

        Thread thread = new Thread(() -> {
            Contexts.runInContext(Context.create(), () -> {
                try {
                    c.call();
                    cf.complete(null);
                } catch (Throwable e) {
                    cf.completeExceptionally(e);
                }
            });
        });
        thread.setDaemon(true);
        thread.start();

        try {
            cf.get(10, TimeUnit.SECONDS);
        } catch (InterruptedException | TimeoutException e) {
            fail(e);
        } catch (ExecutionException e) {
            fail(e.getCause());
        }
    }
}
