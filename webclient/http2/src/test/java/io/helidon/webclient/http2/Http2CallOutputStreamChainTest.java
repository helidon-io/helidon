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

package io.helidon.webclient.http2;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.webclient.api.ClientRequest;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.WebClientServiceRequest;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Http2CallOutputStreamChainTest {
    private static final String BLOCKED_REDIRECT_MESSAGE = "Cross-origin redirect with request entity is disabled.";

    @Test
    void doesNotProbeOneShotHandlerAfterHttp1FallbackAlreadySentEntity() throws Exception {
        Http2ClientImpl client = (Http2ClientImpl) Http2Client.create(builder -> builder
                .baseUri("http://127.0.0.1:8080")
                .followRedirects(true)
                .followCrossOriginEntityRedirects(false));
        Http2ClientRequestImpl request = (Http2ClientRequestImpl) client.method(Method.PUT)
                .path("/token");

        AtomicInteger invocations = new AtomicInteger();
        ClientRequest.OutputStreamHandler oneShotHandler = stream -> {
            if (invocations.getAndIncrement() == 0) {
                stream.write("sensitive-body".getBytes(StandardCharsets.UTF_8));
            }
            stream.close();
        };
        Http2CallOutputStreamChain.EntityTrackingOutputStreamHandler fallbackHandler =
                new Http2CallOutputStreamChain.EntityTrackingOutputStreamHandler(oneShotHandler);
        fallbackHandler.handle(OutputStream.nullOutputStream());

        Http2CallOutputStreamChain chain = new Http2CallOutputStreamChain(client,
                                                                          request,
                                                                          new CompletableFuture<>(),
                                                                          new CompletableFuture<>(),
                                                                          oneShotHandler,
                                                                          fallbackHandler);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                                                       () -> chain.doProceed(mock(WebClientServiceRequest.class),
                                                                             redirectResponse(Status.TEMPORARY_REDIRECT_307)));

        assertThat(exception.getMessage(), is(BLOCKED_REDIRECT_MESSAGE));
        assertThat(invocations.get(), is(1));
    }

    private static HttpClientResponse redirectResponse(Status status) {
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.set(HeaderValues.create(HeaderNames.LOCATION, "http://localhost:8081/capture-body"));

        HttpClientResponse response = mock(HttpClientResponse.class);
        when(response.status()).thenReturn(status);
        when(response.headers()).thenReturn(ClientResponseHeaders.create(headers));
        return response;
    }
}
