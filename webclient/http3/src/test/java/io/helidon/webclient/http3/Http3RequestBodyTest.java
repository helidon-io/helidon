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

package io.helidon.webclient.http3;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.context.Context;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.HeaderNames;
import io.helidon.http.WritableHeaders;
import io.helidon.webclient.api.ClientRequest;
import io.helidon.webclient.api.HttpClientRequest;
import io.helidon.webclient.api.HttpClientResponse;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Http3RequestBodyTest {
    @Test
    void shouldReplayMaterializedBody() {
        byte[] bytes = "request body".getBytes(StandardCharsets.UTF_8);
        Http3RequestBody body = Http3RequestBody.create(bytes);
        ByteArrayOutputStream firstAttempt = new ByteArrayOutputStream();
        ByteArrayOutputStream secondAttempt = new ByteArrayOutputStream();

        body.writeTo(firstAttempt);
        body.writeTo(secondAttempt);

        assertThat(body.canStartAttempt(), is(true));
        assertThat(firstAttempt.toByteArray(), equalTo(bytes));
        assertThat(secondAttempt.toByteArray(), equalTo(bytes));
    }

    @Test
    void shouldClaimStreamingBodyOnFirstInvocation() throws IOException {
        AtomicInteger invocations = new AtomicInteger();
        byte[] bytes = "streamed body".getBytes(StandardCharsets.UTF_8);
        Http3RequestBody body = Http3RequestBody.create(outputStream -> {
            invocations.incrementAndGet();
            outputStream.write(bytes);
        }, -1);
        HttpClientRequest request = mock(HttpClientRequest.class);
        HttpClientResponse response = mock(HttpClientResponse.class);
        when(request.outputStream(any())).thenReturn(response);

        assertThat(body.submit(request), sameInstance(response));
        assertThat(body.canStartAttempt(), is(true));

        ArgumentCaptor<ClientRequest.OutputStreamHandler> handlerCaptor =
                ArgumentCaptor.forClass(ClientRequest.OutputStreamHandler.class);
        verify(request).outputStream(handlerCaptor.capture());
        ByteArrayOutputStream firstAttempt = new ByteArrayOutputStream();
        handlerCaptor.getValue().handle(firstAttempt);

        assertThat(body.canStartAttempt(), is(false));
        assertThat(invocations.get(), is(1));
        assertThat(firstAttempt.toByteArray(), equalTo(bytes));

        assertThrows(IllegalStateException.class,
                     () -> handlerCaptor.getValue().handle(new ByteArrayOutputStream()));
        assertThat(invocations.get(), is(1));
    }

    @Test
    void shouldWrapStreamingProducerIoFailure() {
        IOException cause = new IOException("test producer failure");
        Http3RequestBody body = Http3RequestBody.create(_ -> {
            throw cause;
        }, -1);

        UncheckedIOException failure = assertThrows(
                UncheckedIOException.class,
                () -> body.writeTo(new ByteArrayOutputStream()));

        assertThat(failure.getCause(), sameInstance(cause));
    }

    @Test
    void shouldExposeDeclaredContentLength() {
        Http3RequestBody materialized = Http3RequestBody.create(new byte[13]);
        Http3RequestBody declaredStreaming = Http3RequestBody.create(outputStream -> { }, 21);
        Http3RequestBody unknownStreaming = Http3RequestBody.create(outputStream -> { }, -1);

        assertThat(materialized.contentLength(), is(OptionalLong.of(13)));
        assertThat(declaredStreaming.contentLength(), is(OptionalLong.of(21)));
        assertThat(unknownStreaming.contentLength(), is(OptionalLong.empty()));
    }

    @Test
    void shouldPrepareMaterializedContentLengthFromActualBytes() {
        for (long declaredLength : new long[] {-1, 3, 6}) {
            ClientRequestHeaders headers = ClientRequestHeaders.create(WritableHeaders.create());
            if (declaredLength >= 0) {
                headers.contentLength(declaredLength);
            }
            Http3RequestBody body = Http3RequestBody.create(new byte[5]);

            body.prepare(headers, Context.create(), 16);

            assertThat("Buffered length must replace service length " + declaredLength,
                       headers.contentLength(),
                       is(OptionalLong.of(5)));
        }
    }

    @Test
    void shouldCorrectEmptyBodyLengthWithoutAddingUnneededHeader() {
        Http3RequestBody body = Http3RequestBody.create(new byte[0]);
        ClientRequestHeaders declared = ClientRequestHeaders.create(WritableHeaders.create());
        declared.contentLength(6);
        ClientRequestHeaders absent = ClientRequestHeaders.create(WritableHeaders.create());

        body.prepare(declared, Context.create(), 16);
        body.prepare(absent, Context.create(), 16);

        assertThat(declared.contentLength(), is(OptionalLong.of(0)));
        assertThat(absent.contains(HeaderNames.CONTENT_LENGTH), is(false));
    }

    @Test
    void shouldPreserveServiceFinalStreamingLength() {
        Http3RequestBody body = Http3RequestBody.create(_ -> { }, 21);
        ClientRequestHeaders headers = ClientRequestHeaders.create(WritableHeaders.create());
        headers.contentLength(6);

        body.prepare(headers, Context.create(), 16);

        assertThat(headers.contentLength(), is(OptionalLong.of(6)));
    }

    @Test
    void shouldSubmitMaterializedBodyDirectly() {
        byte[] bytes = "request body".getBytes(StandardCharsets.UTF_8);
        Http3RequestBody body = Http3RequestBody.create(bytes);
        HttpClientRequest request = mock(HttpClientRequest.class);
        HttpClientResponse response = mock(HttpClientResponse.class);
        when(request.submit(any())).thenReturn(response);

        assertThat(body.submit(request), sameInstance(response));

        ArgumentCaptor<Object> entityCaptor = ArgumentCaptor.forClass(Object.class);
        verify(request).submit(entityCaptor.capture());
        assertThat((byte[]) entityCaptor.getValue(), equalTo(bytes));
        verify(request, never()).outputStream(any());
    }
}
