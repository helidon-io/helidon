/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webclient.api;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.buffers.BufferData;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.webclient.spi.WebClientService;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientRequestBaseTest {

    @Test
    void rejectsMultipleHostHeaderBeforeRequest() {
        TestRequest request = new TestRequest(Method.GET, "http://service.example");
        request.headers().add(HeaderValues.create(HeaderNames.HOST, "first.example", "second.example"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, request::request);

        assertThat(exception.getMessage(), containsString("Request Host header must be single-valued"));
        assertThat(request.endpointCount(), is(0));
    }

    @Test
    void rejectsMultipleHostHeaderBeforeSubmit() {
        TestRequest request = new TestRequest(Method.POST, "http://service.example");
        request.headers().add(HeaderValues.create(HeaderNames.HOST, "first.example", "second.example"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                         () -> request.submit(BufferData.EMPTY_BYTES));

        assertThat(exception.getMessage(), containsString("Request Host header must be single-valued"));
        assertThat(request.endpointCount(), is(0));
    }

    @Test
    void rejectsMultipleHostHeaderBeforeOutputStream() {
        TestRequest request = new TestRequest(Method.POST, "http://service.example");
        request.headers().add(HeaderValues.create(HeaderNames.HOST, "first.example", "second.example"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                         () -> request.outputStream(out -> { }));

        assertThat(exception.getMessage(), containsString("Request Host header must be single-valued"));
        assertThat(request.endpointCount(), is(0));
    }

    @Test
    void rejectsHostHeaderMadeMultipleByService() {
        HttpClientConfig config = HttpClientConfig.builder()
                .addService((chain, request) -> {
                    request.headers().add(HeaderValues.create(HeaderNames.HOST, "first.example", "second.example"));
                    return chain.proceed(request);
                })
                .build();
        TestRequest request = new TestRequest(config, Method.GET, "http://service.example");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, request::request);

        assertThat(exception.getMessage(), containsString("Request Host header must be single-valued"));
        assertThat(request.endpointCount(), is(0));
    }

    private static final class TestRequest extends ClientRequestBase<TestRequest, HttpClientResponse> {
        private final AtomicInteger endpointCount = new AtomicInteger();

        private TestRequest(Method method, String uri) {
            this(HttpClientConfig.builder().build(), method, uri);
        }

        private TestRequest(HttpClientConfig clientConfig, Method method, String uri) {
            super(clientConfig,
                  WebClientCookieManager.builder().build(),
                  "test",
                  method,
                  ClientUri.create(URI.create(uri)),
                  Map.of());
        }

        @Override
        protected HttpClientResponse doSubmit(Object entity) {
            invokeEndpoint();
            return null;
        }

        @Override
        protected HttpClientResponse doOutputStream(OutputStreamHandler outputStreamHandler) {
            invokeEndpoint();
            return null;
        }

        private void invokeEndpoint() {
            CompletableFuture<WebClientServiceRequest> whenSent = new CompletableFuture<>();
            CompletableFuture<WebClientServiceResponse> whenComplete = new CompletableFuture<>();
            invokeServices(endpoint(), whenSent, whenComplete, resolvedUri());
        }

        private WebClientService.Chain endpoint() {
            return serviceRequest -> {
                endpointCount.incrementAndGet();
                return WebClientServiceResponse.builder()
                        .serviceRequest(serviceRequest)
                        .whenComplete(new CompletableFuture<>())
                        .connection(() -> { })
                        .status(Status.OK_200)
                        .headers(ClientResponseHeaders.create(WritableHeaders.create()))
                        .build();
            };
        }

        private int endpointCount() {
            return endpointCount.get();
        }
    }
}
