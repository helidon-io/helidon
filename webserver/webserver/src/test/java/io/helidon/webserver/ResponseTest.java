/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.common.reactive.Flow;
import io.helidon.common.reactive.ReactiveStreamsAdapter;
import io.helidon.webserver.spi.BareResponse;

import io.opentracing.SpanContext;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests {@link Response}.
 */
public class ResponseTest {

    private static void close(Response response) throws Exception {
        response.send().toCompletableFuture().get();
    }

    @Test
    public void headersAreClosedFirst() throws Exception {
        StringBuffer sb = new StringBuffer();
        NoOpBareResponse br = new NoOpBareResponse(sb);
        // Close all
        Response response = new ResponseImpl(null, br);
        close(response);
        assertEquals("h200c", sb.toString());
        // Close first headers and then al
        sb.setLength(0);
        response = new ResponseImpl(null, br);
        response.status(300);
        response.headers().send().toCompletableFuture().get();
        assertEquals("h300", sb.toString());
        close(response);
        assertEquals("h300c", sb.toString());
    }

    @Test
    public void headersAreCaseInsensitive() throws Exception {
        StringBuffer sb = new StringBuffer();
        NoOpBareResponse br = new NoOpBareResponse(sb);
        Response response = new ResponseImpl(null, br);

        ResponseHeaders headers = response.headers();
        headers.addCookie("cookie1", "cookie-value-1");
        headers.addCookie("cookie2", "cookie-value-2");

        headers.add("Header", "hv1");
        headers.add("header", "hv2");
        headers.add("heaDer", "hv3");

        assertHeaders(headers, "Set-Cookie","cookie1=cookie-value-1", "cookie2=cookie-value-2");
        assertHeadersToMap(headers, "Set-Cookie","cookie1=cookie-value-1", "cookie2=cookie-value-2");
        assertHeaders(headers, "set-cookie","cookie1=cookie-value-1", "cookie2=cookie-value-2");
        assertHeadersToMap(headers, "set-cookie","cookie1=cookie-value-1", "cookie2=cookie-value-2");
        assertHeaders(headers, "SET-CooKIE","cookie1=cookie-value-1", "cookie2=cookie-value-2");
        assertHeadersToMap(headers, "SET-CooKIE","cookie1=cookie-value-1", "cookie2=cookie-value-2");

        assertHeaders(headers, "header","hv1", "hv2", "hv3");
        assertHeadersToMap(headers, "header","hv1", "hv2", "hv3");
        assertHeaders(headers, "Header","hv1", "hv2", "hv3");
        assertHeadersToMap(headers, "Header","hv1", "hv2", "hv3");
        assertHeaders(headers, "HEADer","hv1", "hv2", "hv3");
        assertHeadersToMap(headers, "HEADer","hv1", "hv2", "hv3");

    }

    private static void assertHeaders(ResponseHeaders headers, String headerName, String... expectedValues) {
        final List<String> actualValues = headers.all(headerName);
        assertThat("Value count doesn't match for header: " + headerName, actualValues, hasSize(expectedValues.length));
        assertThat("Content does not match for header: " + headerName, actualValues, containsInAnyOrder(expectedValues));
    }

    private static void assertHeadersToMap(ResponseHeaders headers, String headerName, String... expectedValues) {
        final List<String> actualValues = headers.toMap().get(headerName);
        assertThat("Value count doesn't match for header: " + headerName, actualValues, hasSize(expectedValues.length));
        assertThat("Content does not match for header: " + headerName, actualValues, containsInAnyOrder(expectedValues));
    }

    @Test
    public void classRelatedWriters() throws Exception {
        StringBuilder sb = new StringBuilder();
        Response response = new ResponseImpl(null, new NoOpBareResponse(null));
        assertNotNull(response.createPublisherUsingWriter("foo")); // Default
        assertNotNull(response.createPublisherUsingWriter("foo".getBytes())); // Default
        assertNull(response.createPublisherUsingWriter(Duration.of(1, ChronoUnit.MINUTES)));
        response.registerWriter(CharSequence.class, o -> {
            sb.append("1");
            return ReactiveStreamsAdapter.publisherToFlow(Mono.empty());
        });
        assertNotNull(response.createPublisherUsingWriter("foo"));
        assertEquals("1", sb.toString());

        sb.setLength(0);
        assertNotNull(response.createPublisherUsingWriter(null));
        assertEquals("", sb.toString());

        sb.setLength(0);
        response.registerWriter(String.class, o -> {
            sb.append("2");
            return ReactiveStreamsAdapter.publisherToFlow(Mono.empty());
        });
        assertNotNull(response.createPublisherUsingWriter("foo"));
        assertEquals("2", sb.toString());

        sb.setLength(0);
        assertNotNull(response.createPublisherUsingWriter(new StringBuilder()));
        assertEquals("1", sb.toString());

        sb.setLength(0);
        response.registerWriter((Class<Object>) null, o -> {
            sb.append("3");
            return ReactiveStreamsAdapter.publisherToFlow(Mono.empty());
        });
        assertNotNull(response.createPublisherUsingWriter(1));
        assertEquals("3", sb.toString());
    }

    @Test
    public void writerByPredicate() throws Exception {
        StringBuilder sb = new StringBuilder();
        Response response = new ResponseImpl(null, new NoOpBareResponse(null));
        response.registerWriter(o -> "1".equals(String.valueOf(o)),
                                o -> {
                                    sb.append("1");
                                    return ReactiveStreamsAdapter.publisherToFlow(Mono.empty());
                                });
        response.registerWriter(o -> "2".equals(String.valueOf(o)),
                                o -> {
                                    sb.append("2");
                                    return ReactiveStreamsAdapter.publisherToFlow(Mono.empty());
                                });
        assertNotNull(response.createPublisherUsingWriter(1));
        assertEquals("1", sb.toString());

        sb.setLength(0);
        assertNotNull(response.createPublisherUsingWriter(2));
        assertEquals("2", sb.toString());

        sb.setLength(0);
        assertNull(response.createPublisherUsingWriter(3));
        assertEquals("", sb.toString());
    }

    @Test
    public void writerWithMediaType() throws Exception {
        StringBuilder sb = new StringBuilder();
        Response response = new ResponseImpl(null, new NoOpBareResponse(null));
        response.registerWriter(CharSequence.class,
                                MediaType.TEXT_PLAIN,
                                o -> {
                                    sb.append("A");
                                    return ReactiveStreamsAdapter.publisherToFlow(Mono.empty());
                                });
        response.registerWriter(o -> o instanceof Number,
                                MediaType.APPLICATION_JSON,
                                o -> {
                                    sb.append("B");
                                    return ReactiveStreamsAdapter.publisherToFlow(Mono.empty());
                                });
        assertNotNull(response.createPublisherUsingWriter("foo"));
        assertEquals("A", sb.toString());
        assertEquals(MediaType.TEXT_PLAIN, response.headers().contentType().orElse(null));

        sb.setLength(0);
        response.headers().remove(Http.Header.CONTENT_TYPE);
        assertNotNull(response.createPublisherUsingWriter(1));
        assertEquals("B", sb.toString());
        assertEquals(MediaType.APPLICATION_JSON, response.headers().contentType().orElse(null));

        sb.setLength(0);
        response.headers().put(Http.Header.CONTENT_TYPE, MediaType.APPLICATION_JSON.toString());
        assertNotNull(response.createPublisherUsingWriter(1));
        assertEquals("B", sb.toString());
        assertEquals(MediaType.APPLICATION_JSON, response.headers().contentType().orElse(null));

        sb.setLength(0);
        response.headers().put(Http.Header.CONTENT_TYPE, MediaType.TEXT_HTML.toString());
        assertNull(response.createPublisherUsingWriter(1));
        assertEquals("", sb.toString());
        assertEquals(MediaType.TEXT_HTML, response.headers().contentType().orElse(null));
    }

    @Test
    public void filters() throws Exception {
        StringBuilder sb = new StringBuilder();
        Response response = new ResponseImpl(null, new NoOpBareResponse(null));
        response.registerFilter(p -> {
            sb.append("A");
            return p;
        });
        response.registerFilter(p -> {
            sb.append("B");
            return null;
        });
        response.registerFilter(p -> {
            sb.append("C");
            return p;
        });
        assertNotNull(response.applyFilters(ReactiveStreamsAdapter.publisherToFlow(Mono.empty()), null));
        assertEquals("ABC", sb.toString());
    }

    static class ResponseImpl extends Response {

        public ResponseImpl(WebServer webServer, BareResponse bareResponse) {
            super(webServer, bareResponse);
        }

        @Override
        SpanContext spanContext() {
            return null;
        }
    }

    static class NoOpBareResponse implements BareResponse {

        private final StringBuffer sb;
        private final CompletableFuture<BareResponse> closeFuture = new CompletableFuture<>();

        NoOpBareResponse(StringBuffer sb) {
            this.sb = sb == null ? new StringBuffer() : sb;
        }

        @Override
        public void writeStatusAndHeaders(Http.ResponseStatus status, Map<String, List<String>> headers) {
            sb.append("h").append(status.code());
        }

        @Override
        public CompletionStage<BareResponse> whenCompleted() {
            return closeFuture;
        }

        @Override
        public CompletionStage<BareResponse> whenHeadersCompleted() {
            return closeFuture;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(DataChunk data) {
            sb.append("d");
        }

        @Override
        public void onError(Throwable thr) {
            sb.append("e");
        }

        @Override
        public void onComplete() {
            sb.append("c");
            closeFuture.complete(this);
        }

        @Override
        public long requestId() {
            return 0;
        }
    }
}
