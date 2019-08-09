/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.common.reactive.Flow;

import io.opentracing.SpanContext;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import io.helidon.common.reactive.Single;

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
        assertThat(sb.toString(), is("h200c"));
        // Close first headers and then al
        sb.setLength(0);
        response = new ResponseImpl(null, br);
        response.status(300);
        response.headers().send().toCompletableFuture().get();
        assertThat(sb.toString(), is("h300"));
        close(response);
        assertThat(sb.toString(), is("h300c"));
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
        assertThat(response.createPublisherUsingWriter("foo"), notNullValue()); // Default
        assertThat(response.createPublisherUsingWriter("foo".getBytes()), notNullValue()); // Default
        assertThat(response.createPublisherUsingWriter(Duration.of(1, ChronoUnit.MINUTES)), nullValue());
        response.registerWriter(CharSequence.class, o -> {
            sb.append("1");
            return Single.empty();
        });
        assertThat(response.createPublisherUsingWriter("foo"), notNullValue());
        assertThat(sb.toString(), is("1"));

        sb.setLength(0);
        assertThat(response.createPublisherUsingWriter(null), notNullValue());
        assertThat(sb.toString(), is(""));

        sb.setLength(0);
        response.registerWriter(String.class, o -> {
            sb.append("2");
            return Single.empty();
        });
        assertThat(response.createPublisherUsingWriter("foo"), notNullValue());
        assertThat(sb.toString(), is("2"));

        sb.setLength(0);
        assertThat(response.createPublisherUsingWriter(new StringBuilder()), notNullValue());
        assertThat(sb.toString(), is("1"));

        sb.setLength(0);
        response.registerWriter((Class<Object>) null, o -> {
            sb.append("3");
            return Single.empty();
        });
        assertThat(response.createPublisherUsingWriter(1), notNullValue());
        assertThat(sb.toString(), is("3"));
    }

    @Test
    public void writerByPredicate() throws Exception {
        StringBuilder sb = new StringBuilder();
        Response response = new ResponseImpl(null, new NoOpBareResponse(null));
        response.registerWriter(o -> "1".equals(String.valueOf(o)),
                                o -> {
                                    sb.append("1");
                                    return Single.empty();
                                });
        response.registerWriter(o -> "2".equals(String.valueOf(o)),
                                o -> {
                                    sb.append("2");
                                    return Single.empty();
                                });
        assertThat(response.createPublisherUsingWriter(1), notNullValue());
        assertThat(sb.toString(), is("1"));

        sb.setLength(0);
        assertThat(response.createPublisherUsingWriter(2), notNullValue());
        assertThat(sb.toString(), is("2"));

        sb.setLength(0);
        assertThat(response.createPublisherUsingWriter(3), nullValue());
        assertThat(sb.toString(), is(""));
    }

    @Test
    public void writerWithMediaType() throws Exception {
        StringBuilder sb = new StringBuilder();
        Response response = new ResponseImpl(null, new NoOpBareResponse(null));
        response.registerWriter(CharSequence.class,
                                MediaType.TEXT_PLAIN,
                                o -> {
                                    sb.append("A");
                                    return Single.empty();
                                });
        response.registerWriter(o -> o instanceof Number,
                                MediaType.APPLICATION_JSON,
                                o -> {
                                    sb.append("B");
                                    return Single.empty();
                                });
        assertThat(response.createPublisherUsingWriter("foo"), notNullValue());
        assertThat(sb.toString(), is("A"));
        assertThat(response.headers().contentType().orElse(null), is(MediaType.TEXT_PLAIN));

        sb.setLength(0);
        response.headers().remove(Http.Header.CONTENT_TYPE);
        assertThat(response.createPublisherUsingWriter(1), notNullValue());
        assertThat(sb.toString(), is("B"));
        assertThat(response.headers().contentType().orElse(null), is(MediaType.APPLICATION_JSON));

        sb.setLength(0);
        response.headers().put(Http.Header.CONTENT_TYPE, MediaType.APPLICATION_JSON.toString());
        assertThat(response.createPublisherUsingWriter(1), notNullValue());
        assertThat(sb.toString(), is("B"));
        assertThat(response.headers().contentType().orElse(null), is(MediaType.APPLICATION_JSON));

        sb.setLength(0);
        response.headers().put(Http.Header.CONTENT_TYPE, MediaType.TEXT_HTML.toString());
        assertThat(response.createPublisherUsingWriter(1), nullValue());
        assertThat(sb.toString(), is(""));
        assertThat(response.headers().contentType().orElse(null), is(MediaType.TEXT_HTML));
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
        assertThat(response.applyFilters(Single.empty(), null), notNullValue());
        assertThat(sb.toString(), is("ABC"));
    }

    static class ResponseImpl extends Response {

        public ResponseImpl(WebServer webServer, BareResponse bareResponse) {
            super(webServer, bareResponse);
        }

        @Override
        Optional<SpanContext> spanContext() {
            return Optional.empty();
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
