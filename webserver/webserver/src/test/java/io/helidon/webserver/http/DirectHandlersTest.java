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

package io.helidon.webserver.http;

import java.util.concurrent.atomic.AtomicReference;

import io.helidon.http.DirectHandler;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Method;
import io.helidon.http.RequestException;
import io.helidon.http.ServerResponseHeaders;
import io.helidon.http.Status;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DirectHandlersTest {

    @Test
    void mergesVaryHeaders() {
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        responseHeaders.add(HeaderNames.VARY, HeaderNames.ORIGIN_NAME);
        DirectHandler.TransportResponse directResponse = DirectHandler.TransportResponse.builder()
                .header(HeaderValues.create(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME))
                .build();

        handle(directResponse, responseHeaders);

        assertThat(responseHeaders.get(HeaderNames.VARY).allValues(),
                   containsInAnyOrder(HeaderNames.ORIGIN_NAME, HeaderNames.ACCEPT_ENCODING_NAME));
    }

    @Test
    void replacesContentLengthForEntitySent() {
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        responseHeaders.contentLength(17);
        DirectHandler.TransportResponse directResponse = DirectHandler.TransportResponse.builder()
                .header(HeaderValues.create(HeaderNames.CONTENT_LENGTH, "23"))
                .entity("error")
                .build();

        handle(directResponse, responseHeaders);

        assertThat(responseHeaders.contentLength().orElseThrow(), is(5L));
    }

    @Test
    void replacesContentLengthForEmptyResponse() {
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        responseHeaders.contentLength(17);
        DirectHandler.TransportResponse directResponse = DirectHandler.TransportResponse.builder()
                .header(HeaderValues.create(HeaderNames.CONTENT_LENGTH, "23"))
                .build();

        handle(directResponse, responseHeaders);

        assertThat(responseHeaders.contentLength().orElseThrow(), is(0L));
    }

    @Test
    void removesContentLengthBeforeNoContentStatus() {
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        responseHeaders.contentLength(17);
        DirectHandler.TransportResponse directResponse = DirectHandler.TransportResponse.builder()
                .status(Status.NO_CONTENT_204)
                .build();

        handle(directResponse, responseHeaders);

        assertThat(responseHeaders.contains(HeaderNames.CONTENT_LENGTH), is(false));
    }

    @Test
    void preservesContentLengthForNotModifiedStatus() {
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        responseHeaders.contentLength(17);
        DirectHandler.TransportResponse directResponse = DirectHandler.TransportResponse.builder()
                .status(Status.NOT_MODIFIED_304)
                .entity("ignored")
                .header(HeaderNames.CONTENT_LENGTH, "23")
                .build();

        handle(directResponse, responseHeaders);

        assertThat(responseHeaders.contentLength().orElseThrow(), is(23L));
    }

    @Test
    void usesAvailableRequestForRequestlessHeadException() {
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        ServerResponse response = mock(ServerResponse.class);
        when(response.status(any())).thenReturn(response);
        when(response.headers()).thenReturn(responseHeaders);
        when(response.header(any(Header.class))).thenAnswer(invocation -> {
            responseHeaders.set(invocation.getArgument(0));
            return response;
        });

        AtomicReference<DirectHandler.TransportRequest> handledRequest = new AtomicReference<>();
        DirectHandlers directHandlers = DirectHandlers.builder()
                .addHandler(DirectHandler.EventType.BAD_REQUEST, (request, _, _, _, _) -> {
                    handledRequest.set(request);
                    return DirectHandler.TransportResponse.builder()
                            .status(Status.BAD_REQUEST_400)
                            .header(HeaderNames.CONTENT_LENGTH, "23")
                            .build();
                })
                .build();
        RequestException requestException = RequestException.builder()
                .type(DirectHandler.EventType.BAD_REQUEST)
                .message("bad request")
                .build();
        DirectHandler.TransportRequest request = mock(DirectHandler.TransportRequest.class);
        when(request.method()).thenReturn(Method.HEAD_NAME);
        when(request.protocolVersion()).thenReturn("HTTP/1.1");

        directHandlers.handle(requestException, request, response, true);

        assertSame(request, handledRequest.get());
        assertThat(responseHeaders.contentLength().orElseThrow(), is(23L));
        verify(response).send();
        verify(response, never()).send(any(byte[].class));
    }

    private static void handle(DirectHandler.TransportResponse directResponse,
                               ServerResponseHeaders responseHeaders) {
        ServerResponse response = mock(ServerResponse.class);
        AtomicReference<Status> status = new AtomicReference<>(Status.OK_200);
        when(response.status(any())).thenAnswer(invocation -> {
            assertThat(responseHeaders.contains(HeaderNames.CONTENT_LENGTH), is(false));
            status.set(invocation.getArgument(0));
            return response;
        });
        when(response.headers()).thenReturn(responseHeaders);
        when(response.header(any(Header.class))).thenAnswer(invocation -> {
            responseHeaders.set(invocation.getArgument(0));
            return response;
        });
        doAnswer(invocation -> {
            assertThat(responseHeaders.contains(HeaderNames.CONTENT_LENGTH), is(false));
            responseHeaders.contentLength(invocation.<byte[]>getArgument(0).length);
            return null;
        }).when(response).send(any(byte[].class));
        doAnswer(_ -> {
            if (status.get().code() == Status.NO_CONTENT_204.code()) {
                assertThat(responseHeaders.contains(HeaderNames.CONTENT_LENGTH), is(false));
            } else if (status.get().code() != Status.NOT_MODIFIED_304.code()) {
                responseHeaders.contentLength(0);
            }
            return null;
        }).when(response).send();

        DirectHandlers directHandlers = DirectHandlers.builder()
                .addHandler(DirectHandler.EventType.BAD_REQUEST,
                            (_, _, _, _, _) -> directResponse)
                .build();
        RequestException requestException = RequestException.builder()
                .type(DirectHandler.EventType.BAD_REQUEST)
                .message("bad request")
                .build();

        directHandlers.handle(requestException, response, true);
    }
}
